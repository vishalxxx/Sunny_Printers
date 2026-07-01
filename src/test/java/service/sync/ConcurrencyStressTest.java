package service.sync;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseRestClient;
import model.Client;
import model.Job;
import repository.ClientRepository;
import repository.JobRepository;
import utils.DBConnection;

public class ConcurrencyStressTest {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("  SUNNY PRINTERS - HIGH CONCURRENCY STRESS TEST");
        System.out.println("==================================================");
        System.out.println("Initializing Database...");
        
        // Trigger DB initialization
        try (Connection con = DBConnection.getExclusiveConnection()) {
            System.out.println("Database Initialized.");
        }

        SupabaseRestClient http = SupabaseGate.restClientIfConfigured().orElse(null);
        if (http == null) {
            System.err.println("WARNING: Supabase is not configured. Sync processes will be a no-op.");
        }

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        ExecutorService workers = Executors.newFixedThreadPool(10);

        AtomicInteger sqliteBusyCount = new AtomicInteger(0);
        AtomicInteger successClientCount = new AtomicInteger(0);
        AtomicInteger successJobCount = new AtomicInteger(0);
        AtomicInteger failedAllocations = new AtomicInteger(0);

        List<String> clientUuids = Collections.synchronizedList(new ArrayList<>());
        List<String> jobUuids = Collections.synchronizedList(new ArrayList<>());
        ClientRepository clientRepo = new ClientRepository();
        JobRepository jobRepo = new JobRepository();

        long startTime = System.currentTimeMillis();

