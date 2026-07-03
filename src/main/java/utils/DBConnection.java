package utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DBConnection {

    private static final String DEFAULT_DB_PATH = java.nio.file.Path.of(System.getProperty("user.home"), ".sunnyprinters", "database.db").toAbsolutePath().toString();
    private static String url = "jdbc:sqlite:" + DEFAULT_DB_PATH + "?busy_timeout=15000&journal_mode=WAL";

    public static void setUrl(String newUrl) {
        url = newUrl;
        service.LoggerService.info("Database URL updated to: " + newUrl, DBConnection.class);
    }

    public static String getUrl() {
        return url;
    }

    private static final java.util.Set<String> initializedUrls = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public static void registerInitializedUrl(String initializedUrl) {
        if (initializedUrl != null) {
            initializedUrls.add(initializedUrl);
        }
    }

    private static void lazyInit() {
        String currentUrl = url;
        if (currentUrl != null && !initializedUrls.contains(currentUrl)) {
            synchronized (initializedUrls) {
                if (!initializedUrls.contains(currentUrl)) {
                    try {
                        ensureDatabaseParentDirectory();
                        DatabaseInitializer.initialize();
                        LocalSchemaVerifier.verifySchema();
                        initializedUrls.add(currentUrl);
                    } catch (Exception e) {
                        service.LoggerService.error("Failed to initialize database: " + e.getMessage(), DBConnection.class, e);
                        throw new RuntimeException("Failed to initialize database: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    static {
        // Eager database initialization is disabled on class load to prevent lock contention in concurrent test suites.
    }

	/**
	 * Setup production database and auxiliary workspace folders under User Home,
	 * as well as dynamically ensuring custom/test database parent directories exist.
	 */
	public static void ensureDatabaseParentDirectory() throws Exception {
		java.nio.file.Path baseDir = java.nio.file.Path.of(System.getProperty("user.home"), ".sunnyprinters");
		java.nio.file.Files.createDirectories(baseDir);
		java.nio.file.Files.createDirectories(baseDir.resolve("logs"));
		java.nio.file.Files.createDirectories(baseDir.resolve("backups"));
		java.nio.file.Files.createDirectories(baseDir.resolve("exports"));
		java.nio.file.Files.createDirectories(baseDir.resolve("imports"));
		java.nio.file.Files.createDirectories(baseDir.resolve("updates"));
		java.nio.file.Files.createDirectories(baseDir.resolve("downloads"));
		java.nio.file.Files.createDirectories(baseDir.resolve("cache"));

		if (url != null && url.startsWith("jdbc:sqlite:")) {
			String cleanPath = url.substring("jdbc:sqlite:".length());
			int qIdx = cleanPath.indexOf('?');
			if (qIdx != -1) {
				cleanPath = cleanPath.substring(0, qIdx);
			}
			java.nio.file.Path dbFilePath = java.nio.file.Path.of(cleanPath);
			java.nio.file.Path parentDir = dbFilePath.getParent();
			if (parentDir != null) {
				java.nio.file.Files.createDirectories(parentDir);
			}
		}
	}

    public static Connection getConnection() throws Exception {
        lazyInit();
        org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
        config.setJournalMode(org.sqlite.SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(org.sqlite.SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(10000); // 10 seconds default timeout
        config.enforceForeignKeys(true);
        
        service.LoggerService.debug("Opening standard SQLite connection to: " + url, DBConnection.class);
        return DriverManager.getConnection(url, config.toProperties());
    }

    public static Connection getExclusiveConnection() throws Exception {
        lazyInit();
        
        org.sqlite.SQLiteConfig config = new org.sqlite.SQLiteConfig();
        config.setTransactionMode(org.sqlite.SQLiteConfig.TransactionMode.IMMEDIATE);
        config.setJournalMode(org.sqlite.SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(org.sqlite.SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(15000); // 15 seconds timeout for heavy writes
        config.enforceForeignKeys(true);
        
        service.LoggerService.debug("Opening exclusive SQLite connection to: " + url, DBConnection.class);
        return DriverManager.getConnection(url, config.toProperties());
    }
}

