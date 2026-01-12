package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import model.Job;

public class JobRepository {

	public Job insertDraftJob(Connection con, String jobNo) throws SQLException {

		String sql = """
				    INSERT INTO jobs (job_no, status)
				    VALUES (?, 'DRAFT')
				""";

		try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

			ps.setString(1, jobNo);
			ps.executeUpdate();

			ResultSet rs = ps.getGeneratedKeys();
			if (!rs.next()) {
				throw new SQLException("Failed to create draft job");
			}

			Job job = new Job();
			job.setId(rs.getInt(1));
			job.setJobNo(jobNo);
			job.setStatus("DRAFT");

			return job;
		}
	}
}
