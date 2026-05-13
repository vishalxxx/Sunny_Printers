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
				    (name, business_name, type, phone, address, gst_number)
				    VALUES (?, ?, ?, ?, ?, ?)
				""";

		try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

			stmt.setString(1, s.getName());
			stmt.setString(2, s.getbusinessName()); // 🔥 REQUIRED
			stmt.setString(3, s.getType());
			stmt.setString(4, s.getPhone());
			stmt.setString(5, s.getAddress());
			stmt.setString(6, s.getGstNumber());

			stmt.executeUpdate();

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
				s.setId(rs.getInt("id"));
				s.setName(rs.getString("name"));
				s.setbusinessName(rs.getString("business_name")); // 🔥 FIX
				s.setType(rs.getString("type"));
				s.setPhone(rs.getString("phone"));
				s.setAddress(rs.getString("address"));
				s.setGstNumber(rs.getString("gst_number"));

				list.add(s);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return list;
	}
}
