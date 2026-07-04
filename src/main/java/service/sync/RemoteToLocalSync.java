package service.sync;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import utils.DBConnection;

public final class RemoteToLocalSync {

	private RemoteToLocalSync() {
	}

	/**
	 * Pulls updates from Supabase and applies them to local SQLite.
	 * Returns the total number of inserted, updated, and deleted rows.
	 */
	public static int pullAll(SupabaseRestClient http) {
		System.out.println("[RemoteToLocalSync] Starting remote-to-local sync...");
		int totalChanges = 0;
		Map<String, String> newLastPullMap = new HashMap<>();

		try {
			// Pull tables in strict dependency order (NO GLOBAL DB LOCK)
			totalChanges += pullTable(http, "users", SupabaseEndpoints.USERS, newLastPullMap);
			totalChanges += pullTable(http, "company_details", SupabaseEndpoints.COMPANY_DETAILS, newLastPullMap);
			totalChanges += pullTable(http, "bank_details", SupabaseEndpoints.BANK_DETAILS, newLastPullMap);
			totalChanges += pullTable(http, "hsn_sac_master", SupabaseEndpoints.HSN_SAC_MASTER, newLastPullMap);
			totalChanges += pullTable(http, "suppliers", SupabaseEndpoints.SUPPLIERS, newLastPullMap);
			totalChanges += pullTable(http, "clients", SupabaseEndpoints.CLIENTS, newLastPullMap);
			totalChanges += pullTable(http, "jobs", SupabaseEndpoints.JOBS, newLastPullMap);
			totalChanges += pullTable(http, "job_items", SupabaseEndpoints.JOB_ITEMS, newLastPullMap);
			totalChanges += pullTable(http, "printing_items", SupabaseEndpoints.PRINTING_ITEMS, newLastPullMap);
			totalChanges += pullTable(http, "paper_items", SupabaseEndpoints.PAPER_ITEMS, newLastPullMap);
			totalChanges += pullTable(http, "binding_items", SupabaseEndpoints.BINDING_ITEMS, newLastPullMap);
			totalChanges += pullTable(http, "lamination_items", SupabaseEndpoints.LAMINATION_ITEMS, newLastPullMap);
			totalChanges += pullTable(http, "ctp_items", SupabaseEndpoints.CTP_ITEMS, newLastPullMap);
			totalChanges += pullTable(http, "invoice_master", SupabaseEndpoints.INVOICE_MASTER, newLastPullMap);
			totalChanges += pullTable(http, "invoice_job_mapping", SupabaseEndpoints.INVOICE_JOB_MAPPING, newLastPullMap);
			totalChanges += pullTable(http, "invoice_additional_charges", SupabaseEndpoints.INVOICE_ADDITIONAL_CHARGES, newLastPullMap);
			totalChanges += pullTable(http, "invoice_adjustments", SupabaseEndpoints.INVOICE_ADJUSTMENTS, newLastPullMap);
			totalChanges += pullTable(http, "payments", SupabaseEndpoints.PAYMENTS, newLastPullMap);
			totalChanges += pullTable(http, "payment_details", SupabaseEndpoints.PAYMENT_DETAILS, newLastPullMap);
			totalChanges += pullTable(http, "payment_allocations", SupabaseEndpoints.PAYMENT_ALLOCATIONS, newLastPullMap);
			totalChanges += pullTable(http, "document_number_mappings", SupabaseEndpoints.DOCUMENT_NUMBER_MAPPINGS, newLastPullMap);


			System.out.println("[RemoteToLocalSync] Remote-to-local sync completed successfully. Total changes: " + totalChanges);
		} catch (Exception e) {
			System.err.println("[RemoteToLocalSync] Failed to execute remote-to-local sync: " + e.getMessage());
			e.printStackTrace();
		}
		return totalChanges;
	}

