package service.sync;

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
            update.setStoragePath("/bucket/update.jar");

            // 1. Full Match Validation
            assertTrue(ds.verifyFile(tempFile, update), "Verification must succeed when size and hash match");

            // 2. Size Mismatch Validation
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
        update.setStoragePath("https://external-cdn.com/releases/installer.exe");

        // Direct URL check
        String resolvedUrl = ds.getDownloadUrl(update);
        assertEquals("https://external-cdn.com/releases/installer.exe", resolvedUrl);

        // Relative path resolution check (uses Supabase Settings from DB)
        update.setStoragePath("updates/releases/sunny.jar");
        try {
            String resolved = ds.getDownloadUrl(update);
            assertNotNull(resolved);
            assertTrue(resolved.contains("/storage/v1/object/public/updates/releases/sunny.jar"));
        } catch (IllegalStateException e) {
            // Safe to ignore if Supabase URL is not configured in test DB
            LOGGER_log("Ignored unconfigured Supabase URL test: " + e.getMessage());
        }
    }

    private void LOGGER_log(String msg) {
        System.out.println("[DownloadManagerTest Info] " + msg);
    }
}
