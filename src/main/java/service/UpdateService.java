package service;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseRestClient;
import api.supabase.updates.UpdatesSupabaseApi;
import model.AppUpdate;
import utils.VersionComparator;

public class UpdateService {

    private static final Logger LOGGER = Logger.getLogger(UpdateService.class.getName());
    private static final String VERSION_PROP_FILE = "/version.properties";
    private static final long CACHE_DURATION_MS = 24 * 60 * 60 * 1000L; // 24 Hours

    // Preferences keys
    private static final String KEY_LAST_CHECK = "last_update_check";
    private static final String KEY_LAST_VERSION = "last_available_version";
    private static final String KEY_IGNORED_VERSION = "ignored_version";
    private static final String KEY_PREFERRED_CHANNEL = "preferred_release_channel";

    private final Preferences prefs = Preferences.userNodeForPackage(UpdateService.class);

    public enum UpdateStatus {
        NO_UPDATE,
        OPTIONAL_UPDATE,
        MANDATORY_UPDATE
    }

    public static class UpdateCheckResult {
        private final UpdateStatus status;
        private final AppUpdate update;
        private final String localVersion;

        public UpdateCheckResult(UpdateStatus status, AppUpdate update, String localVersion) {
            this.status = status;
            this.update = update;
            this.localVersion = localVersion;
        }

        public UpdateStatus getStatus() {
            return status;
        }

        public AppUpdate getUpdate() {
            return update;
        }

        public String getLocalVersion() {
            return localVersion;
        }
    }

