package service.sync;

import javafx.application.Platform;
import javafx.beans.property.*;
import java.time.LocalDateTime;

public final class SyncStatusManager {
    private static final SyncStatusManager INSTANCE = new SyncStatusManager();

    private final BooleanProperty online = new SimpleBooleanProperty(true);
    private final BooleanProperty syncing = new SimpleBooleanProperty(false);
    private final ObjectProperty<LocalDateTime> lastSyncTime = new SimpleObjectProperty<>(null);
    private final IntegerProperty pendingSyncCount = new SimpleIntegerProperty(0);
    private final StringProperty lastError = new SimpleStringProperty(null);

    private SyncStatusManager() {}

    public static SyncStatusManager getInstance() {
        return INSTANCE;
    }

    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    public BooleanProperty onlineProperty() { return online; }
    public boolean isOnline() { return online.get(); }
    public void setOnline(boolean value) { runOnFxThread(() -> this.online.set(value)); }

    public BooleanProperty syncingProperty() { return syncing; }
    public boolean isSyncing() { return syncing.get(); }
    public void setSyncing(boolean value) { runOnFxThread(() -> this.syncing.set(value)); }

    public ObjectProperty<LocalDateTime> lastSyncTimeProperty() { return lastSyncTime; }
    public LocalDateTime getLastSyncTime() { return lastSyncTime.get(); }
    public void setLastSyncTime(LocalDateTime value) { runOnFxThread(() -> this.lastSyncTime.set(value)); }

    public IntegerProperty pendingSyncCountProperty() { return pendingSyncCount; }
    public int getPendingSyncCount() { return pendingSyncCount.get(); }
    public void setPendingSyncCount(int value) { runOnFxThread(() -> this.pendingSyncCount.set(value)); }

    public StringProperty lastErrorProperty() { return lastError; }
    public String getLastError() { return lastError.get(); }
    public void setLastError(String value) { runOnFxThread(() -> this.lastError.set(value)); }
}
