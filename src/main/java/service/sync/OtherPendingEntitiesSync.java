package service.sync;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import utils.DBConnection;

public final class OtherPendingEntitiesSync {

	private static final Set<SupabaseEndpoints> REMOTE_UNAVAILABLE = ConcurrentHashMap.newKeySet();

	public static record TableDef(String sqliteTable, SupabaseEndpoints endpoint, String uuidColumn,
			boolean skipTempCodeColumn) {
	}

	private static final Set<String> INVOICE_JOB_MAPPING_REMOTE_COLS = Set.of(
			"uuid", "invoice_uuid", "job_uuid", "sync_status", "sync_version",
			"is_deleted", "is_active", "created_at", "updated_at", "synced_at");

	private static final Set<String> DOCUMENT_NUMBER_MAPPINGS_REMOTE_COLS = Set.of(
			"uuid", "entity_type", "entity_uuid", "sequence_key",
			"temporary_number", "permanent_number", "allocation_source", "created_at");

	private static final List<TableDef> ORDER = List.of(
			new TableDef("job_items", SupabaseEndpoints.JOB_ITEMS, "uuid", false),
			new TableDef("printing_items", SupabaseEndpoints.PRINTING_ITEMS, "uuid", false),
			new TableDef("paper_items", SupabaseEndpoints.PAPER_ITEMS, "uuid", false),
			new TableDef("binding_items", SupabaseEndpoints.BINDING_ITEMS, "uuid", false),
			new TableDef("lamination_items", SupabaseEndpoints.LAMINATION_ITEMS, "uuid", false),
			new TableDef("ctp_items", SupabaseEndpoints.CTP_ITEMS, "uuid", false),
			new TableDef("invoice_job_mapping", SupabaseEndpoints.INVOICE_JOB_MAPPING, "uuid", false),
			new TableDef("invoice_additional_charges", SupabaseEndpoints.INVOICE_ADDITIONAL_CHARGES, "uuid", false),
			new TableDef("invoice_adjustments", SupabaseEndpoints.INVOICE_ADJUSTMENTS, "uuid", true),
			new TableDef("payment_allocations", SupabaseEndpoints.PAYMENT_ALLOCATIONS, "uuid", false),
			new TableDef("payment_details", SupabaseEndpoints.PAYMENT_DETAILS, "uuid", false),
			new TableDef("suppliers", SupabaseEndpoints.SUPPLIERS, "uuid", false),
			new TableDef("users", SupabaseEndpoints.USERS, "uuid", false),
			new TableDef("document_number_mappings", SupabaseEndpoints.DOCUMENT_NUMBER_MAPPINGS, "uuid", false),
			new TableDef("company_details", SupabaseEndpoints.COMPANY_DETAILS, "uuid", false),
			new TableDef("bank_details", SupabaseEndpoints.BANK_DETAILS, "uuid", false),
			new TableDef("hsn_sac_master", SupabaseEndpoints.HSN_SAC_MASTER, "uuid", false));

	private OtherPendingEntitiesSync() {
	}

	public static int syncAll(SupabaseRestClient http, SyncReport report) {
		int total = 0;
		for (TableDef def : ORDER) {
			total += syncTable(http, def, report);
		}
		return total;
	}