    /**
     * Reads the current local version from resources/version.properties.
     */
    public String getLocalVersion() {
        Properties props = new Properties();
        try (InputStream in = UpdateService.class.getResourceAsStream(VERSION_PROP_FILE)) {
            if (in != null) {
                props.load(in);
                String version = props.getProperty("version");
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[UpdateService] Failed to read " + VERSION_PROP_FILE + ": " + e.getMessage());
        }
        return "1.0.0"; // Fallback default
    }

    public String getPreferredChannel() {
        return prefs.get(KEY_PREFERRED_CHANNEL, "stable");
    }

    public void setPreferredChannel(String channel) {
        prefs.put(KEY_PREFERRED_CHANNEL, channel != null ? channel.trim() : "stable");
    }

    public String getIgnoredVersion() {
        return prefs.get(KEY_IGNORED_VERSION, "");
    }

    public void setIgnoredVersion(String version) {
        prefs.put(KEY_IGNORED_VERSION, version != null ? version.trim() : "");
    }

    /**
     * Resets the update check cache (useful for testing or forcing a refresh).
     */
    public void resetCache() {
        prefs.remove(KEY_LAST_CHECK);
        prefs.remove(KEY_LAST_VERSION);
    }

    /**
     * Checks for updates. If forced is false, checks cache duration before querying Supabase.
     */
    public UpdateCheckResult checkForUpdates(boolean forced) {
        long startTime = System.currentTimeMillis();
        String localVer = getLocalVersion();
        String channel = getPreferredChannel();

        if (!forced) {
            long lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L);
            long diff = System.currentTimeMillis() - lastCheck;
            if (diff >= 0 && diff < CACHE_DURATION_MS) {
                String cachedVer = prefs.get(KEY_LAST_VERSION, "");
                LOGGER.info("[UpdateService] Cache hit: last check was " + (diff / 60000) + " mins ago. Continuing with cached available version: " + (cachedVer.isEmpty() ? "none" : cachedVer));
                return new UpdateCheckResult(UpdateStatus.NO_UPDATE, null, localVer);
            }
        }

        Optional<SupabaseRestClient> clientOpt = SupabaseGate.restClientIfConfigured();
        if (clientOpt.isEmpty()) {
            logTelemetry(localVer, null, null, channel, UpdateStatus.NO_UPDATE, 0, System.currentTimeMillis() - startTime, "Offline", "Supabase client not configured");
            return new UpdateCheckResult(UpdateStatus.NO_UPDATE, null, localVer);
        }

        try {
            long apiStartTime = System.currentTimeMillis();
            SupabaseRestClient http = clientOpt.get();
            UpdatesSupabaseApi api = new UpdatesSupabaseApi(http);
            AppUpdate update = api.fetchLatestRelease(channel);
            long apiDuration = System.currentTimeMillis() - apiStartTime;

            // Update cache check time
            prefs.putLong(KEY_LAST_CHECK, System.currentTimeMillis());

            if (update == null || update.getVersion() == null || update.getVersion().isBlank()) {
                prefs.put(KEY_LAST_VERSION, "");
                logTelemetry(localVer, null, null, channel, UpdateStatus.NO_UPDATE, apiDuration, System.currentTimeMillis() - startTime, "Online", null);
                return new UpdateCheckResult(UpdateStatus.NO_UPDATE, null, localVer);
            }

            String latestVer = update.getVersion();
            prefs.put(KEY_LAST_VERSION, latestVer);

            int cmp = VersionComparator.compare(localVer, latestVer);
            if (cmp >= 0) {
                logTelemetry(localVer, latestVer, update.getMinimumSupportedVersion(), channel, UpdateStatus.NO_UPDATE, apiDuration, System.currentTimeMillis() - startTime, "Online", null);
                // Mark update model fields
                update.setNewerThanInstalled(false);
                update.setMandatoryUpdate(false);
                return new UpdateCheckResult(UpdateStatus.NO_UPDATE, update, localVer);
            }

            // Version is newer than installed
            update.setNewerThanInstalled(true);

            // Check if mandatory based on minimum supported version or explicit flag
            boolean isMandatory = update.isMandatory();
            if (update.getMinimumSupportedVersion() != null && !update.getMinimumSupportedVersion().isBlank()) {
                int cmpMin = VersionComparator.compare(localVer, update.getMinimumSupportedVersion());
                if (cmpMin < 0) {
                    isMandatory = true;
                }
            }
            update.setMandatoryUpdate(isMandatory);

            // Ignore option for optional updates only
            if (!isMandatory) {
                String ignored = getIgnoredVersion();
                if (latestVer.equalsIgnoreCase(ignored)) {
                    LOGGER.info("[UpdateService] Version " + latestVer + " is ignored by user settings. Skipping update alert.");
                    logTelemetry(localVer, latestVer, update.getMinimumSupportedVersion(), channel, UpdateStatus.NO_UPDATE, apiDuration, System.currentTimeMillis() - startTime, "Online", "Ignored by user");
                    return new UpdateCheckResult(UpdateStatus.NO_UPDATE, update, localVer);
                }
            }

            UpdateStatus status = isMandatory ? UpdateStatus.MANDATORY_UPDATE : UpdateStatus.OPTIONAL_UPDATE;
            logTelemetry(localVer, latestVer, update.getMinimumSupportedVersion(), channel, status, apiDuration, System.currentTimeMillis() - startTime, "Online", null);
            return new UpdateCheckResult(status, update, localVer);

        } catch (Exception e) {
            logTelemetry(localVer, null, null, channel, UpdateStatus.NO_UPDATE, 0, System.currentTimeMillis() - startTime, "Error", e.getMessage());
            return new UpdateCheckResult(UpdateStatus.NO_UPDATE, null, localVer);
        }
    }

    private void logTelemetry(String installed, String latest, String minSupported, String channel,
                              UpdateStatus status, long apiTimeMs, long totalTimeMs, String network, String failureReason) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[UpdateService Telemetry]\n");
        sb.append("Installed Version: ").append(installed).append("\n");
        sb.append("Latest Version: ").append(latest != null ? latest : "None").append("\n");
        sb.append("Minimum Supported: ").append(minSupported != null ? minSupported : "None").append("\n");
        sb.append("Release Channel: ").append(channel).append("\n");
        sb.append("Update Classification: ").append(status).append("\n");
        sb.append("Response Time: ").append(apiTimeMs).append(" ms\n");
        sb.append("Total Check Duration: ").append(totalTimeMs).append(" ms\n");
        sb.append("Network Status: ").append(network).append("\n");
        if (failureReason != null) {
            sb.append("Failure Reason: ").append(failureReason).append("\n");
        }
        LOGGER.info(sb.toString());
    }
}
