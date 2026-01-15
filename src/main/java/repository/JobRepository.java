package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import model.Job;
import model.JobSummary;
import utils.DBConnection;

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
	
	public List<JobSummary> findJobsByClientId(int clientId) {

        List<JobSummary> list = new ArrayList<>();

        String sql = """
            SELECT id, job_no, job_title, job_date
            FROM jobs
            WHERE client_id = ?
            ORDER BY DATE(job_date) DESC, id DESC
        """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, clientId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String jobNo = rs.getString("job_no");
                String jobTitle = rs.getString("job_title");
                LocalDate jobDate = LocalDate.parse(rs.getString("job_date"));

                list.add(new JobSummary(id, jobNo, jobTitle, jobDate));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch jobs for clientId=" + clientId, e);
        }

        return list;
    }
	
}
