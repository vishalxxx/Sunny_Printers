package api.supabase;

import java.util.Optional;

import model.SupabaseSettings;
import repository.SupabaseSettingsRepository;
import utils.SessionManager;

/**
 * Resolves a PostgREST client when General Settings has a non-empty Supabase URL and anon key.
 */
public final class SupabaseGate {

	private static SupabaseRestClient overrideClient = null;

	public static void setOverrideClient(SupabaseRestClient client) {
		overrideClient = client;
	}

	private SupabaseGate() {
	}

	public static Optional<SupabaseRestClient> restClientIfConfigured() {
		if (overrideClient != null) {
			return Optional.of(overrideClient);
		}
		try {
			SupabaseSettings s = new SupabaseSettingsRepository().load();
			String url = s.getSupabaseUrl();
			String key = s.getAnonKey();
			if (url == null || url.isBlank() || key == null || key.isBlank()) {
				return Optional.empty();
			}
			String bearer = SessionManager.getInstance().getAccessToken().orElse(null);
			return Optional.of(SupabaseClients.fromSettings(s, bearer));
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}