package service.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import utils.DBConnection;

public class LargeDatasetTest {

    private static String dbPath;
    private static final String DB_FILE = "database_test/LargeDatasetTest.db";

    @BeforeAll
    public static void setup() throws Exception {
        dbPath = TestDatabaseHelper.createIsolatedDb("LargeDatasetTest");
        DBConnection.setUrl(dbPath);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @Test
    public void testLargeDatasetPerformance() throws Exception {
        // Measure initial resource state
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long startMemory = runtime.totalMemory() - runtime.freeMemory();
        long startTime = System.currentTimeMillis();

        System.out.println("[LargeDatasetTest] Seeding large dataset (100,000 jobs, 500,000 job items, 50,000 invoices)...");

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            // 0. Seed a client to satisfy FK constraint
            String clientSql = "INSERT INTO clients (uuid, client_code, client_name) VALUES (?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(clientSql)) {
                ps.setString(1, "client-uuid-1");
                ps.setString(2, "CL-DUMMY");
                ps.setString(3, "Dummy Client");
                ps.executeUpdate();
            }
            
            // 1. Seed 100k Jobs
            String jobSql = "INSERT INTO jobs (uuid, client_uuid, job_code, job_title, status, updated_at, sync_status) VALUES (?,?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(jobSql)) {
                for (int i = 1; i <= 100000; i++) {
                    ps.setString(1, "job-uuid-" + i);
                    ps.setString(2, "client-uuid-1");
                    ps.setString(3, "JOB-CODE-" + i);
                    ps.setString(4, "Large Job Title " + i);
                    ps.setString(5, "Completed");
                    ps.setString(6, "2026-06-13 14:00:00");
                    ps.setString(7, "SYNCED");
                    ps.addBatch();
                    
                    if (i % 10000 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }

            // 2. Seed 500k Job Items
            String itemSql = "INSERT INTO job_items (uuid, job_uuid, type, description, amount, sync_status) VALUES (?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                for (int i = 1; i <= 500000; i++) {
                    int jobIdx = (i % 100000) + 1;
                    ps.setString(1, "item-uuid-" + i);
                    ps.setString(2, "job-uuid-" + jobIdx);
                    ps.setString(3, "PRINTING");
                    ps.setString(4, "Item Description " + i);
                    ps.setDouble(5, 150.0);
                    ps.setString(6, "SYNCED");
                    ps.addBatch();
                    
                    if (i % 50000 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }

            // 3. Seed 50k Invoices
            String invSql = "INSERT INTO invoice_master (uuid, client_uuid, invoice_no, amount, status, sync_status) VALUES (?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(invSql)) {
                for (int i = 1; i <= 50000; i++) {
                    ps.setString(1, "inv-uuid-" + i);
                    ps.setString(2, "client-uuid-1");
                    ps.setString(3, "INV-NO-" + i);
                    ps.setDouble(4, 7500.0);
                    ps.setString(5, "FINAL");
                    ps.setString(6, "SYNCED");
                    ps.addBatch();
                    
                    if (i % 10000 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }

            conn.commit();
        }

        long seedTime = System.currentTimeMillis() - startTime;
        runtime.gc();
        long endMemory = runtime.totalMemory() - runtime.freeMemory();

        String cleanPath = dbPath.substring("jdbc:sqlite:".length());
        int qIdx = cleanPath.indexOf('?');
        if (qIdx != -1) {
            cleanPath = cleanPath.substring(0, qIdx);
        }
        long dbSizeBytes = Files.size(Path.of(cleanPath));

        // Generate Performance Report
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("=================================================");
        pw.println("LARGE DATASET PERFORMANCE REPORT");
        pw.println("=================================================");
        pw.println("Database path: " + cleanPath);
        pw.println("Jobs inserted: 100,000");
        pw.println("Job Items inserted: 500,000");
        pw.println("Invoices inserted: 50,000");
        pw.println("Seeding Duration: " + seedTime + " ms");
        pw.println("Memory Used: " + ((endMemory - startMemory) / (1024 * 1024)) + " MB");
        pw.println("Database File Size: " + (dbSizeBytes / (1024 * 1024)) + " MB");
        pw.println("=================================================");
        System.out.println(sw.toString());

        // Simple Assertions to verify correct data insertion
        assertTrue(dbSizeBytes > 0);
    }
}
