package utils;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Human-readable {@code CL-} + 6 alphanumeric client codes and UUID v7 primary keys for clients.
 */
public final class ClientIdentifiers {

	public static final String CODE_PREFIX = "CL-";

	private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final SecureRandom RND = new SecureRandom();

	private ClientIdentifiers() {
	}

	/** New client primary key (RFC 9562 UUID version 7). */
	public static String newUuidString() {
		return newUuidV7String();
	}

	public static String newUuidV7String() {
		return uuidV7().toString();
	}

	public static UUID uuidV7() {
		long timestampMs = System.currentTimeMillis();
		long randA = RND.nextLong() & 0x0FFFL;
		long randB = RND.nextLong() & 0x3FFFFFFFFFFFFFFFL;
		long msb = (timestampMs << 16) | (0x7L << 12) | randA;
		long lsb = 0x8000000000000000L | randB;
		return new UUID(msb, lsb);
	}

	public static String randomAlphanumeric6() {
		StringBuilder sb = new StringBuilder(6);
		for (int i = 0; i < 6; i++) {
			sb.append(ALPHANUM.charAt(RND.nextInt(ALPHANUM.length())));
		}
		return sb.toString();
	}

	public static String formatClientCode(String six) {
		if (six == null || six.length() != 6) {
			throw new IllegalArgumentException("suffix must be 6 chars");
		}
		return CODE_PREFIX + six;
	}

	public static String allocateUniqueClientCode(Connection con) throws Exception {
		try {
			return new service.NumberSequenceAllocationService().allocateClientCode(con).value();
		} catch (Exception e) {
			System.err.println("Failed to allocate client_code from sequence service, falling back to random: " + e.getMessage());
			for (int attempt = 0; attempt < 64; attempt++) {
				String code = CODE_PREFIX + randomAlphanumeric6();
				if (!clientCodeInUse(con, code, null)) {
					return code;
				}
			}
			throw new IllegalStateException("Could not allocate unique client_code after 64 attempts");
		}
	}

	/**
	 * @param excludeClientUuid when non-blank, ignore that row (for edits that change {@code client_code})
	 */
	public static boolean clientCodeInUse(Connection con, String code, String excludeClientUuid) throws Exception {
		if (code == null || code.isBlank()) {
			return false;
		}
		String c = code.trim();
		String ex = excludeClientUuid != null ? excludeClientUuid.trim() : "";
		String sql = !ex.isBlank()
				? "SELECT 1 FROM clients WHERE client_code = ? AND uuid <> ? LIMIT 1"
				: "SELECT 1 FROM clients WHERE client_code = ? LIMIT 1";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, c);
			if (!ex.isBlank()) {
				ps.setString(2, ex);
			}
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private static boolean clientCodeExists(Connection con, String code) throws Exception {
		return clientCodeInUse(con, code, null);
	}
}
