package api.supabase;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

import utils.SupabaseRestProbe;

/**
 * Minimal synchronous PostgREST client (anon key / service role key as Bearer).
 */
public class SupabaseRestClient {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
	private static final Duration PING_TIMEOUT = Duration.ofSeconds(3);

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.build();

	private final String restV1Base;
	private final String anonKey;
	private final String bearerToken;

	/**
	 * @param projectRoot e.g. {@code https://xxxx.supabase.co} (with or without {@code /rest/v1})
	 * @param anonKey       Supabase anon (or service role) JWT
	 */
	public SupabaseRestClient(String projectRoot, String anonKey) {
		this(projectRoot, anonKey, null);
	}

	/**
	 * @param bearerToken user access token from Supabase Auth; when set, used for Authorization header
	 */
	public SupabaseRestClient(String projectRoot, String anonKey, String bearerToken) {
		String root = SupabaseRestProbe.normalizeProjectUrl(Objects.requireNonNullElse(projectRoot, ""));
		if (root.isEmpty()) {
			throw new IllegalArgumentException("project URL is empty");
		}
		String key = Objects.requireNonNullElse(anonKey, "").trim();
		if (key.isEmpty()) {
			throw new IllegalArgumentException("anon key is empty");
		}
		this.restV1Base = root.endsWith("/") ? root + "rest/v1/" : root + "/rest/v1/";
		this.anonKey = key;
		this.bearerToken = bearerToken != null && !bearerToken.isBlank() ? bearerToken.trim() : null;
	}

	public String restV1Base() {
		return restV1Base;
	}

	public HttpResponse<String> get(SupabaseEndpoints table, String queryWithoutLeadingQuestion)
			throws IOException, InterruptedException {
		return getWithTimeout(table, queryWithoutLeadingQuestion, REQUEST_TIMEOUT);
	}

	public HttpResponse<String> getWithTimeout(SupabaseEndpoints table, String queryWithoutLeadingQuestion, Duration timeout)
			throws IOException, InterruptedException {
		String q = queryWithoutLeadingQuestion == null || queryWithoutLeadingQuestion.isBlank()
				? ""
				: "?" + queryWithoutLeadingQuestion;
		URI uri = URI.create(restV1Base + table.pathSegment() + q);
		HttpRequest.Builder gb = HttpRequest.newBuilder(uri).GET().timeout(timeout);
		applyDefaultHeaders(gb);
		return send(gb.build());
	}

	/** Fast probe for offline detection (short timeout, no full sync payload). */
	public boolean ping() {
		try {
			URI uri = URI.create(restV1Base + SupabaseEndpoints.NUMBER_SEQUENCES.pathSegment()
					+ "?select=sequence_key&limit=1");
			HttpRequest.Builder b = HttpRequest.newBuilder(uri).GET().timeout(PING_TIMEOUT);
			applyDefaultHeaders(b);
			int code = http.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
			return code >= 200 && code < 500;
		} catch (Exception e) {
			return false;
		}
	}

	public HttpResponse<String> getRawPath(String pathAfterRestV1, String queryWithoutLeadingQuestion)
			throws IOException, InterruptedException {
		String p = pathAfterRestV1.startsWith("/") ? pathAfterRestV1.substring(1) : pathAfterRestV1;
		String q = queryWithoutLeadingQuestion == null || queryWithoutLeadingQuestion.isBlank()
				? ""
				: "?" + queryWithoutLeadingQuestion;
		URI uri = URI.create(restV1Base + p + q);
		HttpRequest.Builder gb = HttpRequest.newBuilder(uri).GET().timeout(REQUEST_TIMEOUT);
		applyDefaultHeaders(gb);
		return send(gb.build());
	}

	public HttpResponse<String> postJson(SupabaseEndpoints table, String jsonBody, String preferHeader)
			throws IOException, InterruptedException {
		return postJsonWithQuery(table, null, jsonBody, preferHeader);
	}

	/**
	 * POST with optional query string (e.g. {@code on_conflict=id} for upserts).
	 */
	public HttpResponse<String> postJsonWithQuery(SupabaseEndpoints table, String queryWithoutLeadingQuestion,
			String jsonBody, String preferHeader) throws IOException, InterruptedException {
		String q = queryWithoutLeadingQuestion == null || queryWithoutLeadingQuestion.isBlank()
				? ""
				: "?" + queryWithoutLeadingQuestion;
		URI uri = URI.create(restV1Base + table.pathSegment() + q);
		HttpRequest.Builder b = HttpRequest.newBuilder(uri)
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody))
				.header("Content-Type", "application/json")
				.timeout(REQUEST_TIMEOUT);
		applyDefaultHeaders(b);
		b.header("Prefer", preferHeader == null || preferHeader.isBlank() ? "return=minimal" : preferHeader);
		return send(b.build());
	}

	public HttpResponse<String> patchJson(SupabaseEndpoints table, String postgrestFilter, String jsonBody,
			String preferHeader) throws IOException, InterruptedException {
		String q = postgrestFilter == null || postgrestFilter.isBlank()
				? ""
				: "?" + postgrestFilter;
		URI uri = URI.create(restV1Base + table.pathSegment() + q);
		HttpRequest.Builder b = HttpRequest.newBuilder(uri)
				.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody))
				.header("Content-Type", "application/json")
				.timeout(REQUEST_TIMEOUT);
		applyDefaultHeaders(b);
		b.header("Prefer", preferHeader == null || preferHeader.isBlank() ? "return=minimal" : preferHeader);
		return send(b.build());
	}

	public HttpResponse<String> delete(SupabaseEndpoints table, String postgrestFilter)
			throws IOException, InterruptedException {
		throw new UnsupportedOperationException(
				"Physical HTTP DELETE on business table '" + (table != null ? table.pathSegment() : "unknown")
						+ "' is permanently blocked by architectural policy. Use soft-delete (PATCH) instead.");
	}

	public HttpResponse<String> postJsonRaw(String pathSegment, String jsonBody, String preferHeader)
			throws IOException, InterruptedException {
		URI uri = URI.create(restV1Base + pathSegment);
		HttpRequest.Builder b = HttpRequest.newBuilder(uri)
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody))
				.header("Content-Type", "application/json")
				.timeout(REQUEST_TIMEOUT);
		applyDefaultHeaders(b);
		b.header("Prefer", preferHeader == null || preferHeader.isBlank() ? "return=minimal" : preferHeader);
		return send(b.build());
	}

	private void applyDefaultHeaders(HttpRequest.Builder b) {
		b.header("apikey", anonKey);
		String auth = bearerToken != null ? bearerToken : anonKey;
		b.header("Authorization", "Bearer " + auth);
		b.header("Accept", "application/json");
	}

	private HttpResponse<String> send(HttpRequest req) throws IOException, InterruptedException {
		return http.send(req, HttpResponse.BodyHandlers.ofString());
	}
}
