package api.supabase;

import java.util.Optional;

import model.SupabaseSettings;
import repository.SupabaseSettingsRepository;
import utils.SessionManager;

/**
 * Resolves a PostgREST client when General Settings has a non-empty Supabase URL and anon key.
 */
public final class SupabaseGate {

	private static final ThreadLocal<SupabaseRestClient> overrideClient = new ThreadLocal<>();

	public static void setOverrideClient(SupabaseRestClient client) {
		if (client == null) {
			overrideClient.remove();
		} else {
			overrideClient.set(client);
		}
	}

	public static boolean isOverrideActive() {
		return overrideClient.get() != null;
	}

	private SupabaseGate() {
	}

	public static Optional<SupabaseRestClient> restClientIfConfigured() {
		SupabaseRestClient override = overrideClient.get();
		if (override != null) {
			return Optional.of(override);
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