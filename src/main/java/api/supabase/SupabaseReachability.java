package api.supabase;

import java.util.Optional;

/**
 * Probes whether Supabase PostgREST is reachable (not just configured in settings).
 */
public final class SupabaseReachability {

	private static volatile Boolean cachedReachable;
	private static volatile long cachedAtMs;
	private static final long CACHE_TTL_OK_MS = 8_000;
	/** When offline, avoid repeated TCP/DNS timeouts on every UI action. */
	private static final long CACHE_TTL_FAIL_MS = 45_000;

	private SupabaseReachability() {
	}

	public static boolean isReachable() {
		Optional<SupabaseRestClient> httpOpt = SupabaseGate.restClientIfConfigured();
		if (httpOpt.isEmpty()) {
			cachedReachable = false;
			return false;
		}
		long now = System.currentTimeMillis();
		long ttl = Boolean.TRUE.equals(cachedReachable) ? CACHE_TTL_OK_MS : CACHE_TTL_FAIL_MS;
		if (cachedReachable != null && now - cachedAtMs < ttl) {
			return cachedReachable;
		}
		boolean ok = httpOpt.get().ping();
		cachedReachable = ok;
		cachedAtMs = now;
		return ok;
	}

	public static void invalidateCache() {
		cachedReachable = null;
	}
}