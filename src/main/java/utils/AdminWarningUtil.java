package utils;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

/**
 * Utility for triggering critical administrative warnings from background threads,
 * specifically related to sync engine errors and schema cache blockages.
 */
public class AdminWarningUtil {

    private static long lastWarningTime = 0;
    private static final long WARNING_COOLDOWN_MS = 60000; // Prevent spamming dialogs

    /**
     * Shows a schema cache mismatch warning. Applies a cooldown to prevent dialog spam
     * if multiple rows fail concurrently.
     * 
     * @param tableName    The table that failed to sync.
     * @param errorDetails Additional error context from Supabase.
     */
    public static void showSchemaCacheWarning(String tableName, String errorDetails) {
        long now = System.currentTimeMillis();
        if (now - lastWarningTime < WARNING_COOLDOWN_MS) {
            return; // Cooldown active, skip showing the dialog again
        }
        lastWarningTime = now;

        System.err.println("\n=======================================================");
        System.err.println("!!! SUPABASE SYNC BLOCKED : SCHEMA CACHE STALE !!!");
        System.err.println("Table: " + tableName);
        System.err.println("Diagnostic Details: " + errorDetails);
        System.err.println("-------------------------------------------------------");
        System.err.println("RECOVERY PROCEDURE:");
        System.err.println("1. Open your Supabase SQL Editor.");
        System.err.println("2. Execute: NOTIFY pgrst, 'reload schema';");
        System.err.println("3. The background sync will automatically recover.");
        System.err.println("=======================================================\n");
    }
}
