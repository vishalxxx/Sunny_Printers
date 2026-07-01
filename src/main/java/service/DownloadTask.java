package service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.concurrent.Task;
import model.AppUpdate;

public class DownloadTask extends Task<Void> {

    private static final Logger LOGGER = Logger.getLogger(DownloadTask.class.getName());
    private static final int BUFFER_SIZE = 8192;

    private final AppUpdate update;
    private final Path targetFile;
    private final String downloadUrl;
    private final HttpClient client;

    public DownloadTask(AppUpdate update, Path targetFile, String downloadUrl) {
        this.update = update;
        this.targetFile = targetFile;
        this.downloadUrl = downloadUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    @Override
    protected Void call() throws Exception {
        updateTitle("Downloading update...");
        updateMessage("Connecting...");

        long totalExpectedSize = update.getFileSize();
        long existingSize = 0;
        if (Files.exists(targetFile)) {
            existingSize = Files.size(targetFile);
            if (existingSize == totalExpectedSize) {
                // Already fully downloaded, skip to verification
                updateProgress(totalExpectedSize, totalExpectedSize);
                updateMessage("Download complete (cached). Verifying...");
                return null;
            }
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(URI.create(downloadUrl))
                .timeout(Duration.ofSeconds(10))
                .GET();

        boolean resuming = false;
        if (existingSize > 0 && existingSize < totalExpectedSize) {
            reqBuilder.header("Range", "bytes=" + existingSize + "-");
            resuming = true;
            LOGGER.info("[DownloadTask] Attempting resume from byte: " + existingSize);
        }

        HttpResponse<InputStream> response;
        try {
            response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            throw new IOException("Failed to connect to download server: " + e.getMessage(), e);
        }

        int statusCode = response.statusCode();
        long startPosition = 0;
        OutputStream os;

        if (resuming && statusCode == 206) {
            LOGGER.info("[DownloadTask] Server accepted partial content (Range response). Appending...");
            startPosition = existingSize;
            os = Files.newOutputStream(targetFile, StandardOpenOption.APPEND);
        } else {
            if (resuming) {
                LOGGER.info("[DownloadTask] Server ignored Range request (status " + statusCode + "). Starting from scratch.");
            }
            startPosition = 0;
            os = Files.newOutputStream(targetFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        if (statusCode != 200 && statusCode != 206) {
            os.close();
            throw new IOException("Server returned HTTP status code: " + statusCode);
        }

        long totalDownloaded = startPosition;
        long bytesDownloadedThisSession = 0;
        long startTime = System.currentTimeMillis();
        long lastProgressUpdateTime = startTime;

        try (InputStream is = new BufferedInputStream(response.body());
             OutputStream out = os) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = is.read(buffer)) != -1) {
                if (isCancelled()) {
                    LOGGER.info("[DownloadTask] Download cancelled by user.");
                    throw new InterruptedException("Download cancelled");
                }

                out.write(buffer, 0, bytesRead);
                totalDownloaded += bytesRead;
                bytesDownloadedThisSession += bytesRead;

                long now = System.currentTimeMillis();
                // Update progress metrics every 200ms to keep UI extremely smooth and interactive
                if (now - lastProgressUpdateTime > 200) {
                    double elapsedSeconds = (now - startTime) / 1000.0;
                    double speedBytesPerSec = elapsedSeconds > 0 ? (bytesDownloadedThisSession / elapsedSeconds) : 0;
                    long remainingBytes = totalExpectedSize - totalDownloaded;
                    double remainingSeconds = speedBytesPerSec > 0 ? (remainingBytes / speedBytesPerSec) : 0;

                    updateProgress(totalDownloaded, totalExpectedSize);
                    
                    String speedText = formatSpeed(speedBytesPerSec);
                    String timeText = formatRemainingTime(remainingSeconds);
                    updateMessage(String.format("Downloading... %s | %s remaining", speedText, timeText));
                    
                    lastProgressUpdateTime = now;
                }
            }

            // Final progress update
            updateProgress(totalExpectedSize, totalExpectedSize);
            updateMessage("Download completed. Verifying integrity...");

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[DownloadTask] Error streaming download data: " + e.getMessage());
            throw e;
        }

        return null;
    }

    private String formatSpeed(double speedBytesPerSec) {
        if (speedBytesPerSec < 1024) {
            return String.format("%.0f B/s", speedBytesPerSec);
        } else if (speedBytesPerSec < 1024 * 1024) {
            return String.format("%.1f KB/s", speedBytesPerSec / 1024.0);
        } else {
            return String.format("%.2f MB/s", speedBytesPerSec / (1024.0 * 1024.0));
        }
    }

    private String formatRemainingTime(double remainingSeconds) {
        if (remainingSeconds <= 0) {
            return "Calculating...";
        }
        if (remainingSeconds < 60) {
            return String.format("%.0f sec", remainingSeconds);
        }
        long minutes = (long) (remainingSeconds / 60);
        long seconds = (long) (remainingSeconds % 60);
        return String.format("%d min %d sec", minutes, seconds);
    }
}
