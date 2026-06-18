package service.sync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import utils.DBConnection;
import utils.DatabaseInitializer;

public class TestDatabaseHelper {

    private static final String DIR = "database_test";

    public static void setupTestDir() throws Exception {
        Files.createDirectories(Path.of(DIR));
    }

    public static void cleanupTestDir() {
        try {
            Files.walk(Path.of(DIR))
                 .map(Path::toFile)
                 .forEach(f -> {
                     try {
                         f.delete();
                     } catch (Exception ignored) {}
                 });
            Files.deleteIfExists(Path.of(DIR));
        } catch (Exception ignored) {}
    }

    public static String createIsolatedDb(String name) throws Exception {
        setupTestDir();
        String dbPath = DIR + "/" + name + ".db";
        String jdbcUrl = "jdbc:sqlite:" + dbPath;
        
        // Temporarily swap DB url and run initializer
        String originalUrl = DBConnection.getUrl();
        DBConnection.setUrl(jdbcUrl);
        try {
            DatabaseInitializer.initialize();
        } finally {
            DBConnection.setUrl(originalUrl);
        }
        return jdbcUrl;
    }
}
