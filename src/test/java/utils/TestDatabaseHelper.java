package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import utils.DBConnection;
import utils.DatabaseInitializer;

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
                         try {
                             f.delete();
                         } catch (Exception ignored) {}
                     });
                Files.deleteIfExists(Path.of(DIR));
            }
        } catch (Exception ignored) {}
    }

    public static String createIsolatedDb(String name) throws Exception {
        setupTestDir();
        Path subDir = Path.of(DIR, name + "_" + System.nanoTime());
        Files.createDirectories(subDir);
        Path dbFilePath = subDir.resolve(name + ".db");
        String jdbcUrl = "jdbc:sqlite:" + dbFilePath.toAbsolutePath().toString() + "?busy_timeout=15000&journal_mode=WAL";
        
        // Temporarily swap DB url and run initializer
        String originalUrl = DBConnection.getUrl();
        DBConnection.setUrl(jdbcUrl);
        try {
            DatabaseInitializer.initialize();
            DBConnection.registerInitializedUrl(jdbcUrl);
        } finally {
            DBConnection.setUrl(originalUrl);
        }
        return jdbcUrl;
    }
}

