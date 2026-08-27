package repository;

import model.CompanyDetails;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class CompanyDetailsRepository {

	public List<CompanyDetails> listAll(Connection con, boolean includeInactive) throws Exception {
		String sql = """
				SELECT uuid, trade_name, address, phone, alt_phone, email, gstin, state, is_default, is_active
				FROM company_details
				WHERE IFNULL(is_deleted, 0) = 0
				""" + (includeInactive ? "" : " AND is_active = 1 ") + """
				ORDER BY is_default DESC, trade_name ASC, uuid ASC
				""";
		List<CompanyDetails> out = new ArrayList<>();
		try (PreparedStatement ps = con.prepareStatement(sql);
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				out.add(map(rs));
			}
		}
		return out;
	}

	public CompanyDetails findDefault(Connection con) throws Exception {
		String sql = """
				SELECT uuid, trade_name, address, phone, alt_phone, email, gstin, state, is_default, is_active
				FROM company_details
				WHERE is_active = 1 AND is_default = 1 AND IFNULL(is_deleted, 0) = 0
				ORDER BY uuid ASC
				LIMIT 1
				""";
		try (PreparedStatement ps = con.prepareStatement(sql);
			 ResultSet rs = ps.executeQuery()) {
			return rs.next() ? map(rs) : null;
		}
	}

	public String insert(Connection con, CompanyDetails c) throws Exception {
		String uuid = c.getUuid();
		if (uuid == null || uuid.isBlank()) {
			uuid = java.util.UUID.randomUUID().toString();
			c.setUuid(uuid);
		}
		String sql = """
				INSERT INTO company_details (uuid, trade_name, address, phone, alt_phone, email, gstin, state, is_default, is_active, sync_status, sync_version, is_deleted, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 1, 0, datetime('now'), datetime('now'))
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, uuid);
			ps.setString(2, safe(c.getTradeName()));
			ps.setString(3, safe(c.getAddress()));
			ps.setString(4, safe(c.getPhone()));
			ps.setString(5, safe(c.getAltPhone()));
			ps.setString(6, safe(c.getEmail()));
			ps.setString(7, safe(c.getGstin()));
			ps.setString(8, safe(c.getState()));
			ps.setInt(9, c.isDefault() ? 1 : 0);
			ps.setInt(10, c.isActive() ? 1 : 0);
			int rows = ps.executeUpdate();
			if (rows > 0) {
				service.sync.UniversalSyncEngine.scheduleSyncAsync();
			}
			return uuid;
		}
	}

	public void update(Connection con, CompanyDetails c) throws Exception {
		String sql = """
				UPDATE company_details
				SET trade_name = ?,
				    address = ?,
				    phone = ?,
				    alt_phone = ?,
				    email = ?,
				    gstin = ?,
				    state = ?,
				    is_default = ?,
				    is_active = ?,
				    sync_status = 'PENDING',
				    sync_version = sync_version + 1,
				    updated_at = datetime('now')
				WHERE uuid = ?
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, safe(c.getTradeName()));
			ps.setString(2, safe(c.getAddress()));
			ps.setString(3, safe(c.getPhone()));
			ps.setString(4, safe(c.getAltPhone()));
			ps.setString(5, safe(c.getEmail()));
			ps.setString(6, safe(c.getGstin()));
			ps.setString(7, safe(c.getState()));
			ps.setInt(8, c.isDefault() ? 1 : 0);
			ps.setInt(9, c.isActive() ? 1 : 0);
			ps.setString(10, c.getUuid());
			ps.executeUpdate();
			service.sync.UniversalSyncEngine.scheduleSyncAsync();
		}
	}

	public void delete(Connection con, String uuid) throws Exception {
		if (uuid == null || uuid.isBlank()) return;
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE company_details SET is_deleted = 1, is_active = 0, deleted_at = datetime('now'), sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?")) {
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
					http.patchJson(api.supabase.SupabaseEndpoints.COMPANY_DETAILS, "uuid=eq." + v, body.toString(), "return=minimal");
				} catch (Exception ex) {
					System.err.println("[Supabase company_details] remote soft-delete failed for uuid=" + finalUuid + ": " + ex.getMessage());
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
		try (PreparedStatement ps = con.prepareStatement("UPDATE company_details SET is_default = 0, sync_status = 'PENDING', updated_at = datetime('now') WHERE is_default = 1")) {
			ps.executeUpdate();
		}
	}

	public void setDefault(Connection con, String uuid) throws Exception {
		if (uuid == null || uuid.isBlank()) return;
		clearDefault(con);
		try (PreparedStatement ps = con.prepareStatement("UPDATE company_details SET is_default = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?")) {
			ps.setString(1, uuid.trim());
			ps.executeUpdate();
			service.sync.UniversalSyncEngine.scheduleSyncAsync();
		}
	}

	private static CompanyDetails map(ResultSet rs) throws Exception {
		CompanyDetails c = new CompanyDetails();
		c.setUuid(rs.getString("uuid"));
		c.setTradeName(rs.getString("trade_name"));
		c.setAddress(rs.getString("address"));
		c.setPhone(rs.getString("phone"));
		c.setAltPhone(rs.getString("alt_phone"));
		c.setEmail(rs.getString("email"));
		c.setGstin(rs.getString("gstin"));
		c.setState(rs.getString("state"));
		c.setDefault(rs.getInt("is_default") == 1);
		c.setActive(rs.getInt("is_active") == 1);
		return c;
	}

	private static String safe(String s) {
		return s == null ? "" : s.trim();
	}
}
