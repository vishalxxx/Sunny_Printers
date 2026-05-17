package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import model.User;
import utils.DBConnection;

public class UserRepository {
	

	public User findByUsername(String username) throws Exception {
		String sql = "SELECT uuid, username, password, role FROM users WHERE username = ? COLLATE NOCASE";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, username);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return mapUser(rs);
				}
			}
		}
		return null;
	}

	/** Local offline login (SQLite {@code users} table). */
	public User authenticate(String loginId, String password) throws Exception {
		if (loginId == null || loginId.isBlank() || password == null) {
			return null;
		}
		User user = findByUsername(loginId.trim());
		if (user == null) {
			return null;
		}
		String stored = user.getPassword();
		if (stored == null) {
			return null;
		}
		return stored.equals(password) ? user : null;
	}

	public List<User> findAll() throws Exception {
		List<User> list = new ArrayList<>();
		String sql = "SELECT uuid, username, password, role, sync_status FROM users";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(mapUser(rs));
			}
		}
		return list;
	}

	public void create(User user) throws Exception {
		String sql = "INSERT INTO users (uuid, username, password, role, sync_status, sync_version, is_active, is_deleted) VALUES (?, ?, ?, ?, ?, 1, 1, 0)";
		try (Connection conn = DBConnection.getConnection();
				PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, user.getUuid());
			ps.setString(2, user.getUsername());
			ps.setString(3, user.getPassword());
			ps.setString(4, user.getRole());
			ps.setString(5, user.getSyncStatus() != null ? user.getSyncStatus() : "PENDING"); // Sync engine will pick up PENDING
			ps.executeUpdate();
		}
	}

	private static User mapUser(ResultSet rs) throws SQLException {
		User user = new User();
		try {
			user.setUuid(rs.getString("uuid"));
		} catch (SQLException ignored) {
		}
		try {
			user.setId(rs.getInt("id"));
		} catch (SQLException ignored) {
		}
		user.setUsername(rs.getString("username"));

		user.setPassword(rs.getString("password"));
		user.setRole(rs.getString("role"));
		try {
			user.setSyncStatus(rs.getString("sync_status"));
		} catch (SQLException ignored) {
		}
		return user;
	}
}

