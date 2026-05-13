package model;

/**
 * Optional cloud database (Supabase) connection stored locally in SQLite.
 * Used for future sync / backup features; REST probe uses URL + anon key.
 */
public class SupabaseSettings {
	private String supabaseUrl = "";
	private String anonKey = "";
	private String authEmail = "";
	private String authPassword = "";

	public String getSupabaseUrl() {
		return supabaseUrl;
	}

	public void setSupabaseUrl(String supabaseUrl) {
		this.supabaseUrl = supabaseUrl != null ? supabaseUrl.trim() : "";
	}

	public String getAnonKey() {
		return anonKey;
	}

	public void setAnonKey(String anonKey) {
		this.anonKey = anonKey != null ? anonKey.trim() : "";
	}

	public String getAuthEmail() {
		return authEmail;
	}

	public void setAuthEmail(String authEmail) {
		this.authEmail = authEmail != null ? authEmail.trim() : "";
	}

	public String getAuthPassword() {
		return authPassword;
	}

	public void setAuthPassword(String authPassword) {
		this.authPassword = authPassword != null ? authPassword : "";
	}
}
