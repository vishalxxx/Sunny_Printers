package api.supabase;

import model.SupabaseSettings;
import repository.SupabaseSettingsRepository;

/**
 * Builds {@link SupabaseRestClient} instances from local SQLite settings.
 */
public final class SupabaseClients {

	private SupabaseClients() {
	}

	public static SupabaseRestClient fromSettings(SupabaseSettings s) {
		return fromSettings(s, null);
	}

	public static SupabaseRestClient fromSettings(SupabaseSettings s, String userAccessToken) {
		if (s == null) {
			throw new IllegalArgumentException("settings is null");
		}
		return new SupabaseRestClient(s.getSupabaseUrl(), s.getAnonKey(), userAccessToken);
	}

	/** Loads row {@code id=1} from {@code supabase_settings} via JDBC. */
	public static SupabaseRestClient fromLocalDatabase() throws Exception {
		SupabaseSettingsRepository repo = new SupabaseSettingsRepository();
		return fromSettings(repo.load());
	}
}
