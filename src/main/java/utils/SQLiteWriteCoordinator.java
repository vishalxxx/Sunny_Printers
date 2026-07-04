package utils;

import service.LoggerService;
import service.sync.SyncStatusManager;

public final class SQLiteWriteCoordinator {

    private static final Object lock = new Object();
    private static Thread activeWriter = null;
    private static int activeWriterCount = 0; // Reentrancy
    private static int waitingUserWriters = 0;
    private static int activeUserWriters = 0;
    private static int waitingBackgroundWriters = 0;
    private static int activeBackgroundWriters = 0;
    private static long lockAcquiredAt = 0;

    private static final ThreadLocal<Boolean> isBackground = ThreadLocal.withInitial(() -> false);

    private SQLiteWriteCoordinator() {}

    public static void setBackground(boolean bg) {
        isBackground.set(bg);
    }

    public static boolean isBackground() {
        return isBackground.get();
    }

    public static void runAsBackground(Runnable r) {
        setBackground(true);
        try {
            r.run();
        } finally {
            setBackground(false);
        }
    }

    public static void beginWrite() {
        Thread current = Thread.currentThread();
        boolean bg = isBackground();
        long startTime = System.currentTimeMillis();

        synchronized (lock) {
            if (activeWriter == current) {
                activeWriterCount++;
                return;
            }

            if (!bg) {
                waitingUserWriters++;
            } else {
                waitingBackgroundWriters++;
                if (activeUserWriters > 0 || waitingUserWriters > 0) {
                    LoggerService.info("[SQLiteWriteCoordinator] Background write queued. Waiting for user transaction(s)...", SQLiteWriteCoordinator.class);
                    SyncStatusManager.getInstance().setSyncQueued(true);
                }
            }

            logQueueState("Before Acquire");

            while (activeWriter != null || (bg && (activeUserWriters > 0 || waitingUserWriters > 0))) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    if (!bg) {
                        waitingUserWriters--;
                    } else {
                        waitingBackgroundWriters--;
                    }
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted waiting for database write lock", e);
                }
            }

            if (!bg) {
                waitingUserWriters--;
                activeUserWriters++;
            } else {
                waitingBackgroundWriters--;
                activeBackgroundWriters++;
                SyncStatusManager.getInstance().setSyncQueued(false);
                LoggerService.info("[SQLiteWriteCoordinator] Background write resumed.", SQLiteWriteCoordinator.class);
            }

            activeWriter = current;
            activeWriterCount = 1;
            lockAcquiredAt = System.currentTimeMillis();

            long waitTime = System.currentTimeMillis() - startTime;
            if (waitTime > 10 || bg) {
                LoggerService.db("[SQLiteWriteCoordinator] Lock acquired by " + (bg ? "background" : "user") + " thread '" + current.getName() + "' in " + waitTime + "ms. Lock contention check: queue size=" + (waitingUserWriters + waitingBackgroundWriters));
            }
        }
    }

    public static void endWrite() {
        Thread current = Thread.currentThread();
        boolean bg = isBackground();

        synchronized (lock) {
            if (activeWriter != current) {
                throw new IllegalStateException("Current thread '" + current.getName() + "' does not hold the database write lock. Active writer: " 
                        + (activeWriter == null ? "None" : activeWriter.getName()));
            }

            activeWriterCount--;
            if (activeWriterCount == 0) {
                activeWriter = null;
                if (!bg) {
                    activeUserWriters--;
                } else {
                    activeBackgroundWriters--;
                }
                
                long duration = System.currentTimeMillis() - lockAcquiredAt;
                LoggerService.db("[SQLiteWriteCoordinator] Transaction complete on thread '" + current.getName() + "'. Lock held duration: " + duration + "ms");
                
                logQueueState("After Release");
                lock.notifyAll(); // Wake up other threads to check conditions
            }
        }
    }

    private static void logQueueState(String phase) {
        int totalQueue = waitingUserWriters + waitingBackgroundWriters;
        LoggerService.debug("[SQLiteWriteCoordinator] Queue State (" + phase + ") — Active: [User=" + activeUserWriters + ", BG=" + activeBackgroundWriters 
                + "], Waiting: [User=" + waitingUserWriters + ", BG=" + waitingBackgroundWriters + "], Total Queue Size: " + totalQueue, SQLiteWriteCoordinator.class);
    }
}
