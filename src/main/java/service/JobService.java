package service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;

import model.Job;
import model.JobSummary;
import repository.JobRepository;
import utils.DBConnection;

public class JobService {

	private final JobRepository repo = new JobRepository();

	public synchronized Job createDraftJob() {

		try (Connection con = DBConnection.getConnection()) {
			// Double-check inside synchronized block to prevent race conditions
			Job latest = repo.findLatestDraftJob();
			if (latest != null) {
				return latest;
			}

			con.setAutoCommit(false);

			Job job = repo.insertDraftJob(con);

			con.commit();
			return job;

		} catch (Exception e) {
			throw new RuntimeException("Failed to create draft job", e);
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
					    SET client_id = ?
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

		} catch (Exception e) {
			throw new RuntimeException("Failed to assign client to job", e);
		}
	}

	public void updateJobDetails(int jobId, String jobName, java.time.LocalDate jobDate) {

		if (jobId <= 0) {
			throw new IllegalArgumentException("Invalid job id");
		}

		if (jobName == null || jobName.trim().isEmpty()) {
			throw new IllegalArgumentException("Job name cannot be empty");
		}

		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);

			String sql = """
					UPDATE jobs
					SET job_title = ?, job_date = ?
					WHERE id = ?
					""";

			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setString(1, jobName.trim());
				if (jobDate != null) {
				    ps.setString(2, jobDate.toString());
				} else {
				    ps.setNull(2, java.sql.Types.DATE);
				}
				ps.setInt(3, jobId);
				ps.executeUpdate();
			}

			con.commit();

		} catch (Exception e) {
			throw new RuntimeException("Failed to update job details", e);
		}
	}

    public void updateJobImagePath(int jobId, String imagePath) {
        if (jobId <= 0) throw new IllegalArgumentException("Invalid job id");
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            String sql = "UPDATE jobs SET image_path = ? WHERE id = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, imagePath);
                ps.setInt(2, jobId);
                ps.executeUpdate();
            }
            con.commit();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update job image", e);
        }
    }

	
	public List<Job> getFullJobsByClientId(int clientId) {
	    return repo.findFullJobsByClientId(clientId);
	}

	public List<Job> searchJobs(String keyword) {
	    return repo.searchJobs(keyword);
	}

	
	public List<JobSummary> getJobsByClientId(int clientId) {
		return repo.findJobsByClientId(clientId);
	}

	public List<Job> getAllJobs() {
		return repo.findAllJobs();
	}
	public Job getJobById(int jobId) {
	    return repo.findJobById(jobId);
	}
	public Job getLatestDraftJob() {
	    return repo.findLatestDraftJob();
	}

	public List<Job> getJobsByInvoice(model.InvoiceMaster inv) {
	    return repo.findJobsByInvoice(inv);
	}

	public void updateJobStatus(int jobId, String status) {
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			String sql = "UPDATE jobs SET status = ? WHERE id = ?";
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setString(1, status);
				ps.setInt(2, jobId);
				ps.executeUpdate();
			}
			con.commit();
		} catch (Exception e) {
			throw new RuntimeException("Failed to update job status", e);
		}
	}

	public void updateJobChildStatus(int jobId, String childStatus) {
		if (jobId <= 0) {
			throw new IllegalArgumentException("Invalid job id");
		}
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			repo.updateChildStatus(con, jobId, childStatus);
			con.commit();
		} catch (Exception e) {
			throw new RuntimeException("Failed to update job child status", e);
		}
	}
	
	public List<Job> getCompletedJobsByClient(int clientId) {
	    return repo.findCompletedJobsByClientId(clientId);
	}

	public List<Job> getCompletedJobsByClientInDateRange(int clientId, LocalDate from, LocalDate to) {
		if (from == null || to == null) {
			return List.of();
		}
		return repo.findCompletedJobsByClientIdInDateRange(clientId, from, to);
	}

	public List<Job> getCompletedJobsAllClientsInDateRange(LocalDate from, LocalDate to) {
		if (from == null || to == null) {
			return List.of();
		}
		return repo.findCompletedJobsAllClientsInDateRange(from, to);
	}

	/** Job IDs only; safe for live UI totals (no per-row date parsing). */
	public List<Integer> getCompletedJobIdsByClientInDateRange(int clientId, LocalDate from, LocalDate to) {
		if (from == null || to == null) {
			return List.of();
		}
		return repo.findCompletedJobIdsByClientIdInDateRange(clientId, from, to);
	}

	public double getSumJobItemsAmountForJobIds(List<Integer> jobIds) {
		return repo.sumJobItemsAmountForJobIds(jobIds);
	}

	public long getTotalPrintingQtyForJobIds(List<Integer> jobIds) {
		return repo.sumPrintingQtyForJobIds(jobIds);
	}

}
