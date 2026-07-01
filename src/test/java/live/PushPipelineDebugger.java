package live;
import service.sync.UniversalSyncEngine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import api.supabase.SupabaseGate;
import api.supabase.SupabaseRestClient;
import repository.SupabaseSettingsRepository;
import model.SupabaseSettings;

@Tag("live")
public class PushPipelineDebugger {
    @Test
    public void runPushTest() throws Exception {
        System.out.println("Starting push audit...");
        SupabaseSettings s = new SupabaseSettingsRepository().load();
        String url = s.getSupabaseUrl();
        String key = s.getAnonKey();
        if (url == null || url.isBlank()) {
            url = System.getenv("SUPABASE_URL");
        }
        if (key == null || key.isBlank()) {
            key = System.getenv("SUPABASE_KEY");
        }
        
        if (url == null || url.isBlank()) {
            System.out.println("Supabase credentials not found. Skipping live push test.");
            return;
        }
        
        SupabaseGate.setOverrideClient(new SupabaseRestClient(url, key));
        UniversalSyncEngine.syncAllPending();
        System.out.println("Push audit complete.");
    }
}

