package utils;

import java.util.Optional;

import model.User;

public class SessionManager {

	private static SessionManager instance;
	private User currentUser;
	private String supabaseAccessToken;
	private String supabaseRefreshToken;
	private String supabaseUserId;
	private String authEmail;
	private boolean supabaseSession;

	private SessionManager() {
	}

	public static synchronized SessionManager getInstance() {
		if (instance == null) {
			instance = new SessionManager();
		}
		return instance;
	}

	/** Local SQLite user session (offline fallback). */
	public void loginLocal(User user) {
		this.currentUser = user;
		clearSupabaseTokens();
		this.supabaseSession = false;
	}

	/** Supabase Auth session; API calls use {@link #getAccessToken()}. */
	public void loginWithSupabase(User user, String accessToken, String refreshToken, String supabaseUserId,
			String email) {
		this.currentUser = user;
		this.supabaseAccessToken = accessToken;
		this.supabaseRefreshToken = refreshToken;
		this.supabaseUserId = supabaseUserId;
		this.authEmail = email;
		this.supabaseSession = accessToken != null && !accessToken.isBlank();
	}

	/** @deprecated use {@link #loginLocal} or {@link #loginWithSupabase} */
	public void login(User user) {
		loginLocal(user);
	}

	public void logout() {
		this.currentUser = null;
		clearSupabaseTokens();
		this.supabaseSession = false;
	}

	private void clearSupabaseTokens() {
		this.supabaseAccessToken = null;
		this.supabaseRefreshToken = null;
		this.supabaseUserId = null;
		this.authEmail = null;
	}

	public User getCurrentUser() {
		return currentUser;
	}

	public boolean isLoggedIn() {
		return currentUser != null;
	}

	public boolean isSupabaseSession() {
		return supabaseSession;
	}

	public Optional<String> getAccessToken() {
		if (supabaseAccessToken == null || supabaseAccessToken.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(supabaseAccessToken);
	}

	public String getAuthEmail() {
		return authEmail;
	}
}