	private static int pullTable(SupabaseRestClient http, String table, SupabaseEndpoints endpoint, Map<String, String> newLastPullMap) {
		long startTime = System.currentTimeMillis();
		int fetched = 0;
		int inserted = 0;
		int updated = 0;
		int deleted = 0;
		int skipped = 0;
		int conflicts = 0;

		String lastPullAt = null;
		try (Connection readConn = DBConnection.getConnection()) {
			if (!tableExists(readConn, table)) {
				return 0;
			}
			lastPullAt = getLastPullAt(readConn, table);
		} catch (Exception e) {
			return 0;
		}

		String overlapTimestamp = calculateOverlapTimestamp(lastPullAt);

		try {
			String query = "select=*";
			String timestampCol = "document_number_mappings".equals(table) ? "created_at" : "updated_at";

			if (overlapTimestamp != null) {
				query += "&" + timestampCol + "=gt." + URLEncoder.encode(overlapTimestamp, StandardCharsets.UTF_8);
			}

			// NETWORK CALL OUTSIDE LOCK
			var res = http.get(endpoint, query);
			if (res.statusCode() < 200 || res.statusCode() >= 300) {
				System.err.println("[RemoteToLocalSync] Failed to pull " + table + ": HTTP " + res.statusCode() + " " + res.body());
				return 0;
			}

			String body = res.body();
			JsonArray arr = new JsonArray();
			if (body != null && !body.trim().isEmpty()) {
				JsonElement root = JsonParser.parseString(body);
				if (root.isJsonArray()) {
					arr = root.getAsJsonArray();
				}
			}
			fetched = arr.size();
			String maxTimestamp = lastPullAt;

			// NOW WE TAKE THE EXCLUSIVE WRITE LOCK FOR RAPID DB UPDATES
			try (Connection conn = DBConnection.getExclusiveConnection()) {
				boolean autoCommit = conn.getAutoCommit();
				conn.setAutoCommit(false);
				try {
					try (Statement stmt = conn.createStatement()) {
						stmt.execute("PRAGMA foreign_keys = OFF;");
					}

					List<String> cols = getColumns(conn, table);
					if (cols.isEmpty()) {
						return 0;
					}

					Instant maxInstant = null;
					if (lastPullAt != null) {
						try {
							String fmt = lastPullAt.trim().replace(" ", "T");
							if (!fmt.contains("Z") && !fmt.contains("+") && fmt.length() == 19) {
								fmt += "Z";
							}
							maxInstant = Instant.parse(fmt);
						} catch (Exception e) { service.LoggerService.dbWarn("[SYNC] Could not parse max timestamp for " + table + ": " + e.getMessage()); }
					}

					for (JsonElement el : arr) {
						if (!el.isJsonObject()) {
							continue;
						}
						JsonObject o = el.getAsJsonObject();

						String remoteUpdatedAt = o.has(timestampCol) && !o.get(timestampCol).isJsonNull() ? o.get(timestampCol).getAsString() : null;
						if (remoteUpdatedAt != null) {
							try {
								String formattedRemote = remoteUpdatedAt.trim().replace(" ", "T");
								if (!formattedRemote.contains("Z") && !formattedRemote.contains("+") && formattedRemote.length() == 19) {
									formattedRemote += "Z";
								}
								Instant remoteInst = Instant.parse(formattedRemote);
								if (maxInstant == null || remoteInst.isAfter(maxInstant)) {
									maxInstant = remoteInst;
									maxTimestamp = remoteUpdatedAt;
								}
							} catch (Exception e) { service.LoggerService.dbWarn("[SYNC] Could not parse max timestamp for " + table + ": " + e.getMessage()); }
						}

						String uuid = o.has("uuid") && !o.get("uuid").isJsonNull() ? o.get("uuid").getAsString() : null;
						boolean localExists = false;
						String localSyncStatus = null;
						String localUpdatedAt = null;

						if (uuid != null && cols.contains("sync_status") && cols.contains("updated_at")) {
							try (PreparedStatement checkPs = conn.prepareStatement("SELECT sync_status, updated_at FROM " + table + " WHERE uuid = ?")) {
								checkPs.setString(1, uuid);
								try (ResultSet checkRs = checkPs.executeQuery()) {
									if (checkRs.next()) {
										localExists = true;
										localSyncStatus = checkRs.getString("sync_status");
										localUpdatedAt = checkRs.getString("updated_at");
									}
								}
							}
						}

						boolean shouldUpsert = true;
						if (localExists) {
							String remoteUpdatedAtStr = o.has(timestampCol) && !o.get(timestampCol).isJsonNull() ? o.get(timestampCol).getAsString() : null;
							java.time.Instant localInst = SyncConflictResolver.parseTimestamp(localUpdatedAt);
							java.time.Instant remoteInst = SyncConflictResolver.parseTimestamp(remoteUpdatedAtStr);

							if (remoteInst.equals(localInst)) {
								shouldUpsert = false;
								skipped++;
							} else if ("PENDING".equalsIgnoreCase(localSyncStatus)) {
								conflicts++;
								if (remoteInst.isAfter(localInst)) {
									SyncConflictResolver.logConflict(table, uuid, localUpdatedAt, remoteUpdatedAtStr,
										"Local unpushed edit overwritten by newer remote update", o.toString(), "LAST_WRITE_WINS_REMOTE_WINS");
									shouldUpsert = true;
								} else {
									SyncConflictResolver.logConflict(table, uuid, localUpdatedAt, remoteUpdatedAtStr,
										"Local unpushed edit kept; older remote update rejected", o.toString(), "LAST_WRITE_WINS_LOCAL_WINS");
									shouldUpsert = false;
									skipped++;
								}
							} else {
								if (remoteInst.isBefore(localInst)) {
									shouldUpsert = false;
									skipped++;
								}
							}
						}

						if (!shouldUpsert) {
							continue;
						}

						if ("payment_allocations".equals(table)) {
							String paymentUuid = o.has("payment_uuid") && !o.get("payment_uuid").isJsonNull() ? o.get("payment_uuid").getAsString() : null;
							double allocatedAmount = o.has("allocated_amount") && !o.get("allocated_amount").isJsonNull() ? o.get("allocated_amount").getAsDouble() : 0.0;
							boolean remoteDeleted = false;
							if (o.has("is_deleted") && !o.get("is_deleted").isJsonNull()) {
								remoteDeleted = o.get("is_deleted").getAsBoolean();
							}
							if (paymentUuid != null && !remoteDeleted) {
								boolean rejected = SyncConflictResolver.validateAndResolveDoubleSpend(conn, uuid, paymentUuid, allocatedAmount, true, remoteUpdatedAt, o.toString());
								if (rejected) {
									o.addProperty("is_deleted", true);
									o.addProperty("sync_status", "SYNCED");
								}
							}
						}

						List<String> insertCols = new ArrayList<>();
						List<Object> values = new ArrayList<>();

						for (String col : cols) {
							if (o.has(col)) {
								insertCols.add(col);
								if ("sync_status".equals(col)) {
									values.add("SYNCED");
									continue;
								}
								JsonElement val = o.get(col);
								if (val.isJsonNull()) {
									values.add(null);
								} else if (val.isJsonPrimitive()) {
									var prim = val.getAsJsonPrimitive();
									if (prim.isBoolean()) {
										values.add(prim.getAsBoolean() ? 1 : 0);
									} else if (prim.isNumber()) {
										if (prim.getAsString().contains(".")) {
											values.add(prim.getAsDouble());
										} else {
											values.add(prim.getAsLong());
										}
									} else {
										values.add(prim.getAsString());
									}
								} else {
									values.add(val.toString());
								}
							}
						}

						if (insertCols.isEmpty()) {
							continue;
						}

						StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(" (");
						for (int i = 0; i < insertCols.size(); i++) {
							sb.append(insertCols.get(i));
							if (i < insertCols.size() - 1) {
								sb.append(",");
							}
						}
						sb.append(") VALUES (");
						for (int i = 0; i < insertCols.size(); i++) {
							sb.append("?");
							if (i < insertCols.size() - 1) {
								sb.append(",");
							}
						}
						sb.append(") ON CONFLICT(uuid) DO UPDATE SET ");
						boolean first = true;
						for (String col : insertCols) {
							if ("uuid".equalsIgnoreCase(col) || "id".equalsIgnoreCase(col)) {
								continue;
							}
							if (!first) {
								sb.append(", ");
							}
							sb.append(col).append("=excluded.").append(col);
							first = false;
						}

						try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
							for (int i = 0; i < values.size(); i++) {
								Object v = values.get(i);
								if (v == null) {
									ps.setNull(i + 1, java.sql.Types.VARCHAR);
								} else {
									ps.setObject(i + 1, v);
								}
							}
							ps.executeUpdate();
						}

						boolean isDeletedVal = false;
						if (o.has("is_deleted") && !o.get("is_deleted").isJsonNull()) {
							try {
								isDeletedVal = o.get("is_deleted").getAsBoolean();
							} catch (Exception ignored) {
								isDeletedVal = o.get("is_deleted").getAsInt() != 0;
							}
						}

						if (isDeletedVal) {
							deleted++;
						} else if (localExists) {
							updated++;
						} else {
							inserted++;
						}
					}

					// REMOTE DELETION RECONCILIATION
					// Fetch all active remote UUIDs for this table to detect hard-deleted or soft-deleted remote records
					try {
						String selectQuery = "select=uuid" + (cols.contains("is_deleted") ? ",is_deleted" : "");
						var remoteUuidsRes = http.get(endpoint, selectQuery);
						if (remoteUuidsRes.statusCode() >= 200 && remoteUuidsRes.statusCode() < 300) {
							String rBody = remoteUuidsRes.body();
							if (rBody != null && !rBody.trim().isEmpty()) {
								JsonElement rRoot = JsonParser.parseString(rBody);
								if (rRoot.isJsonArray()) {
									java.util.Set<String> activeRemoteUuids = new java.util.HashSet<>();
									java.util.Set<String> softDeletedRemoteUuids = new java.util.HashSet<>();
									for (JsonElement rEl : rRoot.getAsJsonArray()) {
										if (rEl.isJsonObject()) {
											JsonObject rObj = rEl.getAsJsonObject();
											String rUuid = rObj.has("uuid") && !rObj.get("uuid").isJsonNull() ? rObj.get("uuid").getAsString() : null;
											if (rUuid != null) {
												boolean rDeleted = false;
												if (rObj.has("is_deleted") && !rObj.get("is_deleted").isJsonNull()) {
													try {
														rDeleted = rObj.get("is_deleted").getAsBoolean();
													} catch (Exception ex) {
														rDeleted = rObj.get("is_deleted").getAsInt() != 0;
													}
												}
												if (rDeleted) {
													softDeletedRemoteUuids.add(rUuid);
												} else {
													activeRemoteUuids.add(rUuid);
												}
											}
										}
									}

									// Check local synced records
									boolean hasIsDeletedCol = cols.contains("is_deleted");
									String localQuery = hasIsDeletedCol
											? "SELECT uuid, is_deleted FROM " + table + " WHERE sync_status = 'SYNCED'"
											: "SELECT uuid FROM " + table + " WHERE sync_status = 'SYNCED'";
									try (PreparedStatement lPs = conn.prepareStatement(localQuery);
										 ResultSet lRs = lPs.executeQuery()) {
										while (lRs.next()) {
											String lUuid = lRs.getString("uuid");
											boolean currentlyLocalDeleted = hasIsDeletedCol && lRs.getInt("is_deleted") == 1;

											if (softDeletedRemoteUuids.contains(lUuid)) {
												if (!currentlyLocalDeleted && hasIsDeletedCol) {
													try (PreparedStatement updPs = conn.prepareStatement(
															"UPDATE " + table + " SET is_deleted = 1, sync_status = 'SYNCED', deleted_at = datetime('now') WHERE uuid = ?")) {
														updPs.setString(1, lUuid);
														updPs.executeUpdate();
													}
													deleted++;
													System.out.println("[RemoteToLocalSync] Reconciled remote soft-deletion for table=" + table + ", uuid=" + lUuid);
												}
											} else if (!activeRemoteUuids.contains(lUuid)) {
												// Record was hard-deleted (physically deleted) from remote Supabase!
												if (hasIsDeletedCol) {
													if (!currentlyLocalDeleted) {
														try (PreparedStatement updPs = conn.prepareStatement(
																"UPDATE " + table + " SET is_deleted = 1, sync_status = 'SYNCED', deleted_at = datetime('now') WHERE uuid = ?")) {
															updPs.setString(1, lUuid);
															updPs.executeUpdate();
														}
														deleted++;
														System.out.println("[RemoteToLocalSync] Reconciled remote physical hard-deletion for table=" + table + ", uuid=" + lUuid);
													}
												} else {
													// Soft delete locally for child mapping tables to preserve recovery data
													try (PreparedStatement updPs = conn.prepareStatement("UPDATE " + table + " SET is_deleted = 1, sync_status = 'SYNCED', deleted_at = datetime('now') WHERE uuid = ?")) {
														updPs.setString(1, lUuid);
														updPs.executeUpdate();
													}
													deleted++;
													System.out.println("[RemoteToLocalSync] Reconciled remote physical hard-deletion (local soft-delete) for table=" + table + ", uuid=" + lUuid);
												}
											}
										}
									}
								}
							}
						}
					} catch (Exception rEx) {
						System.err.println("[RemoteToLocalSync] Remote deletion reconciliation failed for " + table + ": " + rEx.getMessage());
					}

					if (maxTimestamp != null) {
						updateLastPullAt(conn, table, maxTimestamp);
						newLastPullMap.put(table, maxTimestamp);
					}

					try (Statement stmt = conn.createStatement()) {
						stmt.execute("PRAGMA foreign_keys = ON;");
					}
					conn.commit();
				} catch (Exception e) {
					conn.rollback();
					throw e;
				} finally {
					conn.setAutoCommit(autoCommit);
				}
			}

			logMetrics(table, lastPullAt, fetched, inserted, updated, deleted, skipped, conflicts, startTime, maxTimestamp);

		} catch (Exception e) {
			System.err.println("[RemoteToLocalSync] Error syncing table " + table + ": " + e.getMessage());
			e.printStackTrace();
		}

