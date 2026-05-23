package repository;

import model.BankDetails;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class BankDetailsRepository {

	public List<BankDetails> listAll(Connection con, boolean includeInactive) throws Exception {
		String sql = """
				SELECT id, bank_name, account_holder_name, account_no,
				       branch_ifsc,
				       COALESCE(branch_name, '') AS branch_name,
				       COALESCE(ifsc_code, '') AS ifsc_code,
				       is_default, is_active
				FROM bank_details
				""" + (includeInactive ? "" : " WHERE is_active = 1 ") + """
				ORDER BY is_default DESC, bank_name ASC, id ASC
				""";

		List<BankDetails> out = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(sql);
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				out.add(map(rs));
			}
		}
		return out;
	}

	public BankDetails findDefault(Connection con) throws Exception {
		String sql = """
				SELECT id, bank_name, account_holder_name, account_no,
				       branch_ifsc,
				       COALESCE(branch_name, '') AS branch_name,
				       COALESCE(ifsc_code, '') AS ifsc_code,
				       is_default, is_active
				FROM bank_details
				WHERE is_active = 1 AND is_default = 1
				ORDER BY id ASC
				LIMIT 1
				""";
		try (PreparedStatement ps = con.prepareStatement(sql);
			 ResultSet rs = ps.executeQuery()) {
			return rs.next() ? map(rs) : null;
		}
	}

	public int insert(Connection con, BankDetails b) throws Exception {
		String sql = """
				INSERT INTO bank_details (bank_name, account_holder_name, account_no, branch_ifsc, branch_name, ifsc_code, is_default, is_active)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""";
		try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, safe(b.getBankName()));
			ps.setString(2, safe(b.getAccountHolderName()));
			ps.setString(3, safe(b.getAccountNo()));
			ps.setString(4, safe(b.getBranchIfsc()));
			ps.setString(5, safe(b.getBranchName()));
			ps.setString(6, safe(b.getIfscCode()));
			ps.setInt(7, b.isDefault() ? 1 : 0);
			ps.setInt(8, b.isActive() ? 1 : 0);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					return keys.getInt(1);
				}
			}
		}
		return 0;
	}

	public void update(Connection con, BankDetails b) throws Exception {
		String sql = """
				UPDATE bank_details
				SET bank_name = ?,
				    account_holder_name = ?,
				    account_no = ?,
				    branch_ifsc = ?,
				    branch_name = ?,
				    ifsc_code = ?,
				    is_default = ?,
				    is_active = ?
				WHERE id = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, safe(b.getBankName()));
			ps.setString(2, safe(b.getAccountHolderName()));
			ps.setString(3, safe(b.getAccountNo()));
			ps.setString(4, safe(b.getBranchIfsc()));
			ps.setString(5, safe(b.getBranchName()));
			ps.setString(6, safe(b.getIfscCode()));
			ps.setInt(7, b.isDefault() ? 1 : 0);
			ps.setInt(8, b.isActive() ? 1 : 0);
			ps.setInt(9, b.getId());
			ps.executeUpdate();
		}
	}

	public void delete(Connection con, int id) throws Exception {
		String uuid = null;
		try (PreparedStatement ps = con.prepareStatement("SELECT uuid FROM bank_details WHERE id = ?")) {
			ps.setInt(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) uuid = rs.getString(1);
			}
		}

		model.User current = utils.SessionManager.getInstance().getCurrentUser();
		boolean isAdmin = current != null && current.getRole() != null && "ADMIN".equalsIgnoreCase(current.getRole());

		if (isAdmin) {
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM bank_details WHERE id = ?")) {
				ps.setInt(1, id);
				ps.executeUpdate();
			}
		} else {
			try (PreparedStatement ps = con.prepareStatement(
					"UPDATE bank_details SET is_deleted = 1, is_active = 0, deleted_at = datetime('now'), sync_status = 'PENDING', updated_at = datetime('now') WHERE id = ?")) {
				ps.setInt(1, id);
				ps.executeUpdate();
			}
		}
		service.sync.UniversalSyncEngine.scheduleSyncAsync();

		if (uuid != null && !uuid.isBlank()) {
			final String finalUuid = uuid;
			api.supabase.SupabaseGate.restClientIfConfigured().ifPresent(http -> java.util.concurrent.CompletableFuture.runAsync(() -> {
				try {
					String v = java.net.URLEncoder.encode(finalUuid.trim(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
					if (isAdmin) {
						http.delete(api.supabase.SupabaseEndpoints.BANK_DETAILS, "uuid=eq." + v);
					} else {
						com.google.gson.JsonObject body = new com.google.gson.JsonObject();
						body.addProperty("uuid", finalUuid.trim());
						body.addProperty("is_deleted", 1);
						body.addProperty("is_active", 0);
						body.addProperty("sync_status", "SYNCED");
						body.addProperty("synced_at", java.time.Instant.now().toString());
						body.addProperty("deleted_at", java.time.Instant.now().toString());
						http.patchJson(api.supabase.SupabaseEndpoints.BANK_DETAILS, "uuid=eq." + v, body.toString(), "return=minimal");
					}
				} catch (Exception ex) {
					System.err.println("[Supabase bank_details] remote delete/patch failed for id=" + id + ": " + ex.getMessage());
				}
			}));
		}
	}

	public void clearDefault(Connection con) throws Exception {
		try (PreparedStatement ps = con.prepareStatement("UPDATE bank_details SET is_default = 0 WHERE is_default = 1")) {
			ps.executeUpdate();
		}
	}

	public void setDefault(Connection con, int bankId) throws Exception {
		clearDefault(con);
		try (PreparedStatement ps = con.prepareStatement("UPDATE bank_details SET is_default = 1 WHERE id = ?")) {
			ps.setInt(1, bankId);
			ps.executeUpdate();
		}
	}

	private static BankDetails map(ResultSet rs) throws Exception {
		BankDetails b = new BankDetails();
		b.setId(rs.getInt("id"));
		b.setBankName(rs.getString("bank_name"));
		b.setAccountHolderName(rs.getString("account_holder_name"));
		b.setAccountNo(rs.getString("account_no"));
		b.setBranchName(rs.getString("branch_name"));
		b.setIfscCode(rs.getString("ifsc_code"));
		// fallback for older rows where new columns are empty
		if ((b.getBranchName() == null || b.getBranchName().isBlank())
				&& (b.getIfscCode() == null || b.getIfscCode().isBlank())) {
			b.setBranchIfsc(rs.getString("branch_ifsc"));
		}
		b.setDefault(rs.getInt("is_default") == 1);
		b.setActive(rs.getInt("is_active") == 1);
		return b;
	}

	private static String safe(String s) {
		return s == null ? "" : s.trim();
	}
}

