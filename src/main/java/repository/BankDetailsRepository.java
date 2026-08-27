package repository;

import model.BankDetails;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class BankDetailsRepository {

	public List<BankDetails> listAll(Connection con, boolean includeInactive) throws Exception {
		String sql = """
				SELECT uuid, bank_name, account_holder_name, account_no,
				       branch_ifsc,
				       COALESCE(branch_name, '') AS branch_name,
				       COALESCE(ifsc_code, '') AS ifsc_code,
				       is_default, is_active
				FROM bank_details
				WHERE IFNULL(is_deleted, 0) = 0
				""" + (includeInactive ? "" : " AND is_active = 1 ") + """
				ORDER BY is_default DESC, bank_name ASC, uuid ASC
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
				SELECT uuid, bank_name, account_holder_name, account_no,
				       branch_ifsc,
				       COALESCE(branch_name, '') AS branch_name,
				       COALESCE(ifsc_code, '') AS ifsc_code,
				       is_default, is_active
				FROM bank_details
				WHERE is_active = 1 AND is_default = 1 AND IFNULL(is_deleted, 0) = 0
				ORDER BY uuid ASC
				LIMIT 1
				""";
		try (PreparedStatement ps = con.prepareStatement(sql);
			 ResultSet rs = ps.executeQuery()) {
			return rs.next() ? map(rs) : null;
		}
	}

	public String insert(Connection con, BankDetails b) throws Exception {
		String uuid = b.getUuid();
		if (uuid == null || uuid.isBlank()) {
			uuid = java.util.UUID.randomUUID().toString();
			b.setUuid(uuid);
		}
		String sql = """
				INSERT INTO bank_details (uuid, bank_name, account_holder_name, account_no, branch_ifsc, branch_name, ifsc_code, is_default, is_active, sync_status, sync_version, is_deleted, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 1, 0, datetime('now'), datetime('now'))
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, uuid);
			ps.setString(2, safe(b.getBankName()));
			ps.setString(3, safe(b.getAccountHolderName()));
			ps.setString(4, safe(b.getAccountNo()));
			ps.setString(5, safe(b.getBranchIfsc()));
			ps.setString(6, safe(b.getBranchName()));
			ps.setString(7, safe(b.getIfscCode()));
			ps.setInt(8, b.isDefault() ? 1 : 0);
			ps.setInt(9, b.isActive() ? 1 : 0);
			int rows = ps.executeUpdate();
			if (rows > 0) {
				service.sync.UniversalSyncEngine.scheduleSyncAsync();
			}
			return uuid;
		}
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
				    is_active = ?,
				    sync_status = 'PENDING',
				    sync_version = sync_version + 1,
				    updated_at = datetime('now')
				WHERE uuid = ?
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
			ps.setString(9, b.getUuid());
			ps.executeUpdate();
			service.sync.UniversalSyncEngine.scheduleSyncAsync();
		}
	}

	public void delete(Connection con, String uuid) throws Exception {
		if (uuid == null || uuid.isBlank()) return;
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE bank_details SET is_deleted = 1, is_active = 0, deleted_at = datetime('now'), sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?")) {
			ps.setString(1, uuid.trim());
			ps.executeUpdate();
		}
		service.sync.UniversalSyncEngine.scheduleSyncAsync();

		final String finalUuid = uuid.trim();
		api.supabase.SupabaseGate.restClientIfConfigured().ifPresent(http -> {
			Runnable task = () -> {
				try {
					String v = java.net.URLEncoder.encode(finalUuid, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
					com.google.gson.JsonObject body = new com.google.gson.JsonObject();
					body.addProperty("uuid", finalUuid);
					body.addProperty("is_deleted", 1);
					body.addProperty("is_active", 0);
					body.addProperty("sync_status", "SYNCED");
					body.addProperty("synced_at", java.time.Instant.now().toString());
					body.addProperty("deleted_at", java.time.Instant.now().toString());
					http.patchJson(api.supabase.SupabaseEndpoints.BANK_DETAILS, "uuid=eq." + v, body.toString(), "return=minimal");
				} catch (Exception ex) {
					System.err.println("[Supabase bank_details] remote soft-delete failed for uuid=" + finalUuid + ": " + ex.getMessage());
				}
			};
			if (api.supabase.SupabaseGate.isOverrideActive()) {
				task.run();
			} else {
				java.util.concurrent.CompletableFuture.runAsync(task);
			}
		});
	}

	public void clearDefault(Connection con) throws Exception {
		try (PreparedStatement ps = con.prepareStatement("UPDATE bank_details SET is_default = 0, sync_status = 'PENDING', updated_at = datetime('now') WHERE is_default = 1")) {
			ps.executeUpdate();
		}
	}

	public void setDefault(Connection con, String uuid) throws Exception {
		if (uuid == null || uuid.isBlank()) return;
		clearDefault(con);
		try (PreparedStatement ps = con.prepareStatement("UPDATE bank_details SET is_default = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?")) {
			ps.setString(1, uuid.trim());
			ps.executeUpdate();
			service.sync.UniversalSyncEngine.scheduleSyncAsync();
		}
	}

	private static BankDetails map(ResultSet rs) throws Exception {
		BankDetails b = new BankDetails();
		b.setUuid(rs.getString("uuid"));
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
