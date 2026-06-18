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
        SyncStatusManager status = SyncStatusManager.getInstance();
        
        // Prevent concurrent execution
        if (status.isSyncing()) {
            return;
        }

        // Run sync asynchronously to not block JavaFX thread
        CompletableFuture.runAsync(() -> {
            synchronized (syncLock) {
                if (status.isSyncing()) {
                    return;
                }
                Platform.runLater(() -> status.setSyncing(true));
                
                try {
                    boolean reachable = SupabaseReachability.isReachable();
                    Platform.runLater(() -> status.setOnline(reachable));
                    
                    if (!reachable) {
                        Platform.runLater(() -> {
                            status.setLastError("Supabase is unreachable (offline).");
                            status.setSyncing(false);
                        });
                        updatePendingCount();
                        return;
                    }

                    var httpOpt = SupabaseGate.restClientIfConfigured();
                    if (httpOpt.isEmpty()) {
                        Platform.runLater(() -> {
                            status.setLastError("Supabase client is not configured.");
                            status.setSyncing(false);
                        });
                        updatePendingCount();
                        return;
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
                } finally {
                    updatePendingCount();
                    Platform.runLater(() -> status.setSyncing(false));
                }
            }
        });
    }
}
