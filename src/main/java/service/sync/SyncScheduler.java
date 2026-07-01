package service.sync;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SyncScheduler {
    private static final SyncScheduler INSTANCE = new SyncScheduler();
    
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auto-sync-scheduler");
        t.setDaemon(true);
        return t;
    });
    
    private final AtomicBoolean started = new AtomicBoolean(false);

    private SyncScheduler() {}

    public static SyncScheduler getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        executor.scheduleAtFixedRate(() -> {
            try {
                SyncCoordinator.getInstance().syncNow();
            } catch (Exception e) {
                System.err.println("[SyncScheduler] Error in auto sync: " + e.getMessage());
            }
        }, 10, 120, TimeUnit.SECONDS); // Initial delay 10s, run every 120s (2 minutes)
    }
}
