package utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * Validates that the local SQLite schema is strictly aligned with the required 
 * synchronization structure (UUID PKs, sync markers) on application startup.
 */
public class LocalSchemaVerifier {

    private static final List<String> SYNC_TABLES = List.of(
        "clients", "jobs", "invoice_master", "invoice_job_mapping", "payments", 
        "payment_allocations", "bank_details", "company_details", "hsn_sac_master"
    );

    public static void verifySchema() {
        System.out.println("[LocalSchemaVerifier] Commencing Startup Integrity Audit...");
        int issuesFound = 0;
        
        try (Connection con = java.sql.DriverManager.getConnection(DBConnection.getUrl())) {
            for (String table : SYNC_TABLES) {
                if (!tableExists(con, table)) {
                    System.err.println("  [ERR] Missing table: " + table);
                    issuesFound++;
                    continue;
                }
                
                boolean hasUuid = false;
                boolean isUuidPk = false;
                boolean hasSyncStatus = false;
                boolean hasSyncVersion = false;
                
                try (PreparedStatement ps = con.prepareStatement("PRAGMA table_info(" + table + ")")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String colName = rs.getString("name");
                            if ("uuid".equalsIgnoreCase(colName)) {
                                hasUuid = true;
                                if (rs.getInt("pk") > 0) {
                                    isUuidPk = true;
                                }
                            } else if ("sync_status".equalsIgnoreCase(colName)) {
                                hasSyncStatus = true;
                            } else if ("sync_version".equalsIgnoreCase(colName)) {
                                hasSyncVersion = true;
                            }
                        }
                    }
                }
                
                if (!hasUuid) {
                    System.err.println("  [ERR] " + table + " is missing 'uuid' column.");
                    issuesFound++;
                } else if (!isUuidPk) {
                    // It might not be marked as PK natively in SQLite due to legacy CREATE statements,
                    // so we must check if there is a UNIQUE index backing it.
                    if (!hasUniqueIndex(con, table, "uuid")) {
                        System.err.println("  [ERR] " + table + " 'uuid' column lacks a PRIMARY KEY or UNIQUE constraint.");
                        issuesFound++;
                    }
                }
                
                if (!hasSyncStatus) {
                    System.err.println("  [ERR] " + table + " is missing 'sync_status' column.");
                    issuesFound++;
                }
                
                if (!hasSyncVersion) {
                    System.err.println("  [ERR] " + table + " is missing 'sync_version' column.");
                    issuesFound++;
                }
            }
            
            if (issuesFound > 0) {
                System.err.println("[LocalSchemaVerifier] Startup Audit Failed! " + issuesFound + " local schema integrity fractures detected.");
            } else {
                System.out.println("[LocalSchemaVerifier] Startup Audit Passed. Local sync constraints are strictly enforced.");
            }
            
        } catch (Exception e) {
            System.err.println("[LocalSchemaVerifier] Failed to execute startup schema audit: " + e.getMessage());
        }
    }
    
    private static boolean tableExists(Connection con, String table) throws Exception {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    private static boolean hasUniqueIndex(Connection con, String table, String column) throws Exception {
        try (PreparedStatement ps = con.prepareStatement("PRAGMA index_list(" + table + ")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getInt("unique") == 1) {
                        String idxName = rs.getString("name");
                        try (PreparedStatement psInfo = con.prepareStatement("PRAGMA index_info(" + idxName + ")")) {
                            try (ResultSet rsInfo = psInfo.executeQuery()) {
                                while (rsInfo.next()) {
                                    if (column.equalsIgnoreCase(rsInfo.getString("name"))) {
                                        return true; // Found a unique index specifically covering this column
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}
