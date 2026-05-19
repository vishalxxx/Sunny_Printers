package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import model.Supplier;
import utils.DBConnection;

public class SupplierService {

	public void addSupplier(Supplier s) {
		String sql = """
				    INSERT INTO suppliers
				    (uuid, supplier_code, name, business_name, type, phone, address, gst_number, created_by_user_uuid, updated_by_user_uuid)
				    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

			stmt.executeUpdate();
			service.sync.UniversalSyncEngine.scheduleSyncAsync();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<Supplier> getSuppliersByType(String type) {

		List<Supplier> list = new ArrayList<>();

		String sql = """
				    SELECT *
				    FROM suppliers
				    WHERE type = ?
				    ORDER BY business_name
				""";

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

				list.add(s);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return list;
	}

	public List<Supplier> getAllSuppliers() {
		List<Supplier> list = new ArrayList<>();
		String sql = """
				    SELECT *
				    FROM suppliers
				    ORDER BY business_name
				""";

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
				    SET supplier_code = ?, name = ?, business_name = ?, type = ?, phone = ?, address = ?, gst_number = ?, updated_by_user_uuid = ?
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
			stmt.setString(9, s.getUuid());
			stmt.executeUpdate();
			service.sync.UniversalSyncEngine.scheduleSyncAsync();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void deleteSupplier(String uuid) {
		String sql = "DELETE FROM suppliers WHERE uuid = ?";
		try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, uuid);
			stmt.executeUpdate();
			service.sync.UniversalSyncEngine.scheduleSyncAsync();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
