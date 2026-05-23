package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import model.JobItem;
import utils.DBConnection;

public class JobItemRepository {

	public JobItem save(Connection con, JobItem item) {
		String uuid = (item.getUuid() == null || item.getUuid().isBlank()) 
		              ? utils.ClientIdentifiers.newUuidString() 
		              : item.getUuid();
		item.setUuid(uuid);

		String sql = """
				    INSERT INTO job_items (uuid, job_uuid, type, description, amount, sort_order, sync_status, created_at, updated_at)
				    VALUES (?, ?, ?, ?, ?, ?, 'PENDING', datetime('now'), datetime('now'))
				    ON CONFLICT(uuid) DO UPDATE SET
				        job_uuid = excluded.job_uuid,
				        type = excluded.type,
				        description = excluded.description,
				        amount = excluded.amount,
				        sort_order = excluded.sort_order,
				        sync_status = 'PENDING',
				        updated_at = datetime('now')
				""";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, uuid);
			ps.setString(2, item.getJobUuid());
			ps.setString(3, item.getType());
			ps.setString(4, item.getDescription());
			ps.setDouble(5, item.getAmount());
			ps.setInt(6, item.getSortOrder());
			ps.executeUpdate();
			JobRepository.syncAmountFromJobItems(con, item.getJobUuid());
			return item;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to save JobItem", e);
		}
	}

	public List<JobItem> findByJobUuid(String jobUuid) throws Exception {
		String sql = """
				    SELECT uuid, job_uuid, type, description, amount, sort_order, sync_status, sync_version, created_at, updated_at
				    FROM job_items
				    WHERE job_uuid = ? AND COALESCE(is_deleted, 0) = 0
				    ORDER BY sort_order, uuid
				""";
		List<JobItem> list = new ArrayList<>();
		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, jobUuid);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					JobItem item = new JobItem();
					item.setUuid(rs.getString("uuid"));
					item.setJobUuid(rs.getString("job_uuid"));
					item.setType(rs.getString("type"));
					item.setDescription(rs.getString("description"));
					item.setAmount(rs.getDouble("amount"));
					item.setSortOrder(rs.getInt("sort_order"));
					item.setSyncStatus(rs.getString("sync_status"));
					item.setSyncVersion(rs.getInt("sync_version"));
					item.setCreatedAt(rs.getString("created_at"));
					item.setUpdatedAt(rs.getString("updated_at"));
					list.add(item);
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to fetch JobItems", e);
		}
		return list;
	}

	public void updateBaseItem(Connection con, String uuid, String description, double amount) {
		String sql = "UPDATE job_items SET description = ?, amount = ?, updated_at = datetime('now'), sync_status = 'PENDING' WHERE uuid = ?";
		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, description);
			ps.setDouble(2, amount);
			ps.setString(3, uuid);
			ps.executeUpdate();
			String jobUuid = resolveJobUuid(con, uuid);
			if (jobUuid != null) {
				JobRepository.syncAmountFromJobItems(con, jobUuid);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update JobItem base record", e);
		}
	}

	public void delete(String uuid) throws Exception {
		try (Connection con = DBConnection.getConnection()) {
			delete(con, uuid);
		}
	}

	public void delete(Connection con, String uuid) {
		String jobUuid;
		try {
			jobUuid = resolveJobUuid(con, uuid);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to resolve job for JobItem delete", e);
		}

		model.User current = utils.SessionManager.getInstance().getCurrentUser();
		boolean isAdmin = current != null && current.getRole() != null && "ADMIN".equalsIgnoreCase(current.getRole());

		String sql;
		if (isAdmin) {
			sql = "DELETE FROM job_items WHERE uuid = ?";
		} else {
			sql = "UPDATE job_items SET is_deleted = 1, updated_at = datetime('now'), sync_status = 'PENDING' WHERE uuid = ?";
		}

		try (PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, uuid);
			ps.executeUpdate();
			if (jobUuid != null) {
				JobRepository.syncAmountFromJobItems(con, jobUuid);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to delete JobItem", e);
		}
	}

	private static String resolveJobUuid(Connection con, String itemUuid) throws SQLException {
		if (con == null || itemUuid == null || itemUuid.isBlank()) {
			return null;
		}
		try (PreparedStatement ps = con.prepareStatement("SELECT job_uuid FROM job_items WHERE uuid = ?")) {
			ps.setString(1, itemUuid);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getString(1) : null;
			}
		}
	}

	public double getTotalForJob(String jobUuid) throws Exception {
		String sql = """
				SELECT COALESCE(SUM(amount), 0) FROM job_items
				WHERE job_uuid = ? AND COALESCE(is_deleted, 0) = 0
				""";
		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setString(1, jobUuid);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getDouble(1) : 0.0;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to calculate job total", e);
		}
	}
}
