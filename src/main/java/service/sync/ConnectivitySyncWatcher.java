package service.sync;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;

/**
 * Periodically detects when Supabase becomes reachable and runs UniversalSyncEngine.
 */
public final class ConnectivitySyncWatcher {

	private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "supabase-connectivity-sync");
		t.setDaemon(true);
		return t;
	});

	private static final AtomicBoolean STARTED = new AtomicBoolean(false);
	private static volatile boolean wasReachable;
	private static volatile long lastSyncTriggerMs;
	private static volatile long lastSequenceRefreshMs;

	private static final long POLL_INTERVAL_SEC = 20;
	private static final long MIN_SYNC_GAP_MS = 12_000;
	private static final long PENDING_RETRY_MS = 45_000;
	private static final long SEQUENCE_REFRESH_INTERVAL_MS = 15 * 60 * 1000; // 15 minutes

	private ConnectivitySyncWatcher() {
	}

	public static void start() {
		if (!STARTED.compareAndSet(false, true)) {
			return;
		}
		lastSequenceRefreshMs = System.currentTimeMillis();
		EXECUTOR.scheduleWithFixedDelay(ConnectivitySyncWatcher::tick, 8, POLL_INTERVAL_SEC, TimeUnit.SECONDS);
	}

	private static void tick() {
		if (SupabaseGate.restClientIfConfigured().isEmpty()) {
			wasReachable = false;
			return;
		}
		boolean reachable = SupabaseReachability.isReachable();
		SyncStatusManager.getInstance().setOnline(reachable);
		long now = System.currentTimeMillis();

		// Periodic background refresh for sequences (e.g. every 15 minutes)
		if (reachable && now - lastSequenceRefreshMs >= SEQUENCE_REFRESH_INTERVAL_MS) {
			lastSequenceRefreshMs = now;
			api.supabase.sequences.NumberSequenceSupabaseSync.syncRemoteToLocalAsync();
		}

		boolean trigger = false;
		if (reachable && !wasReachable) {
			System.out.println("[ConnectivitySyncWatcher] Supabase reachable — scheduling sync");
			trigger = true;
		} else if (reachable && UniversalSyncEngine.hasPendingWork()
				&& now - lastSyncTriggerMs >= PENDING_RETRY_MS) {
			trigger = true;
		}
		wasReachable = reachable;
		if (trigger && reachable && now - lastSyncTriggerMs >= MIN_SYNC_GAP_MS) {
			lastSyncTriggerMs = now;
			SyncCoordinator.getInstance().syncNow();
		}
	}
}