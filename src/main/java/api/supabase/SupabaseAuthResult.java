package api.supabase;

/** Result of Supabase Auth sign-in or sign-up. */
public record SupabaseAuthResult(
		boolean success,
		String accessToken,
		String refreshToken,
		String userId,
		String email,
		String message) {

	public static SupabaseAuthResult ok(String accessToken, String refreshToken, String userId, String email) {
		return new SupabaseAuthResult(true, accessToken, refreshToken, userId, email, null);
	}

	public static SupabaseAuthResult fail(String message) {
		return new SupabaseAuthResult(false, null, null, null, null, message);
	}
}