package service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;
import utils.DBConnection;

public final class DatabaseDiagnostic {

    private static final String EXPECTED_DIR_NAME = ".sunnyprinters";

    public static void runStartupDiagnostics() {
        LoggerService.info("==== STARTING STARTUP DIAGNOSTICS ====", DatabaseDiagnostic.class);
        try {
            String url = DBConnection.getUrl();
            LoggerService.info("Database JDBC Connection URL: " + url, DatabaseDiagnostic.class);

            // Extract absolute path
            String dbPathStr = "";
            if (url != null && url.startsWith("jdbc:sqlite:")) {
                String cleanPath = url.substring("jdbc:sqlite:".length());
                int qIdx = cleanPath.indexOf('?');
                if (qIdx != -1) {
                    dbPathStr = cleanPath.substring(0, qIdx);
                } else {
                    dbPathStr = cleanPath;
                }
            }

            if (dbPathStr.isEmpty()) {
                LoggerService.warn("Could not parse database path from URL: " + url, DatabaseDiagnostic.class);
                return;
            }

            File dbFile = new File(dbPathStr);
            String absolutePath = dbFile.getAbsolutePath();
            LoggerService.info("Absolute Database Path: " + absolutePath, DatabaseDiagnostic.class);
            LoggerService.info("Database directory: " + dbFile.getParent(), DatabaseDiagnostic.class);

            // Check if directory matches expected ~/.sunnyprinters
            Path path = Paths.get(absolutePath);
            boolean isExpectedDir = false;
            for (Path element : path) {
                if (element.toString().equals(EXPECTED_DIR_NAME)) {
                    isExpectedDir = true;
                    break;
                }
            }

            if (!isExpectedDir) {
                LoggerService.warn("WARNING: Database is NOT located in the expected directory '" + EXPECTED_DIR_NAME + "'. Actual path: " + absolutePath, DatabaseDiagnostic.class);
            } else {
                LoggerService.info("Database is located in the expected directory: " + EXPECTED_DIR_NAME, DatabaseDiagnostic.class);
            }

            boolean exists = dbFile.exists();
            LoggerService.info("Database file exists: " + exists, DatabaseDiagnostic.class);

            if (exists) {
                LoggerService.info("Database file size: " + dbFile.length() + " bytes", DatabaseDiagnostic.class);
                LoggerService.info("Database last modified: " + new Date(dbFile.lastModified()), DatabaseDiagnostic.class);
            } else {
                LoggerService.warn("Database file does not exist yet. It will be initialized on first connection.", DatabaseDiagnostic.class);
            }

            // Verify connection, PRAGMAs, and row counts
            try (Connection conn = DBConnection.getConnection()) {
                LoggerService.info("Database connection opened successfully.", DatabaseDiagnostic.class);

                // Check user_version
                int userVersion = 0;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
                    if (rs.next()) {
                        userVersion = rs.getInt(1);
                    }
                }
                LoggerService.info("SQLite PRAGMA user_version: " + userVersion, DatabaseDiagnostic.class);

                // Check journal_mode
                String journalMode = "";
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                    if (rs.next()) {
                        journalMode = rs.getString(1);
                    }
                }
                LoggerService.info("SQLite journal_mode: " + journalMode, DatabaseDiagnostic.class);

                // Check writability
                boolean writable = false;
                try {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("CREATE TABLE IF NOT EXISTS _diag_write_test (id INTEGER PRIMARY KEY)");
                        stmt.execute("INSERT INTO _diag_write_test DEFAULT VALUES");
                        stmt.execute("DROP TABLE _diag_write_test");
                        writable = true;
                    }
                } catch (Exception ex) {
                    LoggerService.error("Database writability check FAILED!", DatabaseDiagnostic.class, ex);
                }
                LoggerService.info("Database is writable: " + writable, DatabaseDiagnostic.class);

                // Check if company_details exists and print counts
                logTableCount(conn, "users");
                logTableCount(conn, "company_details");
                logTableCount(conn, "clients");
                logTableCount(conn, "suppliers");
                logTableCount(conn, "jobs");
                logTableCount(conn, "invoice_master");

            } catch (Exception ex) {
                LoggerService.error("Failed to connect to SQLite database or run SQL diagnostics: " + ex.getMessage(), DatabaseDiagnostic.class, ex);
            }

        } catch (Exception e) {
            LoggerService.error("Error running startup database diagnostics: " + e.getMessage(), DatabaseDiagnostic.class, e);
        }
        LoggerService.info("==== STARTUP DIAGNOSTICS COMPLETED ====", DatabaseDiagnostic.class);
    }

    private static void logTableCount(Connection conn, String tableName) {
        try (Statement stmt = conn.createStatement()) {
            // Check if table exists
            boolean tableExists = false;
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'")) {
                if (rs.next()) {
                    tableExists = true;
                }
            }

            if (!tableExists) {
                LoggerService.warn("Table '" + tableName + "' does NOT exist in schema.", DatabaseDiagnostic.class);
                return;
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    LoggerService.info("Table '" + tableName + "' row count: " + count, DatabaseDiagnostic.class);
                }
            }
        } catch (Exception e) {
            LoggerService.warn("Could not query table count for '" + tableName + "': " + e.getMessage(), DatabaseDiagnostic.class);
        }
    }
}