	public static int syncTable(SupabaseRestClient http, TableDef def, SyncReport report) {
		if (REMOTE_UNAVAILABLE.contains(def.endpoint())) {
			return 0;
		}
		int synced = 0;
		try (Connection conn = DBConnection.getConnection()) {
			if (!tableExists(conn, def.sqliteTable())) {
				return 0;
			}
			String extra = def.skipTempCodeColumn() ? " AND note_no NOT LIKE 'TEMP-%'" : "";
			String sql = buildPendingSelectSql(conn, def.sqliteTable(), extra);
			if (sql == null) {
				return 0;
			}
			try (PreparedStatement ps = conn.prepareStatement(sql);
					ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					String uuid = rs.getString(def.uuidColumn());
					if (uuid == null || uuid.isBlank()) {
						continue;
					}
					if (!UniversalSyncEngine.isValidUuid(uuid)) {
						UniversalSyncEngine.markTableSyncError(def.sqliteTable(), def.uuidColumn(), uuid, "Invalid UUID format");
						continue;
					}
					// Skip child items if parent job/job_item is not synced locally
					if ("job_items".equals(def.sqliteTable())) {
						String jobUuid = rs.getString("job_uuid");
						if (!isParentSynced(conn, "jobs", jobUuid)) {
							UniversalSyncEngine.markTableWaitingDependency(def.sqliteTable(), def.uuidColumn(), uuid);
							continue;
						}
					} else if (isJobItemDetailTable(def.sqliteTable())) {
						String jobItemUuid = rs.getString("job_item_uuid");
						if (!isParentSynced(conn, "job_items", jobItemUuid)) {
							UniversalSyncEngine.markTableWaitingDependency(def.sqliteTable(), def.uuidColumn(), uuid);
							continue;
						}
					}

					try {
						String originalSyncStatus = null;
						try {
							originalSyncStatus = rs.getString("sync_status");
						} catch (Exception e) { service.LoggerService.dbWarn("[SYNC] Could not read sync_status for " + def.sqliteTable() + " " + uuid + ": " + e.getMessage()); }
						boolean wasWaiting = "WAITING_DEPENDENCY".equalsIgnoreCase(originalSyncStatus);

						JsonObject row = rowToJson(rs);
						if ("payment_allocations".equals(def.sqliteTable())) {
							String paymentUuid = rs.getString("payment_uuid");
							double allocatedAmount = rs.getDouble("allocated_amount");
							int localDeleted = rs.getInt("is_deleted");
							if (paymentUuid != null && localDeleted == 0) {
								boolean rejected = SyncConflictResolver.validateAndResolveDoubleSpend(conn, uuid, paymentUuid, allocatedAmount, false, null, row.toString());
								if (rejected) {
									row.addProperty("is_deleted", true);
									row.addProperty("sync_status", "PENDING");
								}
							}
						}
						if ("invoice_job_mapping".equals(def.sqliteTable())) {
							row = filterJsonColumns(row, INVOICE_JOB_MAPPING_REMOTE_COLS);
						}
						if ("document_number_mappings".equals(def.sqliteTable())) {
							row = filterJsonColumns(row, DOCUMENT_NUMBER_MAPPINGS_REMOTE_COLS);
						}
						if ("paper_items".equals(def.sqliteTable()) || "ctp_items".equals(def.sqliteTable())) {
							row.remove("supplier_name");
						}
						// Convert SQLite 0/1 integers to actual JSON booleans for is_deleted, is_active, and taxable_flag
						for (String boolCol : new String[]{"is_deleted", "is_active", "taxable_flag"}) {
							if ("invoice_master".equals(def.sqliteTable()) && "is_deleted".equals(boolCol)) {
								// Skip boolean conversion for invoice_master's is_deleted (stays as integer 0/1)
								continue;
							}
							if (row.has(boolCol) && !row.get(boolCol).isJsonNull()) {
								try {
									com.google.gson.JsonElement el = row.get(boolCol);
									if (el.isJsonPrimitive()) {
										com.google.gson.JsonPrimitive prim = el.getAsJsonPrimitive();
										if (prim.isNumber()) {
											row.addProperty(boolCol, prim.getAsInt() != 0);
										} else if (prim.isString()) {
											String val = prim.getAsString();
											row.addProperty(boolCol, "true".equalsIgnoreCase(val) || "1".equals(val) || "yes".equalsIgnoreCase(val));
										}
									}
								} catch (Exception e) { service.LoggerService.dbWarn("[SYNC] Could not convert boolean for " + def.sqliteTable() + " " + uuid + ": " + e.getMessage()); }
							}
						}


						String localUpdatedAt = null;
						try {
							localUpdatedAt = rs.getString("updated_at");
						} catch (Exception e) { service.LoggerService.dbWarn("[SYNC] Could not read updated_at for " + def.sqliteTable() + " " + uuid + ": " + e.getMessage()); }

						List<String> colsList = SyncConflictResolver.getColumns(conn, def.sqliteTable());
						if (SyncConflictResolver.checkPushConflictAndResolve(conn, http, def.sqliteTable(), def.endpoint(), uuid, localUpdatedAt, colsList)) {
							synced++;
							continue;
						}

						row.addProperty("sync_status", "SYNCED");
						row.addProperty("synced_at", java.time.Instant.now().toString());
						upsertRow(http, def.endpoint(), def.uuidColumn(), row);
						markSynced(def.sqliteTable(), def.uuidColumn(), uuid);
						synced++;
						if (wasWaiting) {
							System.out.println("[UniversalSyncEngine] Dependency resolved: successfully synced " + def.sqliteTable() + " row " + uuid);
						}
					} catch (Exception e) {
						String errMsg = e.getMessage();
						if (errMsg != null && (errMsg.contains("PGRST204") || errMsg.contains("schema cache"))) {
							utils.AdminWarningUtil.showSchemaCacheWarning(def.sqliteTable(), errMsg);
							report.failures++;
							System.err.println("[OtherPendingEntitiesSync] SCHEMA CACHE BLOCK on " + def.sqliteTable() + ": " + errMsg);
							break; // Break the inner loop for this table since the entire table is blocked by the cache
						}
						
						if (markRemoteUnavailableIfMissing(def.endpoint(), errMsg)) {
							break;
						}
						if (UniversalSyncEngine.isForeignKeyFailure(e)) {
							UniversalSyncEngine.markTableWaitingDependency(def.sqliteTable(), def.uuidColumn(), uuid);
						} else {
							report.failures++;
						}
						System.err.println("[OtherPendingEntitiesSync] " + def.sqliteTable() + " " + uuid + ": "
								+ errMsg);
					}
				}
			}
		} catch (Exception e) {
			report.failures++;
			System.err.println("[OtherPendingEntitiesSync] " + def.sqliteTable() + " query failed: "
					+ e.getMessage());
		}
		return synced;
	}

	private static String buildPendingSelectSql(Connection conn, String table, String extra) throws Exception {
		if (!columnExists(conn, table, "sync_status")) {
			return null;
		}
		StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table).append(" WHERE 1=1");
		sql.append(" AND ").append(PendingSyncFilters.PENDING_STATUS);
		if (columnExists(conn, table, "invoice_uuid")) {
			sql.append(" AND (invoice_uuid IS NULL OR invoice_uuid = '' OR invoice_uuid NOT IN (SELECT uuid FROM invoice_master WHERE invoice_no LIKE 'TEMP-%'))");
		}
		sql.append(extra);
		if (columnExists(conn, table, "created_at")) {
			sql.append(" ORDER BY created_at ASC");
		}
		return sql.toString();
	}

	private static boolean tableExists(Connection conn, String table) throws Exception {
		try (var ps = conn.prepareStatement(
				"SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
			ps.setString(1, table);
			try (var rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static boolean columnExists(Connection conn, String table, String column) throws Exception {
		try (var ps = conn.prepareStatement("PRAGMA table_info(" + table + ")")) {
			try (var rs = ps.executeQuery()) {
				while (rs.next()) {
					if (column.equalsIgnoreCase(rs.getString("name"))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static void upsertRow(SupabaseRestClient http, SupabaseEndpoints endpoint, String uuidColumn,
			JsonObject row) throws IOException, InterruptedException {
		JsonArray body = new JsonArray();
		body.add(row);
		String conflictCols = uuidColumn;
		if (SupabaseEndpoints.INVOICE_JOB_MAPPING.equals(endpoint)) {
			conflictCols = "invoice_uuid,job_uuid";
		}
		var res = http.postJsonWithQuery(endpoint, "on_conflict=" + conflictCols, body.toString(),
				"resolution=merge-duplicates,return=minimal");
		int code = res.statusCode();
		if (code < 200 || code >= 300) {
			throw new IOException("HTTP " + code + " " + res.body());
		}
	}

	private static JsonObject filterJsonColumns(JsonObject row, Set<String> allowed) {
		JsonObject filtered = new JsonObject();
		for (String key : allowed) {
			if (row.has(key) && !row.get(key).isJsonNull()) {
				filtered.add(key, row.get(key));
			}
		}
		return filtered;
	}

	private static JsonObject rowToJson(ResultSet rs) throws Exception {
		ResultSetMetaData meta = rs.getMetaData();
		JsonObject o = new JsonObject();
		for (int i = 1; i <= meta.getColumnCount(); i++) {
			String col = meta.getColumnLabel(i);
			if (col == null || col.isBlank()) {
				continue;
			}
			int type = meta.getColumnType(i);
			if (rs.getObject(i) == null) {
				o.add(col, JsonNull.INSTANCE);
				continue;
			}
			switch (type) {
			case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> o.addProperty(col, rs.getInt(i));
			case Types.BIGINT -> o.addProperty(col, rs.getLong(i));
			case Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL ->
				o.addProperty(col, rs.getDouble(i));
			case Types.BOOLEAN, Types.BIT -> o.addProperty(col, rs.getBoolean(i));
			default -> {
				String s = rs.getString(i);
				if (s != null && col.toLowerCase().contains("uuid")) {
					if (s.trim().isEmpty()) {
						o.add(col, JsonNull.INSTANCE);
					} else {
						o.addProperty(col, s.trim());
					}
				} else {
					o.addProperty(col, s);
				}
			}
			}
		}
		return o;
	}

	private static boolean markRemoteUnavailableIfMissing(SupabaseEndpoints endpoint, String message) {
		if (message == null) {
			return false;
		}
		String m = message;
		boolean missing = m.contains("404") || m.contains("PGRST205");
		if (!missing) {
			return false;
		}
		if (REMOTE_UNAVAILABLE.add(endpoint)) {
			System.out.println("[OtherPendingEntitiesSync] remote table missing, skipping: "
					+ endpoint.pathSegment());
		}
		return true;
	}

	private static void markSynced(String table, String uuidColumn, String uuid) {
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(
						"UPDATE " + table + " SET sync_status='SYNCED', synced_at=datetime('now') WHERE "
								+ uuidColumn + "=?")) {
			ps.setString(1, uuid.trim());
			ps.executeUpdate();
		} catch (Exception e) {
			service.LoggerService.dbError("[SYNC] Failed to mark " + table + " row as SYNCED: " + uuid, e);
		}
	}

	private static boolean isParentSynced(Connection conn, String parentTable, String parentUuid) {
		if (parentUuid == null || parentUuid.isBlank()) {
			return false;
		}
		String sql = "SELECT sync_status FROM " + parentTable + " WHERE uuid = ?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, parentUuid.trim());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					String status = rs.getString(1);
					return "SYNCED".equalsIgnoreCase(status);
				}
			}
		} catch (Exception e) {
			System.err.println("[OtherPendingEntitiesSync] Error checking parent sync status for " + parentTable + " " + parentUuid + ": " + e.getMessage());
		}
		return false;
	}

	private static boolean isJobItemDetailTable(String table) {
		return "printing_items".equals(table)
				|| "paper_items".equals(table)
				|| "binding_items".equals(table)
				|| "lamination_items".equals(table)
				|| "ctp_items".equals(table);
	}
}