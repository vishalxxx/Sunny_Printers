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
		service.LoggerService.beginOperation("JOB-CREATE");
		try (Connection con = DBConnection.getConnection()) {
			Job latest = repo.findLatestDraftJob();
			if (latest != null) {
				service.LoggerService.endOperation("JOB-CREATE", true, "Found existing draft: " + latest.getUuid());
				return latest;
			}
			con.setAutoCommit(false);
			Job job = repo.insertDraftJob(con);
			con.commit();
			service.LoggerService.endOperation("JOB-CREATE", true, "Created new draft: " + job.getUuid());
			return job;
		} catch (Exception e) {
			service.LoggerService.endOperation("JOB-CREATE", false, "Exception: " + e.getMessage());
			throw new RuntimeException("Failed to create draft job", e);
		}
	}

	public void assignClient(Job job, String clientUuid) {
		if (job == null || !job.hasUuid()) {
			throw new IllegalStateException("Job not initialized");
		}
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			String userUuid = null;
			if (utils.SessionManager.getInstance().getCurrentUser() != null) {
				userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
			}
			String sql = """
					UPDATE jobs SET client_uuid = ?, sync_status = 'PENDING',
					updated_at = datetime('now'), sync_version = sync_version + 1,
					updated_by_user_uuid = ?
					WHERE uuid = ?
					""";
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setString(1, clientUuid);
				ps.setString(2, userUuid);
				ps.setString(3, job.getUuid());
				ps.executeUpdate();
			}
			con.commit();
			job.setClientUuid(clientUuid);
		} catch (Exception e) {
			throw new RuntimeException("Failed to assign client to job", e);
		}
	}

	public void updateJobDetails(String jobUuid, String jobName, LocalDate jobDate) {
		if (jobUuid == null || jobUuid.isBlank()) {
			throw new IllegalArgumentException("Invalid job uuid");
		}
		if (jobName == null || jobName.trim().isEmpty()) {
			throw new IllegalArgumentException("Job name cannot be empty");
		}
		service.LoggerService.beginOperation("JOB-UPDATE");
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			String userUuid = null;
			if (utils.SessionManager.getInstance().getCurrentUser() != null) {
				userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
			}
			String sql = """
					UPDATE jobs SET job_title = ?, job_date = ?, sync_status = 'PENDING',
					updated_at = datetime('now'), sync_version = sync_version + 1,
					updated_by_user_uuid = ?
					WHERE uuid = ?
					""";
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setString(1, jobName.trim());
				if (jobDate != null) {
					ps.setString(2, jobDate.toString());
				} else {
					ps.setNull(2, java.sql.Types.VARCHAR);
				}
				ps.setString(3, userUuid);
				ps.setString(4, jobUuid.trim());
				ps.executeUpdate();
			}
			con.commit();
			service.LoggerService.endOperation("JOB-UPDATE", true, "UUID: " + jobUuid + ", Name: " + jobName);
		} catch (Exception e) {
			service.LoggerService.endOperation("JOB-UPDATE", false, "Exception: " + e.getMessage());
			throw new RuntimeException("Failed to update job details", e);
		}
	}

	public void updateJobImagePath(String jobUuid, String imagePath) {
		if (jobUuid == null || jobUuid.isBlank()) {
			throw new IllegalArgumentException("Invalid job uuid");
		}
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			String userUuid = null;
			if (utils.SessionManager.getInstance().getCurrentUser() != null) {
				userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
			}
			String sql = """
					UPDATE jobs SET image_path = ?, sync_status = 'PENDING',
					updated_at = datetime('now'), sync_version = sync_version + 1,
					updated_by_user_uuid = ?
					WHERE uuid = ?
					""";
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setString(1, imagePath);
				ps.setString(2, userUuid);
				ps.setString(3, jobUuid.trim());
				ps.executeUpdate();
			}
			con.commit();
		} catch (Exception e) {
			throw new RuntimeException("Failed to update job image", e);
		}
	}

	public List<Job> getFullJobsByClientId(String clientUuid) {
		return repo.findFullJobsByClientId(clientUuid);
	}

	public List<Job> searchJobs(String keyword) {
		return repo.searchJobs(keyword);
	}

	public List<JobSummary> getJobsByClientId(String clientId) {
		return repo.findJobsByClientId(clientId);
	}

	public List<Job> getAllJobs() {
		return repo.findAllJobs();
	}

	public Job getJobByUuid(String jobUuid) {
		return repo.findJobByUuid(jobUuid);
	}

	public Job getLatestDraftJob() {
		return repo.findLatestDraftJob();
	}

	public List<Job> getJobsByInvoice(model.InvoiceMaster inv) {
		return repo.findJobsByInvoice(inv);
	}

	public void updateJobStatus(String jobUuid, String status) {
		if (jobUuid == null || jobUuid.isBlank()) {
			throw new IllegalArgumentException("Invalid job uuid");
		}
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			String userUuid = null;
			if (utils.SessionManager.getInstance().getCurrentUser() != null) {
				userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
			}
			String sql = """
					UPDATE jobs SET status = ?, sync_status = 'PENDING',
					updated_at = datetime('now'), sync_version = sync_version + 1,
					updated_by_user_uuid = ?
					WHERE uuid = ?
					""";
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setString(1, status);
				ps.setString(2, userUuid);
				ps.setString(3, jobUuid.trim());
				ps.executeUpdate();
			}
			con.commit();
		} catch (Exception e) {
			throw new RuntimeException("Failed to update job status", e);
		}
	}

	public void updateJobChildStatus(String jobUuid, String childStatus) {
		if (jobUuid == null || jobUuid.isBlank()) {
			throw new IllegalArgumentException("Invalid job uuid");
		}
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			repo.updateChildStatus(con, jobUuid.trim(), childStatus);
			con.commit();
		} catch (Exception e) {
			throw new RuntimeException("Failed to update job child status", e);
		}
	}

	public List<Job> getCompletedJobsByClient(String clientUuid) {
		return repo.findCompletedJobsByClientId(clientUuid);
	}

	public List<Job> getCompletedJobsByClientInDateRange(String clientId, LocalDate from, LocalDate to) {
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

	public List<String> getCompletedJobUuidsByClientInDateRange(String clientId, LocalDate from, LocalDate to) {
		if (from == null || to == null) {
			return List.of();
		}
		return repo.findCompletedJobUuidsByClientIdInDateRange(clientId, from, to);
	}

	public double getSumJobItemsAmountForJobUuids(List<String> jobUuids) {
		return repo.sumJobItemsAmountForJobUuids(jobUuids);
	}

	public long getTotalPrintingQtyForJobUuids(List<String> jobUuids) {
		return repo.sumPrintingQtyForJobUuids(jobUuids);
	}
}