		return inserted + updated + deleted;
	}

	private static void logMetrics(String table, String lastPull, int fetched, int inserted, int updated,
								   int deleted, int skipped, int conflicts, long startTime, String newLastPull) {
		long duration = System.currentTimeMillis() - startTime;
		System.out.println(String.format(
			"[RemoteToLocalSync]%n" +
			"Table=%s%n" +
			"LastPull=%s%n" +
			"Fetched=%d%n" +
			"Inserted=%d%n" +
			"Updated=%d%n" +
			"Deleted=%d%n" +
			"Skipped=%d%n" +
			"Conflicts=%d%n" +
			"Duration=%dms%n" +
			"NewLastPull=%s%n",
			table,
			lastPull == null ? "None" : lastPull,
			fetched, inserted, updated, deleted, skipped, conflicts,
			duration, newLastPull == null ? "None" : newLastPull
		));
	}

	private static String calculateOverlapTimestamp(String lastPullAt) {
		if (lastPullAt == null || lastPullAt.trim().isEmpty()) {
			return null;
		}
		try {
			String formatted = lastPullAt.trim().replace(" ", "T");
			if (!formatted.contains("Z") && !formatted.contains("+") && formatted.length() == 19) {
				formatted += "Z";
			}
			java.time.Instant instant = java.time.Instant.parse(formatted);
			java.time.Instant overlap = instant.minusSeconds(60);
			return overlap.toString();
		} catch (Exception e) {
			System.err.println("[RemoteToLocalSync] Failed to parse last_pull_at: " + lastPullAt + ", doing full pull.");
			return null;
		}
	}

	private static String getLastPullAt(Connection conn, String table) {
		String sql = "SELECT last_pull_at FROM sync_metadata WHERE table_name = ?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, table);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getString(1);
				}
			}
		} catch (Exception e) {
			// Table might not exist yet, or other issue - safe fallback to null
		}
		return null;
	}

	private static void updateLastPullAt(Connection conn, String table, String timestamp) {
		String sql = "INSERT INTO sync_metadata (table_name, last_pull_at) VALUES (?, ?) " +
		             "ON CONFLICT(table_name) DO UPDATE SET last_pull_at = excluded.last_pull_at";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, table);
			ps.setString(2, timestamp);
			ps.executeUpdate();
		} catch (Exception e) {
			System.err.println("[RemoteToLocalSync] Error updating last_pull_at for " + table + ": " + e.getMessage());
		}
	}

	private static List<String> getColumns(Connection conn, String table) throws Exception {
		List<String> cols = new ArrayList<>();
		try (var ps = conn.prepareStatement("PRAGMA table_info(" + table + ")")) {
			try (var rs = ps.executeQuery()) {
				while (rs.next()) {
					cols.add(rs.getString("name"));
				}
			}
		}
		return cols;
	}

	private static boolean tableExists(Connection conn, String table) throws Exception {
		try (var ps = conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
			ps.setString(1, table);
			try (var rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}
}
