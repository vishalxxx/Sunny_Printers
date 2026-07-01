package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class TestDatabaseManager {

    /**
     * Closes connections and deletes the SQLite database files (including WAL/SHM sidecars) for the given JDBC URL.
     */
    public static synchronized void cleanupDatabase(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlite:")) {
            return;
        }

        // Close any potentially lingering DB locks by executing a checkpoint/close
        try {
            // Force connection pool or driver to release the file
            java.sql.DriverManager.getConnection(jdbcUrl).close();
        } catch (Exception ignored) {}

        // Extract absolute path of the database file
        String cleanPath = jdbcUrl.substring("jdbc:sqlite:".length());
        int qIdx = cleanPath.indexOf('?');
        if (qIdx != -1) {
            cleanPath = cleanPath.substring(0, qIdx);
        }

        Path dbPath = Paths.get(cleanPath).toAbsolutePath();
        Path walPath = Paths.get(cleanPath + "-wal");
        Path shmPath = Paths.get(cleanPath + "-shm");

        // Try deleting the database file and its sidecars
        try {
            // Sleep briefly to let file descriptors release
            Thread.sleep(50);
            
            // Delete main database file
            if (Files.exists(dbPath)) {
                Files.deleteIfExists(dbPath);
            }
            // Delete WAL journal file
            if (Files.exists(walPath)) {
                Files.deleteIfExists(walPath);
            }
            // Delete shared memory file
            if (Files.exists(shmPath)) {
                Files.deleteIfExists(shmPath);
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to delete test database file: " + dbPath + " - " + e.getMessage());
        }
    }
}

