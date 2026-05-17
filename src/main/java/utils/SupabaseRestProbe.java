package utils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

import api.supabase.SupabaseRestClient;
import api.supabase.SupabaseTablesHealthCheck;

/**
 * Lightweight checks against a Supabase project (PostgREST + Auth health).
 */
public final class SupabaseRestProbe {

	private SupabaseRestProbe() {
	}

	public static String normalizeProjectUrl(String raw) {
		if (raw == null) {
			return "";
		}
		String u = raw.trim();
		if (u.isEmpty()) {
			return "";
		}
		while (u.endsWith("/")) {
			u = u.substring(0, u.length() - 1);
		}
		int idx = u.indexOf("/rest/v1");
		if (idx >= 0) {
			return u.substring(0, idx);
		}
		return u;
	}

	/** PostgREST often returns 401 on bare {@code /rest/v1/}; use a real table URL for health checks. */
	private static String restV1ClientsProbeUrl(String projectRoot) {
		return projectRoot + "/rest/v1/clients?select=id&limit=1";
	}

	private static String authHealthUrl(String projectRoot) {
		return projectRoot + "/auth/v1/health";
	}

	/**
	 * @return human-readable result (starts with OK or FAILED)
	 */
	public static String verifyConnection(String projectUrl, String anonKey) {
		String root = normalizeProjectUrl(projectUrl);
		if (root.isEmpty()) {
			return "FAILED: Supabase URL is empty.";
		}
		if (anonKey == null || anonKey.isBlank()) {
			return "FAILED: Anon key is empty.";
		}
		String key = anonKey.trim();

		try {
			int code = httpGetCode(restV1ClientsProbeUrl(root), key);
			if (code >= 200 && code < 300) {
				return "OK: PostgREST reachable (HTTP " + code + ", clients probe).";
			}
			if (code == 401 || code == 403) {
				return "FAILED: HTTP " + code + " — check anon key and RLS on public.clients.";
			}
			return "FAILED: PostgREST HTTP " + code + " — check project URL.";
		} catch (Exception e) {
			return "FAILED: " + e.getMessage();
		}
	}

	/**
	 * Verifies REST again and probes Auth service health (no credentials required).
	 */
	public static String syncTest(String projectUrl, String anonKey) {
		String root = normalizeProjectUrl(projectUrl);
		if (root.isEmpty()) {
			return "FAILED: Supabase URL is empty.";
		}
		if (anonKey == null || anonKey.isBlank()) {
			return "FAILED: Anon key is empty.";
		}
		String key = anonKey.trim();
		StringBuilder sb = new StringBuilder();
		try {
			int rest = httpGetCode(restV1ClientsProbeUrl(root), key);
			sb.append(rest >= 200 && rest < 300 ? "REST OK (" + rest + ", clients). " : "REST HTTP " + rest + ". ");
		} catch (Exception e) {
			return "FAILED: REST — " + e.getMessage();
		}
		try {
			int auth = httpGetCode(authHealthUrl(root), key);
			sb.append(auth >= 200 && auth < 300 ? "Auth health OK (" + auth + "). " : "Auth health HTTP " + auth + ". ");
		} catch (Exception e) {
			sb.append("Auth health: ").append(e.getMessage()).append(". ");
		}
		try {
			SupabaseRestClient client = new SupabaseRestClient(projectUrl, key);
			var rows = SupabaseTablesHealthCheck.probeAll(client);
			sb.append(System.lineSeparator()).append(SupabaseTablesHealthCheck.formatReport(rows));
		} catch (IllegalArgumentException e) {
			sb.append(System.lineSeparator()).append("Table probe skipped: ").append(e.getMessage());
		} catch (Exception e) {
			sb.append(System.lineSeparator()).append("Table probe error: ").append(e.getMessage());
		}
		return sb.toString().trim();
	}

	private static int httpGetCode(String url, String anonKey) throws Exception {
		HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
		c.setRequestMethod("GET");
		c.setConnectTimeout(15_000);
		c.setReadTimeout(15_000);
		c.setRequestProperty("apikey", anonKey);
		c.setRequestProperty("Authorization", "Bearer " + anonKey);
		c.setRequestProperty("Accept", "application/json");
		int code = c.getResponseCode();
		drain(c);
		c.disconnect();
		return code;
	}

	private static void drain(HttpURLConnection c) {
		try (InputStream in = c.getErrorStream() != null ? c.getErrorStream() : c.getInputStream()) {
			if (in != null) {
				in.readAllBytes();
			}
		} catch (Exception ignored) {
			// ignore
		}
	}
}
