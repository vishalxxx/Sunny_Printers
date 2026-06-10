package service.sync;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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

	public static void pullAll(SupabaseRestClient http) {
		System.out.println("[RemoteToLocalSync] Starting remote-to-local sync...");
		try (Connection conn = DBConnection.getConnection()) {
			try (Statement stmt = conn.createStatement()) {
				stmt.execute("PRAGMA foreign_keys = OFF;");
			}

			pullTable(http, conn, "users", SupabaseEndpoints.USERS);
			pullTable(http, conn, "company_details", SupabaseEndpoints.COMPANY_DETAILS);
			pullTable(http, conn, "bank_details", SupabaseEndpoints.BANK_DETAILS);
			pullTable(http, conn, "hsn_sac_master", SupabaseEndpoints.HSN_SAC_MASTER);
			pullTable(http, conn, "suppliers", SupabaseEndpoints.SUPPLIERS);
			pullTable(http, conn, "clients", SupabaseEndpoints.CLIENTS);
			pullTable(http, conn, "invoice_master", SupabaseEndpoints.INVOICE_MASTER);
			pullTable(http, conn, "jobs", SupabaseEndpoints.JOBS);
			pullTable(http, conn, "job_items", SupabaseEndpoints.JOB_ITEMS);
			pullTable(http, conn, "printing_items", SupabaseEndpoints.PRINTING_ITEMS);
			pullTable(http, conn, "paper_items", SupabaseEndpoints.PAPER_ITEMS);
			pullTable(http, conn, "binding_items", SupabaseEndpoints.BINDING_ITEMS);
			pullTable(http, conn, "lamination_items", SupabaseEndpoints.LAMINATION_ITEMS);
			pullTable(http, conn, "ctp_items", SupabaseEndpoints.CTP_ITEMS);
			pullTable(http, conn, "invoice_job_mapping", SupabaseEndpoints.INVOICE_JOB_MAPPING);
			pullTable(http, conn, "invoice_adjustments", SupabaseEndpoints.INVOICE_ADJUSTMENTS);
			pullTable(http, conn, "payments", SupabaseEndpoints.PAYMENTS);
			pullTable(http, conn, "payment_details", SupabaseEndpoints.PAYMENT_DETAILS);
			pullTable(http, conn, "payment_allocations", SupabaseEndpoints.PAYMENT_ALLOCATIONS);
			pullTable(http, conn, "document_number_mappings", SupabaseEndpoints.DOCUMENT_NUMBER_MAPPINGS);

			try (Statement stmt = conn.createStatement()) {
				stmt.execute("PRAGMA foreign_keys = ON;");
			}
			System.out.println("[RemoteToLocalSync] Remote-to-local sync completed successfully.");
		} catch (Exception e) {
			System.err.println("[RemoteToLocalSync] Failed to execute remote-to-local sync: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static void pullTable(SupabaseRestClient http, Connection conn, String table, SupabaseEndpoints endpoint) {
		try {
			if (!tableExists(conn, table)) {
				return;
			}
			var res = http.get(endpoint, "select=*");
			if (res.statusCode() < 200 || res.statusCode() >= 300) {
				System.err.println("[RemoteToLocalSync] Failed to pull " + table + ": HTTP " + res.statusCode() + " " + res.body());
				return;
			}
			String body = res.body();
			if (body == null || body.trim().isEmpty()) {
				return;
			}
			JsonElement root = JsonParser.parseString(body);
			if (!root.isJsonArray()) {
				return;
			}
			JsonArray arr = root.getAsJsonArray();
			if (arr.size() == 0) {
				return;
			}

			List<String> cols = getColumns(conn, table);
			if (cols.isEmpty()) {
				return;
			}

			for (JsonElement el : arr) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject o = el.getAsJsonObject();

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

				StringBuilder sb = new StringBuilder("INSERT OR REPLACE INTO ").append(table).append(" (");
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
				sb.append(")");

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
			}
			System.out.println("[RemoteToLocalSync] Successfully pulled and upserted " + arr.size() + " rows into " + table);
		} catch (Exception e) {
			System.err.println("[RemoteToLocalSync] Error syncing table " + table + ": " + e.getMessage());
			e.printStackTrace();
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
