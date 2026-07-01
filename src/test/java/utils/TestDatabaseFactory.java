package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class TestDatabaseFactory {

    private static final String TEST_DB_DIR = "database_test";

    /**
     * Creates a new unique test database file and returns its JDBC URL.
     */
    public static synchronized String createFreshTestDatabase() {
        try {
            Path testDir = Paths.get(TEST_DB_DIR);
            Files.createDirectories(testDir);

            // Generate a unique file name to avoid test conflicts
            String dbFileName = "test_" + UUID.randomUUID().toString() + ".db";
            Path dbFilePath = testDir.resolve(dbFileName).toAbsolutePath();

            return "jdbc:sqlite:" + dbFilePath.toString() + "?busy_timeout=15000&journal_mode=WAL";
        } catch (IOException e) {
            throw new RuntimeException("Failed to create isolated test database directory", e);
        }
    }
}

