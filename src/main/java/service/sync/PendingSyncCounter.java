package service.sync;

import utils.DBConnection;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class PendingSyncCounter {
    private PendingSyncCounter() {}

    public static int getPendingRecordsCount() {
        int total = 0;
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Find all tables in SQLite
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }

            for (String table : tables) {
                boolean hasSyncStatus = false;
                boolean hasIsDeleted = false;
                
                // Inspect table columns
                try (ResultSet colRs = conn.getMetaData().getColumns(null, null, table, null)) {
                    while (colRs.next()) {
                        String columnName = colRs.getString("COLUMN_NAME");
                        if ("sync_status".equalsIgnoreCase(columnName)) {
                            hasSyncStatus = true;
                        }
                        if ("is_deleted".equalsIgnoreCase(columnName)) {
                            hasIsDeleted = true;
                        }
                    }
                }
                
                if (hasSyncStatus) {
                    String query = "SELECT COUNT(*) FROM " + table + " WHERE ";
                    if (hasIsDeleted) {
                        query += "IFNULL(is_deleted, 0) = 0 AND ";
                    }
                    query += "UPPER(TRIM(COALESCE(sync_status, ''))) IN ('PENDING', 'FAILED')";
                    
                    try (Statement countStmt = conn.createStatement();
                         ResultSet countRs = countStmt.executeQuery(query)) {
                        if (countRs.next()) {
                            total += countRs.getInt(1);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[PendingSyncCounter] Error: " + e.getMessage());
        }
        return total;
    }
}
