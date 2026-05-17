package service;

import java.util.Optional;

import api.supabase.SupabaseAuthResult;
import api.supabase.SupabaseAuthService;
import model.SupabaseSettings;
import model.User;
import repository.SupabaseSettingsRepository;
import repository.UserRepository;
import utils.SessionManager;

/**
 * Sign-in: Supabase Auth first (when configured), then local SQLite users. Sign-up: Supabase only.
 */
public class AuthService {

	private final SupabaseAuthService supabaseAuth = new SupabaseAuthService();
	private final UserRepository userRepository = new UserRepository();

	public enum AuthOutcome {
		SUCCESS, INVALID_CREDENTIALS, CONFIG_ERROR, NETWORK_ERROR
	}

	public record LoginResult(AuthOutcome outcome, String message) {
		public boolean isSuccess() {
			return outcome == AuthOutcome.SUCCESS;
		}
	}

	public LoginResult signIn(String loginId, String password) {
		if (loginId == null || loginId.isBlank()) {
			return new LoginResult(AuthOutcome.INVALID_CREDENTIALS, "Enter your username.");
		}
		if (password == null || password.isEmpty()) {
			return new LoginResult(AuthOutcome.INVALID_CREDENTIALS, "Enter your password.");
		}
		String id = loginId.trim();

		// 1. ALWAYS prioritize local SQLite login first for speed, robustness, and offline/initial setup support!
		LoginResult localResult = tryLocalLogin(id, password, null);
		if (localResult.isSuccess()) {
			return localResult;
		}

		// 2. Fall back to online Supabase cloud check ONLY if local sign-in failed (e.g. for new remote-registered cloud users)
		Optional<SupabaseSettings> settingsOpt = loadSupabaseSettings();
		if (settingsOpt.isPresent()) {
			SupabaseSettings s = settingsOpt.get();
			// Supabase signInWithPassword requires an email. Only execute if id looks like an email.
			if (id.contains("@")) {
				SupabaseAuthResult auth = supabaseAuth.signInWithPassword(
						s.getSupabaseUrl(), s.getAnonKey(), id, password);
				if (auth.success()) {
					completeSupabaseLogin(auth);
					return new LoginResult(AuthOutcome.SUCCESS, null);
				}
			}
		}

		return new LoginResult(AuthOutcome.INVALID_CREDENTIALS, "Invalid username or password.");
	}

	public LoginResult signUp(String email, String password, String confirmPassword) {
		if (email == null || !email.contains("@")) {
			return new LoginResult(AuthOutcome.INVALID_CREDENTIALS, "Enter a valid email address.");
		}
		if (password == null || password.length() < 6) {
			return new LoginResult(AuthOutcome.INVALID_CREDENTIALS, "Password must be at least 6 characters.");
		}
		if (!password.equals(confirmPassword)) {
			return new LoginResult(AuthOutcome.INVALID_CREDENTIALS, "Passwords do not match.");
		}
		Optional<SupabaseSettings> settingsOpt = loadSupabaseSettings();
		if (settingsOpt.isEmpty()) {
			return new LoginResult(AuthOutcome.CONFIG_ERROR,
					"Configure Supabase URL and anon key in General Settings first.");
		}
		SupabaseSettings s = settingsOpt.get();
		SupabaseAuthResult auth = supabaseAuth.signUp(s.getSupabaseUrl(), s.getAnonKey(), email.trim(), password);
		if (auth.success()) {
			if (auth.accessToken() != null && !auth.accessToken().isBlank()) {
				completeSupabaseLogin(auth);
				return new LoginResult(AuthOutcome.SUCCESS, "Account created and signed in.");
			}
			return new LoginResult(AuthOutcome.SUCCESS,
					auth.message() != null ? auth.message()
							: "Account created. Check your email to confirm, then sign in.");
		}
		return new LoginResult(AuthOutcome.INVALID_CREDENTIALS,
				auth.message() != null ? auth.message() : "Sign up failed");
	}

	private LoginResult tryLocalLogin(String loginId, String password, String successNote) {
		try {
			User user = userRepository.authenticate(loginId, password);
			if (user != null) {
				SessionManager.getInstance().loginLocal(user);
				if (successNote != null) {
					return new LoginResult(AuthOutcome.SUCCESS, successNote);
				}
				return new LoginResult(AuthOutcome.SUCCESS, null);
			}
		} catch (Exception e) {
			return new LoginResult(AuthOutcome.NETWORK_ERROR, e.getMessage());
		}
		return new LoginResult(AuthOutcome.INVALID_CREDENTIALS, "Invalid username or password.");
	}

	private void completeSupabaseLogin(SupabaseAuthResult auth) {
		User user = new User();
		String email = auth.email() != null ? auth.email() : "";
		user.setUsername(email);
		user.setRole("USER");
		SessionManager.getInstance().loginWithSupabase(user, auth.accessToken(), auth.refreshToken(),
				auth.userId(), email);
	}

	private static Optional<SupabaseSettings> loadSupabaseSettings() {
		try {
			SupabaseSettings s = new SupabaseSettingsRepository().load();
			if (s.getSupabaseUrl() == null || s.getSupabaseUrl().isBlank()
					|| s.getAnonKey() == null || s.getAnonKey().isBlank()) {
				return Optional.empty();
			}
			return Optional.of(s);
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private static boolean looksLikeNetwork(String msg) {
		String m = msg.toLowerCase();
		return m.contains("timed out") || m.contains("connect") || m.contains("unknown host")
				|| m.contains("network");
	}
}