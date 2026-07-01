package api.supabase.updates;

import java.io.IOException;
import java.net.http.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import model.AppUpdate;

public final class UpdatesSupabaseApi {

    private final SupabaseRestClient http;
    private final Gson gson = new Gson();

    public UpdatesSupabaseApi(SupabaseRestClient http) {
        this.http = http;
    }

    /**
     * Fetches the latest published app update for a specific release channel.
     * Selects the highest version semantically using client-side VersionComparator.
     * Returns null if no valid update is found.
     */
    public AppUpdate fetchLatestRelease(String channel) throws IOException, InterruptedException {
        String query = "select=*&published=eq.true&release_channel=eq." + (channel != null ? channel.trim() : "stable");
        HttpResponse<String> res = http.getWithTimeout(SupabaseEndpoints.APP_UPDATES, query, java.time.Duration.ofSeconds(5));
        int code = res.statusCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP update check failed with status " + code + ": " + res.body());
        }

        String body = res.body();
        if (body == null || body.isBlank() || "[]".equals(body.trim())) {
            return null;
        }

        try {
            JsonElement element = JsonParser.parseString(body);
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                AppUpdate highestUpdate = null;

                for (int i = 0; i < array.size(); i++) {
                    JsonObject obj = array.get(i).getAsJsonObject();
                    AppUpdate candidate = gson.fromJson(obj, AppUpdate.class);

                    // Phase 1 Hardening: Validate that all required metadata is present
                    if (candidate == null || !candidate.isValidMetadata()) {
                        System.err.println("[UpdatesSupabaseApi] Warning: Ignored release update due to incomplete/invalid metadata for version "
                                + (candidate != null ? candidate.getVersion() : "unknown"));
                        continue;
                    }

                    if (highestUpdate == null) {
                        highestUpdate = candidate;
                    } else {
                        // Select the highest semantic version
                        int cmp = utils.VersionComparator.compare(highestUpdate.getVersion(), candidate.getVersion());
                        if (cmp < 0) {
                            highestUpdate = candidate;
                        }
                    }
                }
                return highestUpdate;
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse app updates JSON response: " + e.getMessage(), e);
        }
        return null;
    }
}
