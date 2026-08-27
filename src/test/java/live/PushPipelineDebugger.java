package live;
import service.sync.UniversalSyncEngine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseRestClient;
import utils.TestEnvironment;

@Tag("live")
public class PushPipelineDebugger {
    @Test
    public void runPushTest() throws Exception {
        System.out.println("Starting push audit...");

        // Load TEST credentials — never use production
        TestEnvironment.load();
        if (!TestEnvironment.isSupabaseConfigured()) {
            System.out.println("TEST Supabase credentials not found. Skipping live push test.");
            return;
        }

        String url = TestEnvironment.getTestSupabaseUrl();
        String key = TestEnvironment.getTestSupabaseKey();
        TestEnvironment.logContext();

        SupabaseGate.setOverrideClient(new SupabaseRestClient(url, key));
        try {
            UniversalSyncEngine.syncAllPending();
            System.out.println("Push audit complete.");
        } finally {
            SupabaseGate.setOverrideClient(null);
        }
    }
}
