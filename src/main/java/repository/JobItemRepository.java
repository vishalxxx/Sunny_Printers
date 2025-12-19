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

	/* ================= INSERT ================= */

	public JobItem save(JobItem item) {

		String sql = """
				    INSERT INTO job_items (job_id, type, description, amount, sort_order)
				    VALUES (?, ?, ?, ?, ?)
				""";

		try (Connection con = DBConnection.getConnection();
				PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

			ps.setInt(1, item.getJobId());
			ps.setString(2, item.getType());
			ps.setString(3, item.getDescription());
			ps.setDouble(4, item.getAmount());
			ps.setInt(5, item.getSortOrder());

			ps.executeUpdate();

			try (ResultSet rs = ps.getGeneratedKeys()) {
				if (rs.next()) {
					item.setId(rs.getInt(1));
				}
			}

			return item;

		} catch (SQLException e) {
			throw new RuntimeException("Failed to save JobItem", e);
		}
	}

	/* ================= FETCH BY JOB ================= */

	public List<JobItem> findByJobId(int jobId) {

		String sql = """
				    SELECT id, job_id, type, description, amount, sort_order
				    FROM job_items
				    WHERE job_id = ?
				    ORDER BY sort_order, id
				""";

		List<JobItem> list = new ArrayList<>();

		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, jobId);

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {

					JobItem item = new JobItem();
					item.setId(rs.getInt("id"));
					item.setJobId(rs.getInt("job_id"));
					item.setType(rs.getString("type"));
					item.setDescription(rs.getString("description"));
					item.setAmount(rs.getDouble("amount"));
					item.setSortOrder(rs.getInt("sort_order"));

					list.add(item);
				}
			}

		} catch (SQLException e) {
			throw new RuntimeException("Failed to fetch JobItems", e);
		}

		return list;
	}

	/* ================= DELETE ================= */

	public void delete(int itemId) {

		String sql = "DELETE FROM job_items WHERE id = ?";

		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, itemId);
			ps.executeUpdate();

		} catch (SQLException e) {
			throw new RuntimeException("Failed to delete JobItem", e);
		}
	}

	/* ================= TOTAL ================= */

	public double getTotalForJob(int jobId) {

		String sql = "SELECT COALESCE(SUM(amount), 0) FROM job_items WHERE job_id = ?";

		try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setInt(1, jobId);

			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getDouble(1) : 0.0;
			}

		} catch (SQLException e) {
			throw new RuntimeException("Failed to calculate job total", e);
		}
	}
}
