package service;

import java.sql.Connection;
import java.sql.PreparedStatement;

import model.Job;
import repository.JobRepository;
import utils.DBConnection;

public class JobService {

	private final JobRepository repo = new JobRepository();

	public Job createDraftJob() {

		String jobNo = JobNumberGenerator.generate();

		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);

			Job job = repo.insertDraftJob(con, jobNo);

			con.commit();
			return job;

		} catch (Exception e) {
			throw new RuntimeException("Failed to create draft job", e);
		}
	}

	public class JobNumberGenerator {

		public static String generate() {
			return "JOB-" + System.currentTimeMillis();
		}
	}

	public void assignClient(Job job, int clientId) {

		if (job == null || job.getId() == 0) {
			throw new IllegalStateException("Job not initialized");
		}

		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);

			String sql = """
					    UPDATE jobs
					    SET client_id = ?, status = 'OPEN'
					    WHERE id = ?
					""";

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, clientId);
				ps.setInt(2, job.getId());
				ps.executeUpdate();
			}

			con.commit();

			// 🔥 update in-memory job
			job.setClientId(clientId);
			job.setStatus("OPEN");

		} catch (Exception e) {
			throw new RuntimeException("Failed to assign client to job", e);
		}
	}

}
