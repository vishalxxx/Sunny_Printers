package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import model.Supplier;
import model.User;
import utils.DBConnection;
import api.supabase.SupabaseGate;
import api.supabase.SupabaseEndpoints;
import api.supabase.SupabaseRestClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import utils.SessionManager;

public class SupplierService {

	public void addSupplier(Supplier s) {
		String sql = """
				    INSERT INTO suppliers
				    (uuid, supplier_code, name, business_name, type, phone, address, gst_number, created_by_user_uuid, updated_by_user_uuid,
				     mobile, email, website, state, city, pincode, payment_terms, credit_limit, notes)
				    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""";

		try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

			if (s.getUuid() == null || s.getUuid().isBlank()) {
				s.setUuid(java.util.UUID.randomUUID().toString());
			}
			String code = s.getSupplierCode();
			if (code == null || code.isBlank() || "SUP-NEW".equals(code) || code.startsWith("TEMP-")) {
				try {
					service.sync.UniversalNumberAllocator seqAlloc = service.sync.UniversalNumberAllocator.getInstance();
					service.NumberSequenceAllocationService.AllocatedNumber allocated = seqAlloc.allocateSupplierCode(conn);
					code = allocated.value();
				} catch (Exception ex) {
					System.err.println("Failed to allocate supplier_code: " + ex.getMessage());
					code = "SUP-" + (System.currentTimeMillis() % 100000);
				}
				s.setSupplierCode(code);
			}
			stmt.setString(1, s.getUuid());
			stmt.setString(2, code);
			stmt.setString(3, s.getName());
			stmt.setString(4, s.getbusinessName()); // 🔥 REQUIRED
			stmt.setString(5, s.getType());
			stmt.setString(6, s.getPhone());
			stmt.setString(7, s.getAddress());
			stmt.setString(8, s.getGstNumber());
			
			String userUuid = null;
			if (utils.SessionManager.getInstance().getCurrentUser() != null) {
			    userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
			}
			stmt.setString(9, userUuid);
			stmt.setString(10, userUuid);
			stmt.setString(11, s.getMobile());
			stmt.setString(12, s.getEmail());
			stmt.setString(13, s.getWebsite());
			stmt.setString(14, s.getState());
			stmt.setString(15, s.getCity());
			stmt.setString(16, s.getPincode());
			stmt.setString(17, s.getPaymentTerms());
			stmt.setDouble(18, s.getCreditLimit());
			stmt.setString(19, s.getNotes());

			stmt.executeUpdate();
			service.sync.UniversalSyncEngine.scheduleSyncAsync();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<Supplier> getSuppliersByType(String type) {

		List<Supplier> list = new ArrayList<>();
		User current = SessionManager.getInstance().getCurrentUser();
		boolean isAdmin = current != null && current.getRole() != null && "ADMIN".equalsIgnoreCase(current.getRole());

		String sql;
		if (isAdmin) {
			sql = """
				    SELECT *
				    FROM suppliers
				    WHERE type = ?
				    ORDER BY business_name
				""";
		} else {
			sql = """
				    SELECT *
				    FROM suppliers
				    WHERE type = ? AND is_deleted = 0
				    ORDER BY business_name
				""";
		}

		try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

			stmt.setString(1, type);

			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				Supplier s = new Supplier();
				s.setUuid(rs.getString("uuid"));
				s.setSupplierCode(rs.getString("supplier_code"));
				s.setName(rs.getString("name"));
				s.setbusinessName(rs.getString("business_name")); // 🔥 FIX
				s.setType(rs.getString("type"));
				s.setPhone(rs.getString("phone"));
				s.setAddress(rs.getString("address"));
				s.setGstNumber(rs.getString("gst_number"));
				s.setCreatedByUserUuid(rs.getString("created_by_user_uuid"));
				s.setUpdatedByUserUuid(rs.getString("updated_by_user_uuid"));
				s.setDeleted(rs.getInt("is_deleted") == 1);
				s.setActive(rs.getInt("is_active") == 1);
				s.setMobile(rs.getString("mobile"));
				s.setEmail(rs.getString("email"));
				s.setWebsite(rs.getString("website"));
				s.setState(rs.getString("state"));
				s.setCity(rs.getString("city"));
				s.setPincode(rs.getString("pincode"));
				s.setPaymentTerms(rs.getString("payment_terms"));
				s.setCreditLimit(rs.getDouble("credit_limit"));
				s.setNotes(rs.getString("notes"));

				list.add(s);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return list;
	}

	public List<Supplier> getAllSuppliers() {
		List<Supplier> list = new ArrayList<>();
		User current = SessionManager.getInstance().getCurrentUser();
		boolean isAdmin = current != null && current.getRole() != null && "ADMIN".equalsIgnoreCase(current.getRole());

		String sql;
		if (isAdmin) {
			sql = """
				    SELECT *
				    FROM suppliers
				    ORDER BY business_name
				""";
		} else {
			sql = """
				    SELECT *
				    FROM suppliers
				    WHERE is_deleted = 0
				    ORDER BY business_name
				""";
		}

		try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				Supplier s = new Supplier();
				s.setUuid(rs.getString("uuid"));
				s.setSupplierCode(rs.getString("supplier_code"));
				s.setName(rs.getString("name"));
				s.setbusinessName(rs.getString("business_name"));
				s.setType(rs.getString("type"));
				s.setPhone(rs.getString("phone"));
				s.setAddress(rs.getString("address"));
				s.setGstNumber(rs.getString("gst_number"));
				s.setCreatedByUserUuid(rs.getString("created_by_user_uuid"));
				s.setUpdatedByUserUuid(rs.getString("updated_by_user_uuid"));
				s.setDeleted(rs.getInt("is_deleted") == 1);
				s.setActive(rs.getInt("is_active") == 1);
				s.setMobile(rs.getString("mobile"));
				s.setEmail(rs.getString("email"));
				s.setWebsite(rs.getString("website"));
				s.setState(rs.getString("state"));
				s.setCity(rs.getString("city"));
				s.setPincode(rs.getString("pincode"));
				s.setPaymentTerms(rs.getString("payment_terms"));
				s.setCreditLimit(rs.getDouble("credit_limit"));
				s.setNotes(rs.getString("notes"));
				list.add(s);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return list;
	}

	public void updateSupplier(Supplier s) {
		String sql = """
				    UPDATE suppliers
				    SET supplier_code = ?, name = ?, business_name = ?, type = ?, phone = ?, address = ?, gst_number = ?, updated_by_user_uuid = ?,
				        mobile = ?, email = ?, website = ?, state = ?, city = ?, pincode = ?, payment_terms = ?, credit_limit = ?, notes = ?
				    WHERE uuid = ?
				""";

		try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, s.getSupplierCode());
			stmt.setString(2, s.getName());
			stmt.setString(3, s.getbusinessName());
			stmt.setString(4, s.getType());
			stmt.setString(5, s.getPhone());
			stmt.setString(6, s.getAddress());
			stmt.setString(7, s.getGstNumber());
			
			String userUuid = null;
			if (utils.SessionManager.getInstance().getCurrentUser() != null) {
			    userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
			}
			stmt.setString(8, userUuid);
			stmt.setString(9, s.getMobile());
			stmt.setString(10, s.getEmail());
			stmt.setString(11, s.getWebsite());
			stmt.setString(12, s.getState());
			stmt.setString(13, s.getCity());
			stmt.setString(14, s.getPincode());
			stmt.setString(15, s.getPaymentTerms());
			stmt.setDouble(16, s.getCreditLimit());
			stmt.setString(17, s.getNotes());
			stmt.setString(18, s.getUuid());
			stmt.executeUpdate();
			service.sync.UniversalSyncEngine.scheduleSyncAsync();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void deleteSupplier(String uuid) {
		if (uuid == null || uuid.isBlank()) {
			return;
		}
		String sql = "UPDATE suppliers SET is_deleted = 1, is_active = 0, deleted_at = datetime('now'), sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
		try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, uuid);
			stmt.executeUpdate();
			service.sync.UniversalSyncEngine.scheduleSyncAsync();
		} catch (Exception e) {
			e.printStackTrace();
		}
		SupabaseGate.restClientIfConfigured().ifPresent(http -> {
			Runnable task = () -> {
				try {
					JsonObject body = new JsonObject();
					body.addProperty("uuid", uuid.trim());
					body.addProperty("is_deleted", 1);
					body.addProperty("is_active", 0);
					body.addProperty("sync_status", "SYNCED");
					body.addProperty("synced_at", Instant.now().toString());
					body.addProperty("deleted_at", Instant.now().toString());
					String v = URLEncoder.encode(uuid.trim(), StandardCharsets.UTF_8).replace("+", "%20");
					http.patchJson(SupabaseEndpoints.SUPPLIERS, "uuid=eq." + v, body.toString(), "return=minimal");
				} catch (Exception ex) {
					System.err.println("[Supabase suppliers] remote soft-delete failed for uuid=" + uuid + ": " + ex.getMessage());
				}
			};
			if (SupabaseGate.isOverrideActive()) {
				task.run();
			} else {
				CompletableFuture.runAsync(task);
			}
		});
	}

	public void reviveSupplier(String uuid) {
		if (uuid == null || uuid.isBlank()) {
			return;
		}
		User current = SessionManager.getInstance().getCurrentUser();
		boolean isAdmin = current != null && current.getRole() != null && "ADMIN".equalsIgnoreCase(current.getRole());
		if (isAdmin) {
			String sql = "UPDATE suppliers SET is_deleted = 0, is_active = 1, deleted_at = NULL, sync_status = 'PENDING', updated_at = datetime('now') WHERE uuid = ?";
			try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setString(1, uuid);
				stmt.executeUpdate();
				service.sync.UniversalSyncEngine.scheduleSyncAsync();
			} catch (Exception e) {
				e.printStackTrace();
			}
			SupabaseGate.restClientIfConfigured().ifPresent(http -> {
				Runnable task = () -> {
					try {
						JsonObject body = new JsonObject();
						body.addProperty("uuid", uuid.trim());
						body.addProperty("is_deleted", 0);
						body.addProperty("is_active", 1);
						body.addProperty("sync_status", "SYNCED");
						body.addProperty("synced_at", Instant.now().toString());
						body.add("deleted_at", JsonNull.INSTANCE);
						String v = URLEncoder.encode(uuid.trim(), StandardCharsets.UTF_8).replace("+", "%20");
						http.patchJson(SupabaseEndpoints.SUPPLIERS, "uuid=eq." + v, body.toString(), "return=minimal");
					} catch (Exception ex) {
						System.err.println("[Supabase suppliers] remote revive failed for uuid=" + uuid + ": " + ex.getMessage());
					}
				};
				if (SupabaseGate.isOverrideActive()) {
					task.run();
				} else {
					CompletableFuture.runAsync(task);
				}
			});
		}
	}

	public boolean duplicateGstinExists(String gstin, String excludeUuid) {
		if (gstin == null || gstin.trim().isBlank()) {
			return false;
		}
		String sql = "SELECT 1 FROM suppliers WHERE gst_number = ? AND uuid <> ? AND IFNULL(is_deleted,0)=0 LIMIT 1";
		try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, gstin.trim());
			stmt.setString(2, excludeUuid != null ? excludeUuid.trim() : "");
			try (ResultSet rs = stmt.executeQuery()) {
				return rs.next();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean duplicateMobileExists(String mobile, String excludeUuid) {
		if (mobile == null || mobile.trim().isBlank()) {
			return false;
		}
		String sql = "SELECT 1 FROM suppliers WHERE mobile = ? AND uuid <> ? AND IFNULL(is_deleted,0)=0 LIMIT 1";
		try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, mobile.trim());
			stmt.setString(2, excludeUuid != null ? excludeUuid.trim() : "");
			try (ResultSet rs = stmt.executeQuery()) {
				return rs.next();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
}
