package service;

import java.sql.Connection;

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

}
