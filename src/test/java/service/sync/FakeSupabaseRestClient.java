package service.sync;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;

public class FakeSupabaseRestClient extends SupabaseRestClient {

    private final Map<SupabaseEndpoints, List<JsonObject>> db = new HashMap<>();
    private boolean online = true;

    public FakeSupabaseRestClient() {
        super("https://fake-supabase.co", "fake-anon-key");
        for (SupabaseEndpoints endpoint : SupabaseEndpoints.values()) {
            db.put(endpoint, new ArrayList<>());
        }
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public void clear() {
        for (List<JsonObject> list : db.values()) {
            list.clear();
        }
    }

    public List<JsonObject> getTableData(SupabaseEndpoints endpoint) {
        return db.get(endpoint);
    }

    @Override
    public boolean ping() {
        return online;
    }

    @Override
    public HttpResponse<String> get(SupabaseEndpoints table, String query) throws IOException, InterruptedException {
        if (!online) {
            throw new IOException("Connection refused (mock offline)");
        }

        List<JsonObject> allRows = db.get(table);
        JsonArray filtered = new JsonArray();

        // Simple query parsing
        String uuidFilter = null;
        String updatedAtFilter = null;
        String createdAtFilter = null;

        if (query != null && !query.isEmpty()) {
            String[] params = query.split("&");
            for (String param : params) {
                String p;
                try {
                    p = java.net.URLDecoder.decode(param, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception e) {
                    p = param;
                }
                if (p.startsWith("uuid=eq.")) {
                    uuidFilter = p.substring(8);
                } else if (p.startsWith("updated_at=gt.")) {
                    updatedAtFilter = p.substring(14);
                } else if (p.startsWith("created_at=gt.")) {
                    createdAtFilter = p.substring(14);
                }
            }
        }

        for (JsonObject row : allRows) {
            if (uuidFilter != null) {
                String rowUuid = row.has("uuid") && !row.get("uuid").isJsonNull() ? row.get("uuid").getAsString() : null;
                if (!uuidFilter.equals(rowUuid)) {
                    continue;
                }
            }

            if (updatedAtFilter != null) {
                String rowUpdated = row.has("updated_at") && !row.get("updated_at").isJsonNull() ? row.get("updated_at").getAsString() : null;
                if (rowUpdated == null || !isAfter(rowUpdated, updatedAtFilter)) {
                    continue;
                }
            }

            if (createdAtFilter != null) {
                String rowCreated = row.has("created_at") && !row.get("created_at").isJsonNull() ? row.get("created_at").getAsString() : null;
                if (rowCreated == null || !isAfter(rowCreated, createdAtFilter)) {
                    continue;
                }
            }

            filtered.add(row);
        }

        return new MockHttpResponse(200, filtered.toString());
    }

    @Override
    public HttpResponse<String> postJsonWithQuery(SupabaseEndpoints table, String query, String jsonBody, String preferHeader) throws IOException, InterruptedException {
        if (!online) {
            throw new IOException("Connection refused (mock offline)");
        }

        JsonElement root = JsonParser.parseString(jsonBody);
        JsonArray array = root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
        if (root.isJsonObject()) {
            array.add(root.getAsJsonObject());
        }

        List<JsonObject> allRows = db.get(table);

        for (JsonElement el : array) {
            JsonObject newRow = el.getAsJsonObject();
            String newUuid = newRow.has("uuid") && !newRow.get("uuid").isJsonNull() ? newRow.get("uuid").getAsString() : null;

            if (newUuid != null) {
                // Remove existing row to simulate UPSERT
                allRows.removeIf(row -> {
                    String existingUuid = row.has("uuid") && !row.get("uuid").isJsonNull() ? row.get("uuid").getAsString() : null;
                    return newUuid.equals(existingUuid);
                });
            }
            allRows.add(newRow);
        }

        return new MockHttpResponse(201, "[]");
    }

    @Override
    public HttpResponse<String> patchJson(SupabaseEndpoints table, String postgrestFilter, String jsonBody, String preferHeader) throws IOException, InterruptedException {
        if (!online) {
            throw new IOException("Connection refused (mock offline)");
        }

        String uuidFilter = null;
        if (postgrestFilter != null && postgrestFilter.startsWith("uuid=eq.")) {
            uuidFilter = postgrestFilter.substring(8);
        }

        if (uuidFilter == null) {
            return new MockHttpResponse(400, "Bad Request: Missing uuid filter");
        }

        List<JsonObject> allRows = db.get(table);
        JsonObject patchData = JsonParser.parseString(jsonBody).getAsJsonObject();

        for (JsonObject row : allRows) {
            String rowUuid = row.has("uuid") && !row.get("uuid").isJsonNull() ? row.get("uuid").getAsString() : null;
            if (uuidFilter.equals(rowUuid)) {
                for (Map.Entry<String, JsonElement> entry : patchData.entrySet()) {
                    row.add(entry.getKey(), entry.getValue());
                }
            }
        }

        return new MockHttpResponse(200, "[]");
    }

    @Override
    public HttpResponse<String> delete(SupabaseEndpoints table, String postgrestFilter) throws IOException, InterruptedException {
        if (!online) {
            throw new IOException("Connection refused (mock offline)");
        }

        String uuidFilter = null;
        if (postgrestFilter != null && postgrestFilter.startsWith("uuid=eq.")) {
            uuidFilter = postgrestFilter.substring(8);
        }

        if (uuidFilter == null) {
            return new MockHttpResponse(400, "Bad Request: Missing uuid filter");
        }

        final String targetUuid = uuidFilter;
        db.get(table).removeIf(row -> {
            String rowUuid = row.has("uuid") && !row.get("uuid").isJsonNull() ? row.get("uuid").getAsString() : null;
            return targetUuid.equals(rowUuid);
        });

        return new MockHttpResponse(200, "[]");
    }

    private boolean isAfter(String t1, String t2) {
        try {
            String f1 = t1.trim().replace(" ", "T");
            if (!f1.contains("Z") && !f1.contains("+") && f1.length() == 19) f1 += "Z";
            String f2 = t2.trim().replace(" ", "T");
            if (!f2.contains("Z") && !f2.contains("+") && f2.length() == 19) f2 += "Z";
            return Instant.parse(f1).isAfter(Instant.parse(f2));
        } catch (Exception e) {
            return t1.compareTo(t2) > 0;
        }
    }

    public static class MockHttpResponse implements HttpResponse<String> {
        private final int code;
        private final String body;

        public MockHttpResponse(int code, String body) {
            this.code = code;
            this.body = body;
        }

        @Override
        public int statusCode() { return code; }
        @Override
        public java.net.http.HttpRequest request() { return null; }
        @Override
        public java.net.http.HttpHeaders headers() { return null; }
        @Override
        public String body() { return body; }
        @Override
        public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override
        public java.net.URI uri() { return null; }
        @Override
        public java.net.http.HttpClient.Version version() { return null; }
    }
}
