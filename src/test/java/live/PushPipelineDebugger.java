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
        SupabaseGate.setOverrideClient(new SupabaseRestClient(s.getSupabaseUrl(), s.getAnonKey()));
        UniversalSyncEngine.syncAllPending();
        System.out.println("Push audit complete.");
    }
}

