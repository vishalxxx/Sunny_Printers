package service.sync;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseGate;
import api.supabase.SupabaseReachability;
import model.AppUpdate;
import service.UpdateService;
import utils.VersionComparator;

public class UpdateCheckIntegrationTest {

    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() {
        fakeSupabase = new FakeSupabaseRestClient();
        SupabaseGate.setOverrideClient(fakeSupabase);
    }

    @BeforeEach
    public void resetFake() {
        fakeSupabase.clear();
        SupabaseReachability.invalidateCache();
        new UpdateService().resetCache();
        new UpdateService().setIgnoredVersion("");
        new UpdateService().setPreferredChannel("stable");
    }

    @Test
    public void testVersionComparatorSemanticOrdering() {
        assertEquals(0, VersionComparator.compare("1.0.0", "1.0.0"));
        assertEquals(0, VersionComparator.compare("2.1", "2.1.0"));

        assertTrue(VersionComparator.compare("1.0.0", "1.0.1") < 0);
        assertTrue(VersionComparator.compare("1.0.1", "1.0.10") < 0);
        assertTrue(VersionComparator.compare("1.0.10", "1.1.0") < 0);
        assertTrue(VersionComparator.compare("1.1.0", "2.0.0") < 0);

        assertTrue(VersionComparator.compare("1.0.1", "1.0.0") > 0);
        assertTrue(VersionComparator.compare("1.0.10", "1.0.1") > 0);
        assertTrue(VersionComparator.compare("1.1.0", "1.0.10") > 0);
        assertTrue(VersionComparator.compare("2.0.0", "1.1.0") > 0);

        assertEquals(0, VersionComparator.compare("1.0.0-beta", "1.0.0"));
        assertTrue(VersionComparator.compare("1.0.0-alpha", "1.0.1-beta") < 0);
    }

    @Test
    public void testUpdateServiceLocalPropertiesReading() {
        UpdateService updateService = new UpdateService();
        String localVersion = updateService.getLocalVersion();
        assertNotNull(localVersion);
        assertEquals("1.0.0", localVersion);
    }

