package service.sync;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import utils.DBConnection;

public final class SyncConflictResolver {

    private SyncConflictResolver() {
    }

    public static Instant parseTimestamp(String ts) {
        if (ts == null || ts.isBlank()) {
            return Instant.MIN;
        }
        try {
            String normalized = ts.trim().replace(" ", "T");
            if (!normalized.contains("Z") && !normalized.contains("+") && !normalized.substring(Math.max(0, normalized.length() - 6)).contains("-")) {
                normalized = normalized + "Z";
            }
            return Instant.parse(normalized);
        } catch (Exception e) {
            try {
                String normalized = ts.trim().replace(" ", "T");
                if (normalized.endsWith("Z")) {
                    normalized = normalized.substring(0, normalized.length() - 1);
                }
                return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
            } catch (Exception ex) {
                try {
                    String clean = ts.trim();
                    if (clean.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                        return LocalDateTime.parse(clean + "T00:00:00").toInstant(ZoneOffset.UTC);
                    }
                } catch (Exception ignored) {}
                System.err.println("[SyncConflictResolver] Failed to parse timestamp: " + ts + " - " + ex.getMessage());
                return Instant.MIN;
            }
        }
    }

    public static void logConflict(String tableName, String recordUuid, String localUpdatedAt,
                                   String remoteUpdatedAt, String localData, String remoteData, String strategy) {
        System.out.println("[SyncConflictResolver] Conflict detected on " + tableName + " with UUID " + recordUuid + ". Resolution strategy: " + strategy);
        String sql = "INSERT INTO sync_conflicts (table_name, record_uuid, local_updated_at, remote_updated_at, local_data, remote_data, resolution_strategy) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, recordUuid);
            ps.setString(3, localUpdatedAt);
            ps.setString(4, remoteUpdatedAt);
            ps.setString(5, localData);
            ps.setString(6, remoteData);
            ps.setString(7, strategy);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[SyncConflictResolver] Failed to log conflict into DB: " + e.getMessage());
        }
    }

    public static boolean checkPushConflictAndResolve(Connection conn, SupabaseRestClient http, String table, 
                                                      SupabaseEndpoints endpoint, String uuid, String localUpdatedAt, List<String> cols) {
        try {
            var res = http.get(endpoint, "uuid=eq." + uuid + "&select=updated_at");
            if (res.statusCode() != 200) {
                return false;
            }
            JsonElement root = JsonParser.parseString(res.body());
            if (!root.isJsonArray()) {
                return false;
            }
            JsonArray arr = root.getAsJsonArray();
            if (arr.size() == 0) {
                return false;
            }
            JsonObject remoteObj = arr.get(0).getAsJsonObject();
            String remoteUpdatedAt = remoteObj.has("updated_at") && !remoteObj.get("updated_at").isJsonNull() ? remoteObj.get("updated_at").getAsString() : null;

            Instant localInst = parseTimestamp(localUpdatedAt);
            Instant remoteInst = parseTimestamp(remoteUpdatedAt);

            if (remoteInst.isAfter(localInst)) {
                logConflict(table, uuid, localUpdatedAt, remoteUpdatedAt, "Local push rejected; remote is newer", remoteObj.toString(), "LAST_WRITE_WINS_REMOTE_WINS");

                var fullRes = http.get(endpoint, "uuid=eq." + uuid + "&select=*");
                if (fullRes.statusCode() == 200) {
                    JsonElement fullRoot = JsonParser.parseString(fullRes.body());
                    if (fullRoot.isJsonArray() && fullRoot.getAsJsonArray().size() > 0) {
                        JsonObject fullRemote = fullRoot.getAsJsonArray().get(0).getAsJsonObject();
                        upsertRemoteObjectToLocal(conn, table, fullRemote, cols);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            System.err.println("[SyncConflictResolver] Error checking push conflict: " + e.getMessage());
        }
        return false;
    }

    public static void upsertRemoteObjectToLocal(Connection conn, String table, JsonObject o, List<String> cols) throws Exception {
        List<String> insertCols = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (String col : cols) {
            if (o.has(col)) {
                insertCols.add(col);
                if ("sync_status".equals(col)) {
                    values.add("SYNCED");
                    continue;
                }
                JsonElement val = o.get(col);
                if (val.isJsonNull()) {
                    values.add(null);
                } else if (val.isJsonPrimitive()) {
                    var prim = val.getAsJsonPrimitive();
                    if (prim.isBoolean()) {
                        values.add(prim.getAsBoolean() ? 1 : 0);
                    } else if (prim.isNumber()) {
                        if (prim.getAsString().contains(".")) {
                            values.add(prim.getAsDouble());
                        } else {
                            values.add(prim.getAsLong());
                        }
                    } else {
                        values.add(prim.getAsString());
                    }
                } else {
                    values.add(val.toString());
                }
            }
        }

        if (insertCols.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder("INSERT OR REPLACE INTO ").append(table).append(" (");
        for (int i = 0; i < insertCols.size(); i++) {
            sb.append(insertCols.get(i));
            if (i < insertCols.size() - 1) {
                sb.append(",");
            }
        }
        sb.append(") VALUES (");
        for (int i = 0; i < insertCols.size(); i++) {
            sb.append("?");
            if (i < insertCols.size() - 1) {
                sb.append(",");
            }
        }
        sb.append(")");

        try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < values.size(); i++) {
                Object v = values.get(i);
                if (v == null) {
                    ps.setNull(i + 1, java.sql.Types.VARCHAR);
                } else {
                    ps.setObject(i + 1, v);
                }
            }
            ps.executeUpdate();
        }
    }

    public static List<String> getColumns(Connection conn, String table) throws Exception {
        List<String> cols = new ArrayList<>();
        try (var ps = conn.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    cols.add(rs.getString("name"));
                }
            }
        }
        return cols;
    }
}
