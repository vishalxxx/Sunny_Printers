package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Central database connection provider for the Sunny Printers application.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>{@code PRODUCTION_URL} is computed once at class-load time and is
 *       <strong>immutable</strong>.  No production code can change it.</li>
 *   <li>Test code may inject a per-thread override via
 *       {@link #setTestDatabaseUrl(String)} / {@link #clearTestDatabaseUrl()}.
 *       This uses a {@link ThreadLocal} so concurrent test threads never
 *       interfere with each other or with the production path.</li>
 *   <li>{@link #getUrl()} returns the thread-local override if one is active,
 *       otherwise it returns {@code PRODUCTION_URL}.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <ul>
 *   <li>{@code PRODUCTION_URL} — final, safe to read from any thread.</li>
 *   <li>{@code threadLocalUrl} — inherently per-thread; no synchronisation
 *       needed for reads or writes.</li>
 *   <li>{@code initializedUrls} — wrapped in {@code synchronizedSet}; inner
 *       double-checked lock in {@link #lazyInit()} guards schema migration.</li>
 * </ul>
 */
public class DBConnection {

    // ── Production URL (immutable) ────────────────────────────────────────────

    /**
     * The absolute, canonical path to the production SQLite database.
     * Resolved at class-load time from the OS user-home directory.
     * This value is {@code final} and can never be changed at runtime.
     */
    public static final String PRODUCTION_URL =
            "jdbc:sqlite:"
            + java.nio.file.Path.of(System.getProperty("user.home"), ".sunnyprinters", "database.db")
                                 .toAbsolutePath()
                                 .toString()
            + "?busy_timeout=15000&journal_mode=WAL";

    // ── Test-only per-thread override ─────────────────────────────────────────

    /**
     * Holds an isolated database URL for the current test thread.
     * {@code null} means "use the production URL".
     */
    private static final ThreadLocal<String> threadLocalUrl = new ThreadLocal<>();
    private static volatile String globalTestUrl = null;

    /**
     * Injects a test-specific database URL for the <em>calling thread only</em>.
     * Has zero effect on any other thread, including the JavaFX application thread.
     *
     * <p><strong>Only call this from test code.</strong>
     * Call {@link #clearTestDatabaseUrl()} in {@code @AfterEach} to prevent leaks.
     *
     * @param url a valid {@code jdbc:sqlite:} URL pointing to a temporary database
     * @throws IllegalArgumentException if the URL is null or does not start with {@code jdbc:sqlite:}
     */
    public static void setTestDatabaseUrl(String url) {
        if (url == null || !url.startsWith("jdbc:sqlite:")) {
            throw new IllegalArgumentException("Test database URL must be a non-null jdbc:sqlite: URL. Got: " + url);
        }
        threadLocalUrl.set(url);
        service.LoggerService.debug("[DBConnection] Thread '" + Thread.currentThread().getName()
                + "' — test database URL set: " + url, DBConnection.class);
    }

    /**
     * Removes the thread-local test override so that subsequent calls to
     * {@link #getUrl()} return the production URL again.
     * Safe to call even if no override was set.
     */
    public static void clearTestDatabaseUrl() {
        String removed = threadLocalUrl.get();
        threadLocalUrl.remove();
        if (removed != null) {
            service.LoggerService.debug("[DBConnection] Thread '" + Thread.currentThread().getName()
                    + "' — test database URL cleared.", DBConnection.class);
        }
    }

    /**
     * Injects a test-specific database URL globally for all threads.
     * Use this when background threads need to share the same test database URL.
     */
    public static void setGlobalTestDatabaseUrl(String url) {
        globalTestUrl = url;
    }

    /**
     * Clears the global test-specific database URL override.
     */
    public static void clearGlobalTestDatabaseUrl() {
        globalTestUrl = null;
    }

    // ── URL resolution ────────────────────────────────────────────────────────

    /**
     * Returns the effective JDBC URL for the current thread.
     * Returns the thread-local test override if one is active; otherwise
     * returns the global test override if set; otherwise returns {@link #PRODUCTION_URL}.
     */
    public static String getUrl() {
        String override = threadLocalUrl.get();
        if (override != null) {
            return override;
        }
        if (globalTestUrl != null) {
            return globalTestUrl;
        }
        return PRODUCTION_URL;
    }

    // ── Schema initialization tracking ────────────────────────────────────────

    private static final Set<String> initializedUrls =
            Collections.synchronizedSet(new HashSet<>());

    public static void registerInitializedUrl(String initializedUrl) {
        if (initializedUrl != null) {
            initializedUrls.add(initializedUrl);
        }
    }

    private static void lazyInit() {
        String currentUrl = getUrl();
        if (!initializedUrls.contains(currentUrl)) {
            synchronized (initializedUrls) {
                if (!initializedUrls.contains(currentUrl)) {
                    try {
                        ensureDatabaseParentDirectory();
                        DatabaseInitializer.initialize();
                        LocalSchemaVerifier.verifySchema();
                        initializedUrls.add(currentUrl);
                    } catch (Exception e) {
                        service.LoggerService.error(
                                "Failed to initialize database: " + e.getMessage(),
                                DBConnection.class, e);
                        throw new RuntimeException("Failed to initialize database: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    // ── Directory setup ───────────────────────────────────────────────────────

    /**
     * Ensures the production workspace directories exist under
     * {@code ~/.sunnyprinters/} and that the parent directory of the
     * currently-active database file (which may be a test db) also exists.
     */
    public static void ensureDatabaseParentDirectory() throws Exception {
        // Always create production workspace
        java.nio.file.Path baseDir =
                java.nio.file.Path.of(System.getProperty("user.home"), ".sunnyprinters");
        java.nio.file.Files.createDirectories(baseDir);
        java.nio.file.Files.createDirectories(baseDir.resolve("logs"));
        java.nio.file.Files.createDirectories(baseDir.resolve("backups"));
        java.nio.file.Files.createDirectories(baseDir.resolve("exports"));
        java.nio.file.Files.createDirectories(baseDir.resolve("imports"));
        java.nio.file.Files.createDirectories(baseDir.resolve("updates"));
        java.nio.file.Files.createDirectories(baseDir.resolve("downloads"));
        java.nio.file.Files.createDirectories(baseDir.resolve("cache"));

        // Also ensure the parent dir of the currently-active DB file
        // (needed for test databases placed in temp directories)
        String activeUrl = getUrl();
        if (activeUrl != null && activeUrl.startsWith("jdbc:sqlite:")) {
            String cleanPath = activeUrl.substring("jdbc:sqlite:".length());
            int qIdx = cleanPath.indexOf('?');
            if (qIdx != -1) cleanPath = cleanPath.substring(0, qIdx);
            java.nio.file.Path dbFilePath = java.nio.file.Path.of(cleanPath);
            java.nio.file.Path parentDir = dbFilePath.getParent();
            if (parentDir != null) {
                java.nio.file.Files.createDirectories(parentDir);
            }
        }
    }

    // ── Connection factory ────────────────────────────────────────────────────

    public static Connection getConnection() throws Exception {
        lazyInit();
        org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
        config.setJournalMode(org.sqlite.SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(org.sqlite.SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(10000);
        config.enforceForeignKeys(true);
        String activeUrl = getUrl();
        service.LoggerService.db("[DB-OPEN] Opening connection to: " + activeUrl);
        try {
            Connection conn = DriverManager.getConnection(activeUrl, config.toProperties());
            return WriteCoordinatedConnection.wrap(conn, false);
        } catch (java.sql.SQLException e) {
            if (e.getErrorCode() == 5 || (e.getMessage() != null && e.getMessage().contains("busy"))) {
                service.LoggerService.dbWarn("[DB-BUSY] SQLite database is busy/locked. URL: " + activeUrl + ". Error: " + e.getMessage());
            }
            throw e;
        }
    }

    public static Connection getExclusiveConnection() throws Exception {
        lazyInit();
        org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
        config.setTransactionMode(org.sqlite.SQLiteConfig.TransactionMode.IMMEDIATE);
        config.setJournalMode(org.sqlite.SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(org.sqlite.SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(15000);
        config.enforceForeignKeys(true);
        String activeUrl = getUrl();
        service.LoggerService.db("[DB-OPEN] Opening exclusive connection to: " + activeUrl);
        try {
            Connection conn = DriverManager.getConnection(activeUrl, config.toProperties());
            return WriteCoordinatedConnection.wrap(conn, true);
        } catch (java.sql.SQLException e) {
            if (e.getErrorCode() == 5 || (e.getMessage() != null && e.getMessage().contains("busy"))) {
                service.LoggerService.dbWarn("[DB-BUSY] SQLite database is busy/locked (Exclusive). URL: " + activeUrl + ". Error: " + e.getMessage());
            }
            throw e;
        }
    }

    private DBConnection() {}
}
