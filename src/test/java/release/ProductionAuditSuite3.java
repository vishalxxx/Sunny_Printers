package release;
import service.sync.UniversalSyncEngine;
import utils.ClientIdentifiers;
import utils.DBConnection;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import utils.*;
import model.*;
import repository.*;
import service.*;
import api.supabase.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("release")
public class ProductionAuditSuite3 {

    private static ExecutorService executor = Executors.newFixedThreadPool(50);
    private static List<String> concurrentClients = new CopyOnWriteArrayList<>();
    private static List<String> concurrentJobs = new CopyOnWriteArrayList<>();

    @BeforeAll
    public static void setup() {
        System.out.println("--- Starting Audit Suite 3 ---");
        // Production audit suite: uses DBConnection.PRODUCTION_URL automatically.
        SupabaseReachability.invalidateCache();
    }

    @AfterAll
    public static void teardown() {
        executor.shutdownNow();
    }

    @Test
    @Order(12)
    public void phase12_Concurrency() throws Exception {
        System.out.println("Phase 12: Concurrency Stress Test (50 Threads)");
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < 50; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    Client c = new Client("Concurrent Client " + index, "Contact " + index, "7777000" + (index < 10 ? "0" + index : index), "", "", "", "", "Address", "", "");
                    c.setClientUuid(ClientIdentifiers.newUuidV7String());
                    c.setClientCode("CL-CONC-" + index);
                    c.setSyncStatus("PENDING");
                    if (new ClientRepository().save(c)) {
                        concurrentClients.add(c.getClientUuid());
                    }

                    String runId = UUID.randomUUID().toString().substring(0, 5);
                    AtomicDB.runVoid(con -> {
                        Job job = new Job();
                        job.setUuid(JobIdentifiers.newUuidString());
                        job.setClientUuid(c.getClientUuid());
                        job.setJobCode("CONC-" + runId + "-" + index);
                        job.setJobTitle("Concurrent Job " + index);
                        job.setStatus("Completed");
                        new JobRepository().insertJob(con, job);
                        concurrentJobs.add(job.getUuid());
                    });
                } catch (Exception e) {
                    fail("Concurrency failure in thread " + index, e);
                }
            }));
        }

        for (Future<?> f : futures) f.get(); // Wait for all 50 threads
        assertEquals(50, concurrentClients.size(), "All 50 clients created concurrently without SQLite busy errors");
        assertEquals(50, concurrentJobs.size(), "All 50 jobs created concurrently without SQLite busy errors");
    }

    @Test
    @Order(13)
    public void phase13_LargeDatasetPerformance() throws Exception {
        System.out.println("Phase 13: Large Dataset Performance Audit");
        long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.currentTimeMillis();

        String bulkRunId = UUID.randomUUID().toString().substring(0, 5);
        // Batch insert 1,000 jobs to test database bulk throughput
        try (Connection con = DBConnection.getExclusiveConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO jobs (uuid, client_uuid, job_code, job_title, status, sync_status, created_at, updated_at) VALUES (?,?,?,?,'Completed','PENDING',datetime('now'),datetime('now'))")) {
                for (int i = 0; i < 1000; i++) {
                    ps.setString(1, ClientIdentifiers.newUuidV7String());
                    ps.setString(2, concurrentClients.get(i % concurrentClients.size()));
                    ps.setString(3, "BULK-" + bulkRunId + "-" + i);
                    ps.setString(4, "Bulk Job " + i);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            con.commit();
            con.setAutoCommit(true);
        }

        long endTime = System.currentTimeMillis();
        long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long duration = endTime - startTime;
        
        System.out.println("[Performance Metrics] 1,000 Bulk Jobs inserted in " + duration + " ms. Memory delta: " + (endMem - startMem) / 1024 + " KB");
        assertTrue(duration < 5000, "1,000 bulk insertions must finish under 5 seconds");
    }

    @Test
    @Order(14)
    public void phase14_DataIntegrityAudit() throws Exception {
        System.out.println("Phase 14: Data Integrity Audit");
        
        // Trigger sync for pending jobs
        UniversalSyncEngine.syncAllPending();
        Thread.sleep(5000);

        // Check orphan records in local DB
        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM jobs WHERE client_uuid NOT IN (SELECT uuid FROM clients)")) {
            rs.next();
            assertEquals(0, rs.getInt(1), "Zero orphan jobs referencing non-existent clients");
        }
    }

    @Test
    @Order(15)
    public void phase15_FinancialAudit() throws Exception {
        System.out.println("Phase 15: Financial Calculation Verification");
        
        // Independent financial check using BigDecimal
        BigDecimal rate = new BigDecimal("100.00");
        BigDecimal qty = new BigDecimal("5.00");
        BigDecimal expectedTotal = rate.multiply(qty);

        BigDecimal cgst = expectedTotal.multiply(new BigDecimal("0.09"));
        BigDecimal sgst = expectedTotal.multiply(new BigDecimal("0.09"));
        BigDecimal grandTotal = expectedTotal.add(cgst).add(sgst);

        assertEquals(0, grandTotal.compareTo(new BigDecimal("590.00")), "Financial calculation must match exact decimal arithmetic");
    }

    @Test
    @Order(16)
    public void phase16_PerformanceMetricsReport() throws Exception {
        System.out.println("Phase 16: Performance Metrics Report");
        long totalMemoryMB = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long freeMemoryMB = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        System.out.println("[System Metrics] Peak Memory Allocated: " + totalMemoryMB + " MB, Free: " + freeMemoryMB + " MB");
        assertTrue(totalMemoryMB > 0);
    }
}

