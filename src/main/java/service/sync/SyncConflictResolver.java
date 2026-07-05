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
                } catch (Exception ex2) { service.LoggerService.dbWarn("[SYNC] Failed to parse fallback TS " + ts + ": " + ex2.getMessage()); }
                System.err.println("[SyncConflictResolver] Failed to parse timestamp: " + ts + " - " + ex.getMessage());
                return Instant.MIN;
            }
        }
    }

    public static void logConflict(Connection conn, String tableName, String recordUuid, String localUpdatedAt,
                                   String remoteUpdatedAt, String localData, String remoteData, String strategy) {
        System.out.println("[SyncConflictResolver] Conflict detected on " + tableName + " with UUID " + recordUuid + ". Resolution strategy: " + strategy);
        String sql = "INSERT INTO sync_conflicts (table_name, record_uuid, local_updated_at, remote_updated_at, local_data, remote_data, resolution_strategy) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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
            System.out.println("[DIAGNOSTIC] GET updated_at status=" + res.statusCode() + " body=" + res.body());
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

            boolean isRemoteNewer = remoteInst != Instant.MIN && localInst != Instant.MIN && remoteInst.isAfter(localInst);
            long localSyncVersion = 0L;
            try (PreparedStatement ps = conn.prepareStatement("SELECT sync_version FROM " + table + " WHERE uuid = ?")) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        localSyncVersion = rs.getLong(1);
                    }
                }
            } catch (Exception ignored) {}
            long remoteSyncVersion = remoteObj.has("sync_version") && !remoteObj.get("sync_version").isJsonNull() ? remoteObj.get("sync_version").getAsLong() : 0L;
            System.out.println("[DIAGNOSTIC] Push Conflict check for " + table + " " + uuid + ": local.updated_at='" + localUpdatedAt + "' (" + localInst + "), remote.updated_at='" + remoteUpdatedAt + "' (" + remoteInst + "), local.sync_version=" + localSyncVersion + ", remote.sync_version=" + remoteSyncVersion + ". isRemoteNewer=" + isRemoteNewer);

            if (isRemoteNewer) {
                logConflict(conn, table, uuid, localUpdatedAt, remoteUpdatedAt, "Local push rejected; remote is newer", remoteObj.toString(), "LAST_WRITE_WINS_REMOTE_WINS");

                var fullRes = http.get(endpoint, "uuid=eq." + uuid + "&select=*");
                System.out.println("[DIAGNOSTIC] GET full record status=" + fullRes.statusCode() + " body=" + fullRes.body());
                if (fullRes.statusCode() == 200) {
                    JsonElement fullRoot = JsonParser.parseString(fullRes.body());
                    if (fullRoot.isJsonArray() && fullRoot.getAsJsonArray().size() > 0) {
                        JsonObject fullRemote = fullRoot.getAsJsonArray().get(0).getAsJsonObject();
                        System.out.println("[DIAGNOSTIC] Upserting remote object to local: " + fullRemote);
                        upsertRemoteObjectToLocal(conn, table, fullRemote, cols);
                        System.out.println("[DIAGNOSTIC] Upsert remote object complete.");
                        
                        try (PreparedStatement markPs = conn.prepareStatement(
                                "UPDATE " + table + " SET sync_status='SYNCED', synced_at=datetime('now') WHERE uuid=?")) {
                            markPs.setString(1, uuid);
                            int rows = markPs.executeUpdate();
                            System.out.println("[DIAGNOSTIC] Marked as SYNCED rows updated=" + rows);
                        } catch (Exception markEx) {
                            System.err.println("[SyncConflictResolver] Failed to mark " + table + " " + uuid + " as SYNCED: " + markEx.getMessage());
                        }
                    }
                }
                return true;
            }
        } catch (Exception e) {
            System.err.println("[SyncConflictResolver] Error checking push conflict: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public static void upsertRemoteObjectToLocal(Connection conn, String table, JsonObject o, List<String> cols) throws Exception {
        List<String> insertCols = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        System.out.println("[DIAGNOSTIC] upsertRemoteObjectToLocal: table=" + table + ", cols=" + cols + ", json=" + o);

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
            System.out.println("[DIAGNOSTIC] No overlapping columns to insert!");
            return;
        }

        StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(" (");
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
        sb.append(") ON CONFLICT(uuid) DO UPDATE SET ");
        boolean first = true;
        for (String col : insertCols) {
            if ("uuid".equalsIgnoreCase(col) || "id".equalsIgnoreCase(col)) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            sb.append(col).append("=excluded.").append(col);
            first = false;
        }

        System.out.println("[DIAGNOSTIC] SQL=" + sb.toString() + " | values=" + values);

        try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < values.size(); i++) {
                Object v = values.get(i);
                if (v == null) {
                    ps.setNull(i + 1, java.sql.Types.VARCHAR);
                } else {
                    ps.setObject(i + 1, v);
                }
            }
            int updatedRows = ps.executeUpdate();
            System.out.println("[DIAGNOSTIC] ps.executeUpdate() returned: " + updatedRows);
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

    public static boolean validateAndResolveDoubleSpend(Connection con, String allocUuid, String paymentUuid, 
                                                        double allocatedAmount, boolean isIncomingSync, 
                                                        String remoteUpdatedAt, String remoteDataJson) throws Exception {
        String clientUuid = null;
        String paySql = "SELECT client_uuid FROM payments WHERE uuid = ?";
        try (PreparedStatement ps = con.prepareStatement(paySql)) {
            ps.setString(1, paymentUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    clientUuid = rs.getString(1);
                }
            }
        }
        if (clientUuid == null) {
            return false;
        }

        double totalPayments = 0;
        double totalAllocated = 0;

        String sqlPay = "SELECT SUM(amount) FROM payments WHERE client_uuid = ?";
        try (PreparedStatement ps = con.prepareStatement(sqlPay)) {
            ps.setString(1, clientUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalPayments = rs.getDouble(1);
            }
        }

        String sqlAlloc = """
            SELECT SUM(pa.allocated_amount) 
            FROM payment_allocations pa
            JOIN payments p ON pa.payment_uuid = p.uuid
            WHERE p.client_uuid = ? AND COALESCE(pa.is_deleted, 0) = 0 AND pa.uuid <> ?
        """;
        try (PreparedStatement ps = con.prepareStatement(sqlAlloc)) {
            ps.setString(1, clientUuid);
            ps.setString(2, allocUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) totalAllocated = rs.getDouble(1);
            }
        }

        double available = totalPayments - totalAllocated;

        if (available - allocatedAmount < -0.001) {
            System.out.println("[Double-Spend Prevention] Rejecting allocation " + allocUuid + " for client " + clientUuid + ". Available: " + available + ", requested: " + allocatedAmount);
            
            logConflict(
                con,
                "payment_allocations",
                allocUuid,
                LocalDateTime.now().toString(),
                remoteUpdatedAt,
                "Local double-spend prevention: rejected allocation due to negative client balance",
                remoteDataJson,
                "DOUBLE_SPEND_REJECTION"
            );

            String localUpdate;
            if (!isIncomingSync) {
                localUpdate = "UPDATE payment_allocations SET is_deleted = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
            } else {
                localUpdate = "UPDATE payment_allocations SET is_deleted = 1, sync_status = 'SYNCED', updated_at = datetime('now') WHERE uuid = ?";
            }
            try (PreparedStatement ps = con.prepareStatement(localUpdate)) {
                ps.setString(1, allocUuid);
                ps.executeUpdate();
            }

            String invoiceUuid = null;
            String invSql = "SELECT invoice_uuid FROM payment_allocations WHERE uuid = ?";
            try (PreparedStatement ps = con.prepareStatement(invSql)) {
                ps.setString(1, allocUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        invoiceUuid = rs.getString(1);
                    }
                }
            }

            // Log double spend rejection details
            if (invoiceUuid != null) {
                String invDetailsSql = "SELECT invoice_no, client_name, invoice_date, amount, type FROM invoice_master WHERE uuid = ?";
                String invNo = "";
                String clName = "";
                String invDate = "";
                double invAmt = 0.0;
                String invType = "";
                try (PreparedStatement ps = con.prepareStatement(invDetailsSql)) {
                    ps.setString(1, invoiceUuid);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            invNo = rs.getString("invoice_no");
                            clName = rs.getString("client_name");
                            invDate = rs.getString("invoice_date");
                            invAmt = rs.getDouble("amount");
                            invType = rs.getString("type");
                        }
                    }
                } catch (Exception e) {
                    service.LoggerService.dbWarn("[CONFLICT] Failed to read invoice details for rejected allocation " + allocUuid + ": " + e.getMessage());
                }
                service.LoggerService.dbWarn("[CONFLICT] DOUBLE_SPEND_REJECTION for invoice=" + invNo + " (UUID=" + invoiceUuid + "), client=" + clName + ", amount=" + invAmt + ", reason=Allocation " + allocUuid + " failed validation (negative client balance)");
            }

            if (invoiceUuid != null) {
                new service.InvoiceMasterService().recalculateInvoiceTotals(con, invoiceUuid);
            }

            try {
                controller.MainController.showSyncConflictNotification();
            } catch (Throwable e) { service.LoggerService.dbWarn("[SYNC] UI notification failed for conflict: " + e.getMessage()); }

            return true;
        }

        return false;
    }
}
