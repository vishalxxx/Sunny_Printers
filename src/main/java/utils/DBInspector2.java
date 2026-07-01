package utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DBInspector2 {
    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection()) {
            System.out.println("--- Invoice Mapping Record ---");
            String invoiceUuid = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM invoice_master WHERE invoice_no = 'TEMP-PI-0047'")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    invoiceUuid = rs.getString("uuid");
                }
            }
            
            if (invoiceUuid != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM invoice_job_mapping WHERE invoice_uuid = ?")) {
                    ps.setString(1, invoiceUuid);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        System.out.println("Job UUID mapped: " + rs.getString("job_uuid"));
                        System.out.println("Is Deleted: " + rs.getInt("is_deleted"));
                        
                        try (PreparedStatement ps2 = conn.prepareStatement("SELECT status FROM jobs WHERE uuid = ?")) {
                            ps2.setString(1, rs.getString("job_uuid"));
                            ResultSet rs2 = ps2.executeQuery();
                            if (rs2.next()) {
                                System.out.println("Job Status: " + rs2.getString("status"));
                            } else {
                                System.out.println("Job DOES NOT EXIST in jobs table!");
                            }
                        }
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
