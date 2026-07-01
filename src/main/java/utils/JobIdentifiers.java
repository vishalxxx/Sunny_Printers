package utils;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class JobIdentifiers {

	public static final String CODE_PREFIX = "JOB-";

	private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final SecureRandom RND = new SecureRandom();

	private JobIdentifiers() {
	}

	public static String newUuidString() {
		return ClientIdentifiers.newUuidV7String();
	}

	public static String randomAlphanumeric6() {
		StringBuilder sb = new StringBuilder(6);
		for (int i = 0; i < 6; i++) {
			sb.append(ALPHANUM.charAt(RND.nextInt(ALPHANUM.length())));
		}
		return sb.toString();
	}

	public static String allocateUniqueJobCode(Connection con) throws Exception {
		try {
			return new service.NumberSequenceAllocationService().allocateJobCode(con);
		} catch (Exception e) {
			System.err.println("Failed to allocate job_code from sequence service, falling back to random: " + e.getMessage());
			for (int attempt = 0; attempt < 64; attempt++) {
				String code = CODE_PREFIX + randomAlphanumeric6();
				if (!jobCodeExists(con, code)) {
					return code;
				}
			}
			throw new IllegalStateException("Could not allocate unique job_code after 64 attempts");
		}
	}

	public static String legacyJobCodeFromSqliteId(int sqliteId) {
		return CODE_PREFIX + sqliteId;
	}

	public static boolean jobCodeExists(Connection con, String code) throws Exception {
		if (code == null || code.isBlank()) {
			return false;
		}
		try (PreparedStatement ps = con.prepareStatement(
				"SELECT 1 FROM jobs WHERE job_code = ? LIMIT 1")) {
			ps.setString(1, code.trim());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}
}