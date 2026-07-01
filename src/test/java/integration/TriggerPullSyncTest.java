package integration;
import service.sync.RemoteToLocalSync;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import service.sync.UniversalSyncEngine;

@Tag("integration")
public class TriggerPullSyncTest {
	@Test
	public void runPullSync() throws Exception {
		System.out.println("Triggering Full Pull Sync...");
		UniversalSyncEngine.syncAllPending(); // First do any push if any
		api.supabase.SupabaseRestClient http = api.supabase.SupabaseGate.restClientIfConfigured().orElse(null);
		if (http != null) {
			int downloaded = service.sync.RemoteToLocalSync.pullAll(http);
			System.out.println("Pull Sync completed. Downloaded " + downloaded + " rows.");
		} else {
			System.out.println("Supabase client not configured.");
		}
	}
}

