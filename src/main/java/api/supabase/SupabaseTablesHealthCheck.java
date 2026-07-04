package api.supabase;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Probes each configured PostgREST table with a light {@code GET} (limit 1).
 * HTTP 200 with JSON array is success; 401/403 usually means RLS; 404 missing table.
 */
public final class SupabaseTablesHealthCheck {

	private SupabaseTablesHealthCheck() {
	}

	/**
	 * Optional CLI: pass {@code projectUrl} and {@code anonKey}, or no args to read from local SQLite
	 * {@code supabase_settings} (classpath must include {@code sqlite-jdbc}, e.g. Maven dependency list).
	 * Easiest check: General Settings > Sync test (runs the same table probe in-app).
	 */
	public static void main(String[] args) throws Exception {
		SupabaseRestClient client;
		if (args.length >= 2) {
			client = new SupabaseRestClient(args[0], args[1]);
		} else {
			client = SupabaseClients.fromLocalDatabase();
		}
		System.out.println(formatReport(probeAll(client)));
	}

	public record TableProbe(SupabaseEndpoints endpoint, int status, String summary) {
	}

	public static final java.util.Set<SupabaseEndpoints> LOCAL_ONLY_TABLES = java.util.Set.of(
			SupabaseEndpoints.SYSTEM_SETTINGS,
			SupabaseEndpoints.EMAIL_SETTINGS,
			SupabaseEndpoints.SUPABASE_SETTINGS
	);

	public static List<TableProbe> probeAll(SupabaseRestClient client) {
		List<TableProbe> rows = new ArrayList<>();
		for (SupabaseEndpoints t : SupabaseEndpoints.values()) {
			if (LOCAL_ONLY_TABLES.contains(t)) {
				boolean exists = checkLocalTableExists(t.pathSegment());
				rows.add(new TableProbe(t, 0, exists ? "LOCAL ONLY (Exists)" : "LOCAL ONLY (Missing)"));
			} else {
				rows.add(probeOne(client, t));
			}
		}
		return rows;
	}

	private static boolean checkLocalTableExists(String tableName) {
		try (java.sql.Connection conn = utils.DBConnection.getConnection();
			 java.sql.PreparedStatement ps = conn.prepareStatement(
				"SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
			ps.setString(1, tableName);
			try (java.sql.ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		} catch (Exception e) {
			return false;
		}
	}

	public static TableProbe probeOne(SupabaseRestClient client, SupabaseEndpoints table) {
		try {
			HttpResponse<String> res = client.get(table, "select=*&limit=1");
			int code = res.statusCode();
			String body = res.body() == null ? "" : res.body();
			String sum = summarizeBody(code, body);
			return new TableProbe(table, code, sum);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new TableProbe(table, -1, e.getMessage() != null ? e.getMessage() : "interrupted");
		} catch (IOException e) {
			return new TableProbe(table, -1, e.getMessage() != null ? e.getMessage() : e.toString());
		}
	}

	private static String summarizeBody(int code, String body) {
		if (code >= 200 && code < 300) {
			String b = body.trim();
			if (b.startsWith("[")) {
				return b.length() > 120 ? b.substring(0, 120) + "..." : b;
			}
			return b.length() > 160 ? b.substring(0, 160) + "..." : b;
		}
		return body != null && body.length() > 200 ? body.substring(0, 200) + "..." : String.valueOf(body);
	}

	public static String formatReport(List<TableProbe> rows) {
		StringBuilder sb = new StringBuilder();
		int cloudOk = 0;
		int cloudTotal = 0;
		List<TableProbe> localRows = new ArrayList<>();
		List<TableProbe> cloudRows = new ArrayList<>();

		for (TableProbe r : rows) {
			if (LOCAL_ONLY_TABLES.contains(r.endpoint())) {
				localRows.add(r);
			} else {
				cloudRows.add(r);
				cloudTotal++;
				if (r.status() >= 200 && r.status() < 300) {
					cloudOk++;
				}
			}
		}

		sb.append("--- Cloud Sync Tables Health Check ---\n");
		for (TableProbe r : cloudRows) {
			boolean good = r.status() >= 200 && r.status() < 300;
			sb.append(String.format("%-26s HTTP %4d %s%n",
					r.endpoint().pathSegment(),
					r.status(),
					good ? "OK" : ""));
			if (!good && r.summary() != null && !r.summary().isBlank()) {
				sb.append("    ").append(r.summary().replace("\n", " ")).append('\n');
			}
		}

		sb.append("\n--- Local-Only Tables Status ---\n");
		for (TableProbe r : localRows) {
			sb.append(String.format("%-26s %s%n",
					r.endpoint().pathSegment(),
					r.summary()));
		}

		sb.insert(0, String.format("Cloud tables reachable: %d / %d%n", cloudOk, cloudTotal));
		return sb.toString().trim();
	}
}
