package service.sync;

import utils.DBConnection;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

public final class PendingSyncCounter {
    private PendingSyncCounter() {}

    private static final List<String> SYNCABLE_BUSINESS_TABLES = List.of(
        "clients", "jobs", "invoice_master", "payments", "job_items",
        "printing_items", "paper_items", "binding_items", "lamination_items", "ctp_items",
        "invoice_job_mapping", "invoice_additional_charges", "invoice_adjustments",
        "payment_allocations", "payment_details", "suppliers", "company_details",
        "bank_details", "hsn_sac_master", "document_number_mappings"
    );

    public static int getPendingRecordsCount() {
        int total = 0;
        try (Connection conn = DBConnection.getConnection()) {
            for (String table : SYNCABLE_BUSINESS_TABLES) {
                boolean hasSyncStatus = false;
                boolean hasIsDeleted = false;
                
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
                    query += PendingSyncFilters.PENDING_STATUS;
                    
                    try (Statement countStmt = conn.createStatement();
                         ResultSet countRs = countStmt.executeQuery(query)) {
                        if (countRs.next()) {
                            int c = countRs.getInt(1);
                            if (c > 0) {
                                System.out.println("[PendingSyncCounter] Table '" + table + "' has " + c + " pending records.");
                            }
                            total += c;
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
