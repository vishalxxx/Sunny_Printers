package utils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

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

	private static String restV1Url(String projectRoot) {
		return projectRoot + "/rest/v1/";
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
			int code = httpGetCode(restV1Url(root), key);
			if (code >= 200 && code < 300) {
				return "OK: PostgREST reachable (HTTP " + code + ").";
			}
			if (code == 401 || code == 403) {
				return "FAILED: HTTP " + code + " — check anon key.";
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
			int rest = httpGetCode(restV1Url(root), key);
			sb.append(rest >= 200 && rest < 300 ? "REST OK (" + rest + "). " : "REST HTTP " + rest + ". ");
		} catch (Exception e) {
			return "FAILED: REST — " + e.getMessage();
		}
		try {
			int auth = httpGetCodeNoApiKey(authHealthUrl(root));
			sb.append(auth >= 200 && auth < 300 ? "Auth health OK (" + auth + "). " : "Auth health HTTP " + auth + ". ");
		} catch (Exception e) {
			sb.append("Auth health: ").append(e.getMessage()).append(". ");
		}
		sb.append("Full data sync is not wired in this build.");
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

	private static int httpGetCodeNoApiKey(String url) throws Exception {
		HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
		c.setRequestMethod("GET");
		c.setConnectTimeout(10_000);
		c.setReadTimeout(10_000);
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
