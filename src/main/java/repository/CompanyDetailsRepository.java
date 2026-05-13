package repository;

import model.CompanyDetails;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CompanyDetailsRepository {

	public List<CompanyDetails> listAll(Connection con, boolean includeInactive) throws Exception {
		String sql = """
				SELECT id, trade_name, address, phone, alt_phone, email, gstin, state, is_default, is_active
				FROM company_details
				""" + (includeInactive ? "" : " WHERE is_active = 1 ") + """
				ORDER BY is_default DESC, trade_name ASC, id ASC
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
				SELECT id, trade_name, address, phone, alt_phone, email, gstin, state, is_default, is_active
				FROM company_details
				WHERE is_active = 1 AND is_default = 1
				ORDER BY id ASC
				LIMIT 1
				""";
		try (PreparedStatement ps = con.prepareStatement(sql);
			 ResultSet rs = ps.executeQuery()) {
			return rs.next() ? map(rs) : null;
		}
	}

	public int insert(Connection con, CompanyDetails c) throws Exception {
		String sql = """
				INSERT INTO company_details (trade_name, address, phone, alt_phone, email, gstin, state, is_default, is_active)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""";
		try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, safe(c.getTradeName()));
			ps.setString(2, safe(c.getAddress()));
			ps.setString(3, safe(c.getPhone()));
			ps.setString(4, safe(c.getAltPhone()));
			ps.setString(5, safe(c.getEmail()));
			ps.setString(6, safe(c.getGstin()));
			ps.setString(7, safe(c.getState()));
			ps.setInt(8, c.isDefault() ? 1 : 0);
			ps.setInt(9, c.isActive() ? 1 : 0);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) return keys.getInt(1);
			}
		}
		return 0;
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
				    is_active = ?
				WHERE id = ?
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
			ps.setInt(10, c.getId());
			ps.executeUpdate();
		}
	}

	public void delete(Connection con, int id) throws Exception {
		try (PreparedStatement ps = con.prepareStatement("DELETE FROM company_details WHERE id = ?")) {
			ps.setInt(1, id);
			ps.executeUpdate();
		}
	}

	public void clearDefault(Connection con) throws Exception {
		try (PreparedStatement ps = con.prepareStatement("UPDATE company_details SET is_default = 0 WHERE is_default = 1")) {
			ps.executeUpdate();
		}
	}

	public void setDefault(Connection con, int companyId) throws Exception {
		clearDefault(con);
		try (PreparedStatement ps = con.prepareStatement("UPDATE company_details SET is_default = 1 WHERE id = ?")) {
			ps.setInt(1, companyId);
			ps.executeUpdate();
		}
	}

	private static CompanyDetails map(ResultSet rs) throws Exception {
		CompanyDetails c = new CompanyDetails();
		c.setId(rs.getInt("id"));
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

