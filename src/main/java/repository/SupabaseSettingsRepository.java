package repository;

import model.SupabaseSettings;
import utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SupabaseSettingsRepository {

	public SupabaseSettings load() throws Exception {
		SupabaseSettings s = new SupabaseSettings();
		try (Connection con = DBConnection.getConnection();
				PreparedStatement ps = con.prepareStatement("SELECT * FROM supabase_settings WHERE id = 1");
				ResultSet rs = ps.executeQuery()) {
			if (rs.next()) {
				s.setSupabaseUrl(rs.getString("supabase_url"));
				s.setAnonKey(rs.getString("anon_key"));
				s.setAuthEmail(rs.getString("auth_email"));
				s.setAuthPassword(rs.getString("auth_password"));
			}
		}
		return s;
	}

	public void save(SupabaseSettings s) throws Exception {
		String sql = """
				UPDATE supabase_settings SET
				  supabase_url = ?, anon_key = ?, auth_email = ?, auth_password = ?,
				  updated_at = CURRENT_TIMESTAMP
				WHERE id = 1
				""";
		try (Connection con = DBConnection.getConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, s.getSupabaseUrl());
			ps.setString(2, s.getAnonKey());
			ps.setString(3, s.getAuthEmail());
			ps.setString(4, s.getAuthPassword());
			ps.executeUpdate();
		}
	}
}