    @Test
    public void testSemanticVersionSelectionIgnoresInsertionOrder() {
        UpdateService updateService = new UpdateService();

        // Seed 3 rows in different version order:
        // Candidate A: 1.0.5 (older)
        com.google.gson.JsonObject updateObjA = createValidUpdateJson("1.0.5", false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(updateObjA);

        // Candidate B: 1.1.0 (highest version)
        com.google.gson.JsonObject updateObjB = createValidUpdateJson("1.1.0", false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(updateObjB);

        // Candidate C: 1.0.10 (inserted after 1.1.0, but semantic version is lower)
        com.google.gson.JsonObject updateObjC = createValidUpdateJson("1.0.10", false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(updateObjC);

        // Query: should select 1.1.0 semantically, ignoring insertion order
        UpdateService.UpdateCheckResult res = updateService.checkForUpdates(true);
        assertEquals(UpdateService.UpdateStatus.OPTIONAL_UPDATE, res.getStatus());
        assertNotNull(res.getUpdate());
        assertEquals("1.1.0", res.getUpdate().getVersion());
    }

    @Test
    public void testMetadataValidationRejectsInvalidUpdates() {
        UpdateService updateService = new UpdateService();

        // 1. Missing storage_path
        com.google.gson.JsonObject invalidA = createValidUpdateJson("1.2.0", false);
        invalidA.addProperty("storage_path", "");
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(invalidA);

        // 2. Missing sha256
        com.google.gson.JsonObject invalidB = createValidUpdateJson("1.3.0", false);
        invalidB.addProperty("sha256", "");
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(invalidB);

        // 3. Not published
        com.google.gson.JsonObject invalidC = createValidUpdateJson("1.4.0", false);
        invalidC.addProperty("published", false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(invalidC);

        // 4. Valid update 1.1.0
        com.google.gson.JsonObject valid = createValidUpdateJson("1.1.0", false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(valid);

        UpdateService.UpdateCheckResult res = updateService.checkForUpdates(true);
        assertEquals(UpdateService.UpdateStatus.OPTIONAL_UPDATE, res.getStatus());
        assertEquals("1.1.0", res.getUpdate().getVersion(), "Invalid updates must be ignored and valid 1.1.0 resolved");
    }

    @Test
    public void testCachingBehavior() {
        UpdateService updateService = new UpdateService();

        com.google.gson.JsonObject valid = createValidUpdateJson("1.1.0", false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(valid);

        // Check 1: Cache Miss, calls API
        UpdateService.UpdateCheckResult res1 = updateService.checkForUpdates(false);
        assertEquals(UpdateService.UpdateStatus.OPTIONAL_UPDATE, res1.getStatus());

        // Clear mock DB so that if a network check occurs, it will find no updates
        fakeSupabase.clear();

        // Check 2: Cache Hit (within 24h), should NOT call API, returning cached result or NO_UPDATE
        UpdateService.UpdateCheckResult res2 = updateService.checkForUpdates(false);
        assertEquals(UpdateService.UpdateStatus.NO_UPDATE, res2.getStatus(), "Should hit cache and not query remote");

        // Check 3: Forced refresh bypasses cache, calls API and finds nothing
        UpdateService.UpdateCheckResult res3 = updateService.checkForUpdates(true);
        assertEquals(UpdateService.UpdateStatus.NO_UPDATE, res3.getStatus());
    }

    @Test
    public void testReleaseChannelIsolation() {
        UpdateService updateService = new UpdateService();

        // Set channel to stable
        updateService.setPreferredChannel("stable");
        
        com.google.gson.JsonObject betaUpdate = createValidUpdateJson("1.5.0", false);
        betaUpdate.addProperty("release_channel", "beta");
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(betaUpdate);

        com.google.gson.JsonObject stableUpdate = createValidUpdateJson("1.1.0", false);
        stableUpdate.addProperty("release_channel", "stable");
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(stableUpdate);

        // When channel is stable, should ignore beta (1.5.0) and return 1.1.0
        UpdateService.UpdateCheckResult resStable = updateService.checkForUpdates(true);
        assertEquals("1.1.0", resStable.getUpdate().getVersion());

        // Switch channel to beta
        fakeSupabase.clear();
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(betaUpdate);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(stableUpdate);
        updateService.setPreferredChannel("beta");

        UpdateService.UpdateCheckResult resBeta = updateService.checkForUpdates(true);
        assertEquals("1.5.0", resBeta.getUpdate().getVersion());
    }

    @Test
    public void testIgnoreVersionPreference() {
        UpdateService updateService = new UpdateService();

        com.google.gson.JsonObject optionalUpdate = createValidUpdateJson("1.1.0", false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(optionalUpdate);

        // Check 1: Normal Optional Update
        UpdateService.UpdateCheckResult res1 = updateService.checkForUpdates(true);
        assertEquals(UpdateService.UpdateStatus.OPTIONAL_UPDATE, res1.getStatus());

        // Set to ignore version 1.1.0
        updateService.setIgnoredVersion("1.1.0");

        // Check 2: Ignored version should result in NO_UPDATE
        UpdateService.UpdateCheckResult res2 = updateService.checkForUpdates(true);
        assertEquals(UpdateService.UpdateStatus.NO_UPDATE, res2.getStatus());

        // Check 3: Mandatory updates bypass ignore version
        fakeSupabase.clear();
        com.google.gson.JsonObject mandatoryUpdate = createValidUpdateJson("1.1.0", true);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(mandatoryUpdate);

        UpdateService.UpdateCheckResult res3 = updateService.checkForUpdates(true);
        assertEquals(UpdateService.UpdateStatus.MANDATORY_UPDATE, res3.getStatus(), "Mandatory updates must bypass ignore rules");
    }

    private com.google.gson.JsonObject createValidUpdateJson(String version, boolean mandatory) {
        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        obj.addProperty("version", version);
        obj.addProperty("minimum_supported_version", "1.0.0");
        obj.addProperty("storage_path", "/updates/releases/" + version);
        obj.addProperty("file_name", "sunny-printers-" + version + ".jar");
        obj.addProperty("file_size", 1048576L);
        obj.addProperty("sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        obj.addProperty("release_channel", "stable");
        obj.addProperty("published", true);
        obj.addProperty("mandatory", mandatory);
        obj.addProperty("created_at", "2026-06-30T12:00:00Z");
        return obj;
    }
}
