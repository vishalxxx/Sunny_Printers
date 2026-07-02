package sanity;


import org.junit.jupiter.api.Tag;
import utils.FakeSupabaseRestClient;
import utils.TestDatabaseHelper;
import utils.CleanupUtility;
import utils.ClearAllExceptSettings;
import utils.ClearRemoteDatabase;
import utils.MetricsExtractor;
import utils.SchemaAndSyncChecker;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import model.AppUpdate;
import service.DownloadService;
import utils.DBConnection;

@Tag("sanity")
public class DownloadManagerTest {

    private static String dbUrl;

    @BeforeAll
    public static void setup() throws Exception {
        dbUrl = TestDatabaseHelper.createIsolatedDb("DownloadManagerTest");
        DBConnection.setUrl(dbUrl);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @Test
    public void testDownloadDirectoryResolution() {
        DownloadService ds = new DownloadService();
        Path dir = ds.getDownloadDir();
        assertNotNull(dir);
        assertTrue(Files.exists(dir));
    }

    @Test
    public void testSHA256Calculation() throws IOException, java.security.NoSuchAlgorithmException {
        DownloadService ds = new DownloadService();
        Path tempFile = Files.createTempFile("test-sha-", ".txt");
        try {
            String testContent = "Sunny Printers Production Update Content Payload";
            Files.write(tempFile, testContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Calculated checksum
            String calculated = ds.calculateSHA256(tempFile);
            assertNotNull(calculated);
            assertEquals(64, calculated.length(), "SHA-256 string must be 64 characters long");

            // Verify programmatically computed hash matches
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] expectedBytes = md.digest(testContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : expectedBytes) {
                sb.append(String.format("%02x", b));
            }
            assertEquals(sb.toString(), calculated.toLowerCase());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testFileVerificationIntegrity() throws IOException {
        DownloadService ds = new DownloadService();
        Path tempFile = Files.createTempFile("test-verify-", ".jar");
        try {
            String content = "Hello World Update Payload";
            Files.writeString(tempFile, content);
            long size = Files.size(tempFile);
            String hash = ds.calculateSHA256(tempFile);

            AppUpdate update = new AppUpdate();
            update.setFileName(tempFile.getFileName().toString());
            update.setFileSize(size);
            update.setSha256(hash);
            update.setPublished(true);
            update.setStoragePath("https://github.com/vishalxxx/Sunny_Printers/releases/download/v1.0.3/update.jar");

            // 1. Full Match Validation
            assertTrue(ds.verifyFile(tempFile, update), "Verification must succeed when size, hash and GitHub URL match");

            // 2. Invalid GitHub URL Validation
            update.setStoragePath("https://malicious-site.com/releases/update.jar");
            assertFalse(ds.verifyFile(tempFile, update), "Verification must fail on invalid GitHub Release URL");
            assertFalse(Files.exists(tempFile), "Corrupt file must be cleaned up on verification failure");

            // Recreate file for size mismatch test
            Files.writeString(tempFile, content);
            update.setStoragePath("https://github.com/vishalxxx/Sunny_Printers/releases/download/v1.0.3/update.jar");
            update.setFileSize(size + 10);
            assertFalse(ds.verifyFile(tempFile, update), "Verification must fail on size mismatch");
            assertFalse(Files.exists(tempFile), "Corrupt file must be cleaned up on verification failure");

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    public void testDownloadUrlResolving() throws Exception {
        DownloadService ds = new DownloadService();

        AppUpdate update = new AppUpdate();
        update.setStoragePath("https://github.com/vishalxxx/Sunny_Printers/releases/download/v1.0.3/SunnyPrintersERP-1.0.3.msi");

        // Direct URL check
        String resolvedUrl = ds.getDownloadUrl(update);
        assertEquals("https://github.com/vishalxxx/Sunny_Printers/releases/download/v1.0.3/SunnyPrintersERP-1.0.3.msi", resolvedUrl);

        // Invalid path throws exception
        update.setStoragePath("updates/releases/sunny.jar");
        assertThrows(IllegalArgumentException.class, () -> {
            ds.getDownloadUrl(update);
        });
    }

    private void LOGGER_log(String msg) {
        System.out.println("[DownloadManagerTest Info] " + msg);
    }
}

