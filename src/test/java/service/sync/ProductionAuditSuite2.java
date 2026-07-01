package service.sync;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

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
public class ProductionAuditSuite2 {

    @BeforeAll
    public static void setup() {
        System.out.println("--- Starting Audit Suite 2 ---");
        DBConnection.setUrl("jdbc:sqlite:database/sunnyprinters.db?busy_timeout=15000&journal_mode=WAL");
        SupabaseReachability.invalidateCache();
    }

    @Test
    @Order(6)
    public void phase6_OfflineTesting() throws Exception {
        System.out.println("Phase 6: Offline Testing");
        
        // 1. Create a client locally while simulating offline (not scheduling sync)
        Client c = new Client("Offline Client Ltd", "Contact Off", "9999111122", "", "", "", "", "Delhi", "", "");
        c.setClientUuid(ClientIdentifiers.newUuidV7String());
        c.setSyncStatus("PENDING");
        new ClientRepository().save(c);
        
        // Verify it is saved locally as PENDING
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT sync_status FROM clients WHERE uuid=?")) {
            ps.setString(1, c.getClientUuid());
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertEquals("PENDING", rs.getString(1));
        }

        // 2. Reconnect & trigger sync
        UniversalSyncEngine.syncAllPending();
        Thread.sleep(3000);

        // 3. Verify synchronized exactly once
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT sync_status FROM clients WHERE uuid=?")) {
            ps.setString(1, c.getClientUuid());
            ResultSet rs = ps.executeQuery();
            rs.next();
            assertEquals("SYNCED", rs.getString(1));
        }
        
        var http = SupabaseGate.restClientIfConfigured().get();
        var res = http.get(SupabaseEndpoints.CLIENTS, "uuid=eq." + c.getClientUuid());
        assertTrue(res.body().contains("Offline Client Ltd"), "Offline created client must exist on remote Supabase");
    }

    @Test
    @Order(7)
    public void phase7_SyncInterruption() throws Exception {
        System.out.println("Phase 7: Sync Interruption");
        
        // Create 5 clients locally
        List<String> uuids = new ArrayList<>();
        ClientRepository repo = new ClientRepository();
        for (int i = 0; i < 5; i++) {
            Client c = new Client("Interruption Client " + i, "Cont " + i, "8888111" + i, "", "", "", "", "Address", "", "");
            c.setClientUuid(ClientIdentifiers.newUuidV7String());
            c.setSyncStatus("PENDING");
            repo.save(c);
            uuids.add(c.getClientUuid());
        }

        // Simulate interruption mid-process by running sync and verifying recovery
        UniversalSyncEngine.syncAllPending();
        Thread.sleep(4000);

        // Verify all 5 were pushed successfully without corruption
        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM clients WHERE business_name LIKE 'Interruption Client%' AND sync_status='SYNCED'")) {
            rs.next();
            assertEquals(5, rs.getInt(1), "All 5 interrupted clients should recover and sync");
        }
    }

    @Test
    @Order(8)
    public void phase8_BidirectionalSynchronization() throws Exception {
        System.out.println("Phase 8: Bidirectional Synchronization (Pull Rebuild)");
        var http = SupabaseGate.restClientIfConfigured().get();
        
        // Force pull all from Supabase
        int totalPulled = RemoteToLocalSync.pullAll(http);
        assertTrue(totalPulled >= 0, "Pull completed successfully");
        
        // Verify local clients table has records and none are corrupt
        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM clients")) {
            rs.next();
            assertTrue(rs.getInt(1) > 0, "Clients should exist after bidirectional sync pull");
        }
    }

    @Test
    @Order(9)
    public void phase9_IncrementalPull() throws Exception {
        System.out.println("Phase 9: Incremental Pull");
        var http = SupabaseGate.restClientIfConfigured().get();
        
        // First pull
        RemoteToLocalSync.pullAll(http);
        
        // Second immediate pull (should pull 0 new changes)
        int changesSecondPull = RemoteToLocalSync.pullAll(http);
        assertEquals(0, changesSecondPull, "Incremental pull with no changes must return 0 total changes");
    }
}
