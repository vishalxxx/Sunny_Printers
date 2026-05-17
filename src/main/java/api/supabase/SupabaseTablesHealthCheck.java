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

	public static List<TableProbe> probeAll(SupabaseRestClient client) {
		List<TableProbe> rows = new ArrayList<>();
		for (SupabaseEndpoints t : SupabaseEndpoints.values()) {
			rows.add(probeOne(client, t));
		}
		return rows;
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
		int ok = 0;
		for (TableProbe r : rows) {
			boolean good = r.status() >= 200 && r.status() < 300;
			if (good) {
				ok++;
			}
			sb.append(String.format("%-26s HTTP %4d %s%n",
					r.endpoint().pathSegment(),
					r.status(),
					good ? "OK" : ""));
			if (!good && r.summary() != null && !r.summary().isBlank()) {
				sb.append("    ").append(r.summary().replace("\n", " ")).append('\n');
			}
		}
		sb.insert(0, String.format("Tables reachable: %d / %d%n", ok, rows.size()));
		return sb.toString().trim();
	}
}
