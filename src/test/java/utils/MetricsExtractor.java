package utils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import utils.DBConnection;

public class MetricsExtractor {
    public static void main(String[] args) {
        try (Connection con = DBConnection.getConnection(); Statement st = con.createStatement()) {
            
            ResultSet rsClients = st.executeQuery("SELECT COUNT(*) FROM clients WHERE business_name LIKE 'TEST-%'");
            int clients = rsClients.next() ? rsClients.getInt(1) : 0;
            
            ResultSet rsJobs = st.executeQuery("SELECT COUNT(*) FROM jobs WHERE job_title LIKE 'TEST-%'");
            int jobs = rsJobs.next() ? rsJobs.getInt(1) : 0;
            
            ResultSet rsDupClients = st.executeQuery("SELECT SUM(c) FROM (SELECT COUNT(uuid)-1 as c FROM clients WHERE business_name LIKE 'TEST-%' GROUP BY uuid HAVING COUNT(uuid) > 1)");
            int dupClients = rsDupClients.next() ? rsDupClients.getInt(1) : 0;
            
            ResultSet rsDupJobs = st.executeQuery("SELECT SUM(c) FROM (SELECT COUNT(uuid)-1 as c FROM jobs WHERE job_title LIKE 'TEST-%' GROUP BY uuid HAVING COUNT(uuid) > 1)");
            int dupJobs = rsDupJobs.next() ? rsDupJobs.getInt(1) : 0;

            System.out.println("STRESS TEST DATABASE METRICS:");
            System.out.println("Clients Created: " + clients + " / 100");
            System.out.println("Jobs Created: " + jobs + " / 300");
            System.out.println("Duplicate Client UUIDs: " + dupClients);
            System.out.println("Duplicate Job UUIDs: " + dupJobs);
            
            // Clean up test data
            st.executeUpdate("DELETE FROM jobs WHERE job_title LIKE 'TEST-%'");
            st.executeUpdate("DELETE FROM clients WHERE business_name LIKE 'TEST-%'");
            System.out.println("Test Data Cleaned Successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