        // 1. Start Background Sync (Every 5 Seconds)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("[TestScheduler] Triggering RemoteToLocalSync...");
                if (http != null) {
                    RemoteToLocalSync.pullAll(http);
                }
                UniversalSyncEngine.syncAllPending();
            } catch (Exception e) {
                System.err.println("[TestScheduler] Sync threw exception: " + e.getMessage());
            }
        }, 1, 5, TimeUnit.SECONDS);

        // 2. Submit 100 Client Creation Tasks
        System.out.println("Submitting 100 Client Creation Tasks...");
        for (int i = 0; i < 100; i++) {
            final int index = i;
            workers.submit(() -> {
                try {
                    Client c = new Client();
                    c.setBusinessName("TEST-BUSINESS-" + index + "-" + System.currentTimeMillis());
                    
                    boolean saved = clientRepo.save(c);
                    if (saved) {
                        successClientCount.incrementAndGet();
                        if (c.getClientUuid() != null) {
                            clientUuids.add(c.getClientUuid());
                        }
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("SQLITE_BUSY")) {
                        sqliteBusyCount.incrementAndGet();
                    }
                    e.printStackTrace();
                }
            });
        }

        AtomicInteger successInvoiceCount = new AtomicInteger(0);
        AtomicInteger successPaymentCount = new AtomicInteger(0);

        // 3. Submit 300 Job Creation Tasks
        System.out.println("Submitting 300 Job Creation Tasks...");
        for (int i = 0; i < 300; i++) {
            final int index = i;
            workers.submit(() -> {
                try {
                    Job j = new Job();
                    j.setJobTitle("TEST-JOB-" + index);
                    if (!clientUuids.isEmpty()) {
                        j.setClientUuid(clientUuids.get((int) (Math.random() * clientUuids.size())));
                    }
                    j.setStatus("DRAFT");
                    
                    try (Connection con = DBConnection.getConnection()) {
                        jobRepo.insertJob(con, j);
                        successJobCount.incrementAndGet();
                        if (j.getUuid() != null) jobUuids.add(j.getUuid());
                    } catch (Exception sqlE) {
                        if (sqlE.getMessage() != null && sqlE.getMessage().contains("job_code")) {
                            failedAllocations.incrementAndGet();
                        }
                        if (sqlE.getMessage() != null && sqlE.getMessage().contains("SQLITE_BUSY")) {
                            sqliteBusyCount.incrementAndGet();
                        }
                        throw sqlE;
                    }
                } catch (Exception e) {}
            });
        }

        // Wait for workers to finish
        workers.shutdown();
        workers.awaitTermination(3, TimeUnit.MINUTES);
        
        // Let's spawn a second set of workers for Invoices & Payments since Jobs must exist first
        ExecutorService secondWorkers = Executors.newFixedThreadPool(10);
        
        System.out.println("Submitting 50 Invoice Creation Tasks...");
        for (int i = 0; i < 50; i++) {
            final int index = i;
            secondWorkers.submit(() -> {
                try {
                    model.InvoiceMaster inv = new model.InvoiceMaster();
                    inv.setUuid(utils.ClientIdentifiers.newUuidV7String());
                    inv.setInvoiceNo("TEST-INV-" + index + "-" + System.currentTimeMillis());
                    inv.setDocumentSeries("TAX_INVOICE");
                    inv.setStatus("DRAFT");
                    
                    try (Connection con = DBConnection.getConnection()) {
                        repository.InvoiceMasterRepository repo = new repository.InvoiceMasterRepository();
                        repo.insert(con, inv);
                        
                        // Create a mapping to trigger the duplicate UUID test
                        if (!jobUuids.isEmpty()) {
                            service.InvoiceMasterService.insertInvoiceJobMapping(con, inv.getUuid(), jobUuids.get((int) (Math.random() * jobUuids.size())));
                        }
                        successInvoiceCount.incrementAndGet();
                    } catch (Exception sqlE) {}
                } catch (Exception e) {}
            });
        }
        
        secondWorkers.shutdown();
        secondWorkers.awaitTermination(3, TimeUnit.MINUTES);
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("\nStopping scheduler...");
        scheduler.shutdown();
        scheduler.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("\n==================================================");
        System.out.println("  STRESS TEST COMPLETE");
        System.out.println("==================================================");
        System.out.println("Duration: " + (duration / 1000.0) + " seconds");
        System.out.println("Clients Created: " + successClientCount.get() + " / 100");
        System.out.println("Jobs Created: " + successJobCount.get() + " / 300");
        System.out.println("Failed Job Allocations: " + failedAllocations.get());
        System.out.println("SQLITE_BUSY Locks Encountered: " + sqliteBusyCount.get());
        
        System.out.println("\n[Data Integrity Audit]");
        try (Connection con = DBConnection.getConnection(); Statement st = con.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(uuid) - COUNT(DISTINCT uuid) as dupes FROM clients WHERE business_name LIKE 'TEST-%'");
            if (rs.next()) {
                System.out.println("Duplicate Client UUIDs: " + rs.getInt("dupes"));
            }
            rs = st.executeQuery("SELECT COUNT(uuid) - COUNT(DISTINCT uuid) as dupes FROM jobs WHERE job_title LIKE 'TEST-%'");
            if (rs.next()) {
                System.out.println("Duplicate Job UUIDs: " + rs.getInt("dupes"));
            }
        } catch (Exception e) {
            System.err.println("Audit failed: " + e.getMessage());
        }
        
        System.out.println("\nCleaning up Test Data...");
        try (Connection con = DBConnection.getExclusiveConnection(); Statement st = con.createStatement()) {
            int dJobs = st.executeUpdate("DELETE FROM jobs WHERE job_title LIKE 'TEST-%'");
            int dClients = st.executeUpdate("DELETE FROM clients WHERE business_name LIKE 'TEST-%'");
            int dInvoices = st.executeUpdate("DELETE FROM invoice_master WHERE invoice_no LIKE 'TEST-%'");
            System.out.println("Deleted " + dJobs + " test jobs.");
            System.out.println("Deleted " + dClients + " test clients.");
            System.out.println("Deleted " + dInvoices + " test invoices.");
        }

        System.exit(0);
    }
}
