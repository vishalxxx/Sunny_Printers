package utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DBInspector3 {
    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection()) {
            System.out.println("--- Checking Nulls in Mapping Table ---");
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM invoice_job_mapping WHERE invoice_uuid IS NULL")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    System.out.println("Null invoice_uuid count: " + rs.getInt(1));
                }
            }
            
            System.out.println("--- Testing NOT IN Query ---");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid FROM invoice_master WHERE uuid NOT IN (SELECT invoice_uuid FROM invoice_job_mapping) AND status = 'DRAFT' AND invoice_no = 'TEMP-PI-0047'")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    System.out.println("NOT IN matched the invoice!");
                } else {
                    System.out.println("NOT IN failed to match!");
                }
            }
            
            System.out.println("--- Testing IS NOT NULL NOT IN Query ---");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid FROM invoice_master WHERE uuid NOT IN (SELECT invoice_uuid FROM invoice_job_mapping WHERE invoice_uuid IS NOT NULL) AND status = 'DRAFT' AND invoice_no = 'TEMP-PI-0047'")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    System.out.println("Fixed NOT IN matched the invoice!");
                } else {
                    System.out.println("Fixed NOT IN failed to match!");
                }
            }

            System.out.println("Done.");
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
