package utils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper that creates isolated SQLite databases for tests that need to
 * initialise schema state before injecting the URL via the extension.
 */
public class TestDatabaseHelper {

    private static final String DIR = "target/database_test";

    public static void setupTestDir() throws Exception {
        Files.createDirectories(Path.of(DIR));
    }

    public static void cleanupTestDir() {
        try {
            api.supabase.SupabaseGate.setOverrideClient(null);
            if (Files.exists(Path.of(DIR))) {
                Files.walk(Path.of(DIR))
                     .map(Path::toFile)
                     .forEach(f -> {
                         try { f.delete(); } catch (Exception ignored) {}
                     });
                Files.deleteIfExists(Path.of(DIR));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Creates an isolated SQLite database with a fully initialised schema and
     * returns its JDBC URL.
     *
     * <p>The URL is injected as a thread-local override for the duration of the
     * schema init, then removed.  Callers are responsible for injecting the
     * returned URL into their test thread via
     * {@link DBConnection#setTestDatabaseUrl(String)}.
     */
    public static String createIsolatedDb(String name) throws Exception {
        setupTestDir();
        Path subDir = Path.of(DIR, name + "_" + System.nanoTime());
        Files.createDirectories(subDir);
        Path dbFilePath = subDir.resolve(name + ".db");
        String jdbcUrl = "jdbc:sqlite:" + dbFilePath.toAbsolutePath().toString()
                + "?busy_timeout=15000&journal_mode=WAL";

        // Temporarily set the thread-local override so DatabaseInitializer
        // writes the schema into the new isolated file.
        DBConnection.setTestDatabaseUrl(jdbcUrl);
        try {
            DatabaseInitializer.initialize();
            DBConnection.registerInitializedUrl(jdbcUrl);
        } finally {
            // Always clear the override — the caller will re-set it if needed.
            DBConnection.clearTestDatabaseUrl();
        }
        return jdbcUrl;
    }
}
