package service.sync;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;
import api.supabase.SupabaseRestClient;
import javafx.application.Platform;
import controller.MainController;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

public final class SyncCoordinator {
    private static final SyncCoordinator INSTANCE = new SyncCoordinator();
    private final Object syncLock = new Object();
    private final java.util.concurrent.atomic.AtomicInteger retryCount = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.ScheduledExecutorService retryScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sync-retry-scheduler");
        t.setDaemon(true);
        return t;
    });

    private SyncCoordinator() {
        // Initial pending count calculation
        updatePendingCount();
    }

    public static SyncCoordinator getInstance() {
        return INSTANCE;
    }

    public void updatePendingCount() {
        int count = PendingSyncCounter.getPendingRecordsCount();
        SyncStatusManager.getInstance().setPendingSyncCount(count);
    }

    public void syncNow() {
        syncNow(false);
    }

    public void syncNow(boolean isRetry) {
        SyncStatusManager status = SyncStatusManager.getInstance();
        
        // Prevent concurrent execution
        if (status.isSyncing()) {
            return;
        }

        // Run sync asynchronously to not block JavaFX thread
        CompletableFuture.runAsync(() -> {
            utils.SQLiteWriteCoordinator.runAsBackground(() -> {
                synchronized (syncLock) {
                    if (status.isSyncing()) {
                        return;
                    }
                    Platform.runLater(() -> status.setSyncing(true));
                    
                    try {
                        boolean reachable = SupabaseReachability.isReachable();
                        Platform.runLater(() -> status.setOnline(reachable));
                        
                        if (!reachable) {
                            throw new java.io.IOException("Supabase is unreachable (offline).");
                        }

                        var httpOpt = SupabaseGate.restClientIfConfigured();
                        if (httpOpt.isEmpty()) {
                            throw new IllegalStateException("Supabase client is not configured.");
                        }

                        SupabaseRestClient http = httpOpt.get();

                        // 1. Push Phase
                        SyncReport report = UniversalSyncEngine.syncAllPending();

                        // 2. Pull Phase
                        int pulledChanges = RemoteToLocalSync.pullAll(http);

                        // Sync finished successfully
                        Platform.runLater(() -> {
                            status.setLastSyncTime(LocalDateTime.now());
                            status.setLastError(null);
                        });

                        // Reset retry count on success
                        retryCount.set(0);

                        // 3. Refresh Screen
                        if (report.totalSynced() > 0 || pulledChanges > 0 || report.tempCodesPromoted > 0) {
                            Platform.runLater(() -> {
                                MainController mc = MainController.getInstance();
                                if (mc != null) {
                                    mc.refreshActiveScreen();
                                }
                            });
                        }

                    } catch (Exception e) {
                        System.err.println("[SyncCoordinator] Sync failed: " + e.getMessage());
                        e.printStackTrace();
                        Platform.runLater(() -> status.setLastError("Sync failed: " + e.getMessage()));
                        
                        // Schedule automatic retry with exponential backoff
                        scheduleRetry();
                    } finally {
                        updatePendingCount();
                        Platform.runLater(() -> status.setSyncing(false));
                    }
                }
            });
        });
    }

    private void scheduleRetry() {
        int attempt = retryCount.incrementAndGet();
        long delaySeconds = (long) Math.min(120, Math.pow(2, attempt) * 5); // 5s, 10s, 20s, 40s, 80s, 120s
        service.LoggerService.info("[SyncCoordinator] Scheduling auto-retry attempt #" + attempt + " in " + delaySeconds + "s", SyncCoordinator.class);
        retryScheduler.schedule(() -> syncNow(true), delaySeconds, java.util.concurrent.TimeUnit.SECONDS);
    }
}
