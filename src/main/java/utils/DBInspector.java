package utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DBInspector {
    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection()) {
            System.out.println("--- Invoice Record ---");
            String invoiceUuid = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM invoice_master WHERE invoice_no = 'TEMP-PI-0047'")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    invoiceUuid = rs.getString("uuid");
                    System.out.println("UUID: " + invoiceUuid);
                    System.out.println("Status: " + rs.getString("status"));
                }
            }
            
            if (invoiceUuid != null) {
                System.out.println("\n--- Jobs attached to Invoice ---");
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM jobs WHERE invoice_uuid = ?")) {
                    ps.setString(1, invoiceUuid);
                    ResultSet rs = ps.executeQuery();
                    int count = 0;
                    while (rs.next()) {
                        System.out.println("Job UUID: " + rs.getString("uuid"));
                        System.out.println("Title: " + rs.getString("job_title"));
                        count++;
                    }
                    System.out.println("Total Jobs: " + count);
                }
                
                System.out.println("\n--- Invoice Custom Items ---");
                // The table might be invoice_items, let's try reading schema
                try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE '%item%'")) {
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        System.out.println("Table: " + rs.getString("name"));
                    }
                }
            } else {
                System.out.println("Invoice not found.");
            }
            System.out.println("Done.");
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
