package integration;


import org.junit.jupiter.api.Tag;
import utils.FakeSupabaseRestClient;
import utils.TestDatabaseHelper;
import utils.CleanupUtility;
import utils.ClearAllExceptSettings;
import utils.ClearRemoteDatabase;
import utils.MetricsExtractor;
import utils.SchemaAndSyncChecker;
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

@Tag("integration")
public class UpdateCheckIntegrationTest {

    private static FakeSupabaseRestClient fakeSupabase;

    @BeforeAll
    public static void setup() {
        fakeSupabase = new FakeSupabaseRestClient();
        SupabaseGate.setOverrideClient(fakeSupabase);
    }

    private String localVer;

    @BeforeEach
    public void resetFake() {
        fakeSupabase.clear();
        SupabaseReachability.invalidateCache();
        UpdateService updateService = new UpdateService();
        updateService.resetCache();
        updateService.setIgnoredVersion("");
        updateService.setPreferredChannel("stable");
        localVer = updateService.getLocalVersion();
    }

    private String incrementVersion(String version, int patchIncrement) {
        String[] parts = version.split("\\.");
        if (parts.length < 3) {
            return version + "." + patchIncrement;
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2].split("-")[0]);
            return major + "." + minor + "." + (patch + patchIncrement);
        } catch (NumberFormatException e) {
            return version + "." + patchIncrement;
        }
    }

    @Test
    public void testUpdateServiceLocalPropertiesReading() {
        UpdateService updateService = new UpdateService();
        String localVersion = updateService.getLocalVersion();
        assertNotNull(localVersion);
        
        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream in = UpdateService.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            fail("Failed to load version.properties for test verification: " + e.getMessage());
        }
        String expectedVersion = props.getProperty("version", "1.0.0").trim();
        assertEquals(expectedVersion, localVersion);
    }

    @Test
    public void testSemanticVersionSelectionIgnoresInsertionOrder() {
        UpdateService updateService = new UpdateService();

        String vA = incrementVersion(localVer, 1);
        String vB = incrementVersion(localVer, 3);
        String vC = incrementVersion(localVer, 2);

        // Seed 3 rows in different version order:
        // Candidate A: older
        com.google.gson.JsonObject updateObjA = createValidUpdateJson(vA, false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(updateObjA);

        // Candidate B: highest version
        com.google.gson.JsonObject updateObjB = createValidUpdateJson(vB, false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(updateObjB);

        // Candidate C: inserted after B, but semantic version is lower
        com.google.gson.JsonObject updateObjC = createValidUpdateJson(vC, false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(updateObjC);

        // Query: should select highest version semantically, ignoring insertion order
        UpdateService.UpdateCheckResult res = updateService.checkForUpdates(true);
        assertEquals(UpdateService.UpdateStatus.OPTIONAL_UPDATE, res.getStatus());
        assertNotNull(res.getUpdate());
        assertEquals(vB, res.getUpdate().getVersion());
    }

    @Test
    public void testMetadataValidationRejectsInvalidUpdates() {
        UpdateService updateService = new UpdateService();

        String vA = incrementVersion(localVer, 2);
        String vB = incrementVersion(localVer, 3);
        String vC = incrementVersion(localVer, 4);
        String vValid = incrementVersion(localVer, 1);

        // 1. Missing storage_path & download_url
        com.google.gson.JsonObject invalidA = createValidUpdateJson(vA, false);
        invalidA.addProperty("storage_path", "");
        invalidA.addProperty("download_url", "");
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(invalidA);

        // 2. Missing sha256
        com.google.gson.JsonObject invalidB = createValidUpdateJson(vB, false);
        invalidB.addProperty("sha256", "");
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(invalidB);

        // 3. Not published
        com.google.gson.JsonObject invalidC = createValidUpdateJson(vC, false);
        invalidC.addProperty("published", false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(invalidC);

        // 4. Valid update
        com.google.gson.JsonObject valid = createValidUpdateJson(vValid, false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(valid);

        UpdateService.UpdateCheckResult res = updateService.checkForUpdates(true);
        assertEquals(UpdateService.UpdateStatus.OPTIONAL_UPDATE, res.getStatus());
        assertEquals(vValid, res.getUpdate().getVersion(), "Invalid updates must be ignored and valid version resolved");
    }

    @Test
    public void testCachingBehavior() {
        UpdateService updateService = new UpdateService();

        String vValid = incrementVersion(localVer, 1);
        com.google.gson.JsonObject valid = createValidUpdateJson(vValid, false);
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
        
        String vBeta = incrementVersion(localVer, 5);
        String vStable = incrementVersion(localVer, 1);

        com.google.gson.JsonObject betaUpdate = createValidUpdateJson(vBeta, false);
        betaUpdate.addProperty("release_channel", "beta");
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(betaUpdate);

        com.google.gson.JsonObject stableUpdate = createValidUpdateJson(vStable, false);
        stableUpdate.addProperty("release_channel", "stable");
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(stableUpdate);

        // When channel is stable, should ignore beta and return stable version
        UpdateService.UpdateCheckResult resStable = updateService.checkForUpdates(true);
        assertEquals(vStable, resStable.getUpdate().getVersion());

        // Switch channel to beta
        fakeSupabase.clear();
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(betaUpdate);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(stableUpdate);
        updateService.setPreferredChannel("beta");

        UpdateService.UpdateCheckResult resBeta = updateService.checkForUpdates(true);
        assertEquals(vBeta, resBeta.getUpdate().getVersion());
    }

    @Test
    public void testIgnoreVersionPreference() {
        UpdateService updateService = new UpdateService();

        String vOpt = incrementVersion(localVer, 1);
        com.google.gson.JsonObject optionalUpdate = createValidUpdateJson(vOpt, false);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(optionalUpdate);

        // Check 1: Normal Optional Update
        UpdateService.UpdateCheckResult res1 = updateService.checkForUpdates(true);
        assertEquals(UpdateService.UpdateStatus.OPTIONAL_UPDATE, res1.getStatus());

        // Set to ignore version
        updateService.setIgnoredVersion(vOpt);

        // Check 2: Ignored version should result in NO_UPDATE
        UpdateService.UpdateCheckResult res2 = updateService.checkForUpdates(true);
        assertEquals(UpdateService.UpdateStatus.NO_UPDATE, res2.getStatus());

        // Check 3: Mandatory updates bypass ignore version
        fakeSupabase.clear();
        com.google.gson.JsonObject mandatoryUpdate = createValidUpdateJson(vOpt, true);
        fakeSupabase.getTableData(SupabaseEndpoints.APP_UPDATES).add(mandatoryUpdate);

        UpdateService.UpdateCheckResult res3 = updateService.checkForUpdates(true);
        assertEquals(UpdateService.UpdateStatus.MANDATORY_UPDATE, res3.getStatus(), "Mandatory updates must bypass ignore rules");
    }

    private com.google.gson.JsonObject createValidUpdateJson(String version, boolean mandatory) {
        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        obj.addProperty("version", version);
        obj.addProperty("minimum_supported_version", "1.0.0");
        obj.addProperty("storage_path", "/updates/releases/" + version);
        obj.addProperty("download_url", "https://github.com/owner/repo/releases/download/v" + version + "/sunny-printers-" + version + ".jar");
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


