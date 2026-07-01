package service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

import model.AppUpdate;
import repository.SupabaseSettingsRepository;

public class DownloadService {

    private static final Logger LOGGER = Logger.getLogger(DownloadService.class.getName());

    private final Path downloadDir;

    public DownloadService() {
        this.downloadDir = Paths.get(System.getProperty("user.home"), ".sunny_printers", "updates", "downloads");
        try {
            Files.createDirectories(downloadDir);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "[DownloadService] Failed to create download directories: " + e.getMessage(), e);
        }
    }

    public Path getDownloadDir() {
        return downloadDir;
    }

    /**
     * Resolves the local target path for the update file.
     */
    public Path getTargetFilePath(AppUpdate update) {
        String fileName = update.getFileName();
        if (fileName == null || fileName.isBlank()) {
            fileName = "update-" + update.getVersion() + ".jar";
        }
        return downloadDir.resolve(fileName);
    }

    /**
     * Resolves the remote download URL.
     */
    public String getDownloadUrl(AppUpdate update) throws Exception {
        String storagePath = update.getStoragePath();
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("storage_path in update record is empty");
        }
        if (storagePath.startsWith("http://") || storagePath.startsWith("https://")) {
            return storagePath;
        }

        SupabaseSettingsRepository repo = new SupabaseSettingsRepository();
        model.SupabaseSettings settings = repo.load();
        String supabaseUrl = settings.getSupabaseUrl();
        if (supabaseUrl == null || supabaseUrl.isBlank()) {
            throw new IllegalStateException("Supabase URL is not configured locally");
        }

        // Standard public storage URL build
        String baseUrl = supabaseUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String cleanPath = storagePath.trim();
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }
        return baseUrl + "/storage/v1/object/public/" + cleanPath;
    }

    /**
     * Calculates the SHA-256 checksum of a file.
     */
    public String calculateSHA256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(is, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // Just read the stream to compute hash
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies that the file matches the update's expected size and SHA-256 checksum.
     */
    public boolean verifyFile(Path file, AppUpdate update) {
        if (!Files.exists(file)) {
            LOGGER.warning("[DownloadService] Verification failed: File does not exist at " + file);
            return false;
        }

        try {
            long actualSize = Files.size(file);
            long expectedSize = update.getFileSize();
            if (actualSize != expectedSize) {
                LOGGER.warning("[DownloadService] Size mismatch: Expected " + expectedSize + " bytes, but found " + actualSize);
                cleanupCorruptFile(file);
                return false;
            }

            String expectedSha = update.getSha256();
            if (expectedSha == null || expectedSha.isBlank()) {
                LOGGER.warning("[DownloadService] Verification failed: Expected SHA-256 is empty in database record");
                cleanupCorruptFile(file);
                return false;
            }

            String actualSha = calculateSHA256(file);
            if (!expectedSha.trim().equalsIgnoreCase(actualSha.trim())) {
                LOGGER.warning("[DownloadService] SHA-256 checksum mismatch: Expected " + expectedSha + ", but got " + actualSha);
                cleanupCorruptFile(file);
                return false;
            }

            LOGGER.info("[DownloadService] Verification SUCCESS: File size and SHA-256 match perfectly.");
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[DownloadService] File verification failed with exception: " + e.getMessage(), e);
            cleanupCorruptFile(file);
            return false;
        }
    }

    /**
     * Deletes corrupt or failed download files.
     */
    public void cleanupCorruptFile(Path file) {
        if (Files.exists(file)) {
            try {
                Files.delete(file);
                LOGGER.info("[DownloadService] Cleaned up invalid/corrupt file: " + file);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "[DownloadService] Failed to delete invalid file " + file + ": " + e.getMessage(), e);
            }
        }
    }
}
