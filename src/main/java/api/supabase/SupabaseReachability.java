package api.supabase;

import java.util.Optional;

/**
 * Probes whether Supabase PostgREST is reachable (not just configured in settings).
 */
public final class SupabaseReachability {

	private static final ThreadLocal<Boolean> cachedReachable = new ThreadLocal<>();
	private static final ThreadLocal<Long> cachedAtMs = ThreadLocal.withInitial(() -> 0L);
	private static final long CACHE_TTL_OK_MS = 8_000;
	/** When offline, avoid repeated TCP/DNS timeouts on every UI action. */
	private static final long CACHE_TTL_FAIL_MS = 45_000;

	private SupabaseReachability() {
	}

	public static boolean isReachable() {
		Optional<SupabaseRestClient> httpOpt = SupabaseGate.restClientIfConfigured();
		if (httpOpt.isEmpty()) {
			cachedReachable.set(false);
			return false;
		}
		if (SupabaseGate.isOverrideActive()) {
			return httpOpt.get().ping();
		}
		long now = System.currentTimeMillis();
		Boolean cached = cachedReachable.get();
		long lastCached = cachedAtMs.get();
		long ttl = Boolean.TRUE.equals(cached) ? CACHE_TTL_OK_MS : CACHE_TTL_FAIL_MS;
		if (cached != null && now - lastCached < ttl) {
			return cached;
		}
		boolean ok = httpOpt.get().ping();
		cachedReachable.set(ok);
		cachedAtMs.set(now);
		return ok;
	}

	public static void invalidateCache() {
		cachedReachable.remove();
		cachedAtMs.remove();
	}
}