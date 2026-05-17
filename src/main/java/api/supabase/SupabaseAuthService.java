package api.supabase;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import utils.SupabaseRestProbe;

/**
 * Supabase Auth (email + password): sign-in and sign-up.
 */
public final class SupabaseAuthService {

	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.build();

	public SupabaseAuthResult signInWithPassword(String projectUrl, String anonKey, String email,
			String password) {
		return tokenRequest(projectUrl, anonKey, "/auth/v1/token?grant_type=password",
				buildCredentialsJson(email, password), "Sign in failed");
	}

	public SupabaseAuthResult signUp(String projectUrl, String anonKey, String email, String password) {
		JsonObject signup = new JsonObject();
		signup.addProperty("email", email.trim());
		signup.addProperty("password", password);
		String body = signup.toString();
		try {
			HttpResponse<String> res = post(projectUrl, anonKey, "/auth/v1/signup", body);
			int code = res.statusCode();
			if (code >= 200 && code < 300) {
				return parseAuthResponse(res.body(),
						"Account created. Check your email to confirm, then sign in.");
			}
			return SupabaseAuthResult.fail(parseErrorMessage(res.body(), code, "Sign up failed"));
		} catch (Exception e) {
			return SupabaseAuthResult.fail(e.getMessage() != null ? e.getMessage() : "Sign up failed");
		}
	}

	private SupabaseAuthResult tokenRequest(String projectUrl, String anonKey, String path, String jsonBody,
			String defaultError) {
		try {
			HttpResponse<String> res = post(projectUrl, anonKey, path, jsonBody);
			int code = res.statusCode();
			if (code >= 200 && code < 300) {
				return parseAuthResponse(res.body(), null);
			}
			return SupabaseAuthResult.fail(parseErrorMessage(res.body(), code, defaultError));
		} catch (Exception e) {
			return SupabaseAuthResult.fail(e.getMessage() != null ? e.getMessage() : defaultError);
		}
	}

	private static String buildCredentialsJson(String email, String password) {
		JsonObject o = new JsonObject();
		o.addProperty("email", email.trim());
		o.addProperty("password", password);
		return o.toString();
	}

	private HttpResponse<String> post(String projectUrl, String anonKey, String path, String body)
			throws IOException, InterruptedException {
		String root = SupabaseRestProbe.normalizeProjectUrl(projectUrl);
		if (root.isEmpty()) {
			throw new IOException("Supabase URL is not configured");
		}
		String key = anonKey == null ? "" : anonKey.trim();
		if (key.isEmpty()) {
			throw new IOException("Supabase anon key is not configured");
		}
		URI uri = URI.create(root + path);
		HttpRequest req = HttpRequest.newBuilder(uri)
				.timeout(TIMEOUT)
				.header("apikey", key)
				.header("Authorization", "Bearer " + key)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return http.send(req, HttpResponse.BodyHandlers.ofString());
	}

	private static SupabaseAuthResult parseAuthResponse(String body, String messageIfNoToken) {
		if (body == null || body.isBlank()) {
			if (messageIfNoToken != null) {
				return SupabaseAuthResult.fail(messageIfNoToken);
			}
			return SupabaseAuthResult.fail("Empty response from Supabase Auth");
		}
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			String access = root.has("access_token") && !root.get("access_token").isJsonNull()
					? root.get("access_token").getAsString()
					: null;
			String refresh = root.has("refresh_token") && !root.get("refresh_token").isJsonNull()
					? root.get("refresh_token").getAsString()
					: null;
			String userId = null;
			String email = null;
			if (root.has("user") && root.get("user").isJsonObject()) {
				JsonObject user = root.getAsJsonObject("user");
				if (user.has("id") && !user.get("id").isJsonNull()) {
					userId = user.get("id").getAsString();
				}
				if (user.has("email") && !user.get("email").isJsonNull()) {
					email = user.get("email").getAsString();
				}
			}
			if (access != null && !access.isBlank()) {
				return SupabaseAuthResult.ok(access, refresh, userId, email);
			}
			if (userId != null && messageIfNoToken != null) {
				return new SupabaseAuthResult(true, null, null, userId, email, messageIfNoToken);
			}
			if (messageIfNoToken != null) {
				return SupabaseAuthResult.fail(messageIfNoToken);
			}
			return SupabaseAuthResult.fail("No access token in Supabase response (check email confirmation settings)");
		} catch (Exception e) {
			return SupabaseAuthResult.fail("Invalid Supabase Auth response");
		}
	}

	private static String parseErrorMessage(String body, int code, String fallback) {
		if (body != null && !body.isBlank()) {
			try {
				JsonObject o = JsonParser.parseString(body).getAsJsonObject();
				if (o.has("msg") && !o.get("msg").isJsonNull()) {
					return o.get("msg").getAsString();
				}
				if (o.has("error_description") && !o.get("error_description").isJsonNull()) {
					return o.get("error_description").getAsString();
				}
				if (o.has("message") && !o.get("message").isJsonNull()) {
					return o.get("message").getAsString();
				}
			} catch (Exception ignored) {
			}
		}
		return fallback + " (HTTP " + code + ")";
	}
}
