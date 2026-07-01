package service.sync;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;
import utils.DBConnection;

public class DuplicateSyncTest {

    private static String dbPath;
    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() throws Exception {
        dbPath = TestDatabaseHelper.createIsolatedDb("DuplicateSyncTest");
        fakeSupabase = new FakeSupabaseRestClient();
        SupabaseGate.setOverrideClient(fakeSupabase);
        DBConnection.setUrl(dbPath);
    }

    @AfterAll
    public static void tearDown() {
        TestDatabaseHelper.cleanupTestDir();
    }

    @BeforeEach
    public void resetFake() throws Exception {
        fakeSupabase.clear();
        SupabaseReachability.invalidateCache();
        
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM clients");
            stmt.execute("DELETE FROM jobs");
            stmt.execute("DELETE FROM invoice_master");
            stmt.execute("DELETE FROM invoice_job_mapping");
        }
    }

    @Test
    public void testRepeatedSyncCycleStaysStable() throws Exception {
        // Seed 1 client, 1 job, 1 invoice, and 1 mapping on Supabase
        JsonObject client = new JsonObject();
        client.addProperty("uuid", "c-1");
        client.addProperty("client_code", "CL-01");
        client.addProperty("client_name", "Test Client");
        client.addProperty("updated_at", "2026-06-13T10:00:00Z");
        fakeSupabase.getTableData(SupabaseEndpoints.CLIENTS).add(client);

        JsonObject job = new JsonObject();
        job.addProperty("uuid", "j-1");
        job.addProperty("client_uuid", "c-1");
        job.addProperty("job_code", "JOB-01");
        job.addProperty("job_title", "Test Job");
        job.addProperty("updated_at", "2026-06-13T10:00:00Z");
        fakeSupabase.getTableData(SupabaseEndpoints.JOBS).add(job);

        JsonObject invoice = new JsonObject();
        invoice.addProperty("uuid", "inv-1");
        invoice.addProperty("client_uuid", "c-1");
        invoice.addProperty("invoice_no", "INV-01");
        invoice.addProperty("updated_at", "2026-06-13T10:00:00Z");
        fakeSupabase.getTableData(SupabaseEndpoints.INVOICE_MASTER).add(invoice);

        JsonObject mapping = new JsonObject();
        mapping.addProperty("uuid", "m-1");
        mapping.addProperty("invoice_uuid", "inv-1");
        mapping.addProperty("job_uuid", "j-1");
        mapping.addProperty("updated_at", "2026-06-13T10:00:00Z");
        fakeSupabase.getTableData(SupabaseEndpoints.INVOICE_JOB_MAPPING).add(mapping);

        // Run sync pulls 50 times in a loop
        for (int i = 0; i < 50; i++) {
            RemoteToLocalSync.pullAll(fakeSupabase);
        }

        // Verify counts are exactly 1 across all tables (no duplicates created)
        assertEquals(1, getCount("clients"));
        assertEquals(1, getCount("jobs"));
        assertEquals(1, getCount("invoice_master"));
        assertEquals(1, getCount("invoice_job_mapping"));
    }

    private int getCount(String table) throws Exception {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
}
