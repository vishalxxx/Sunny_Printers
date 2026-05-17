package model;

import java.time.LocalDate;

public class JobSummary {

	private String uuid;
	private String jobCode;
	private String jobTitle;
	private LocalDate jobDate;

	public JobSummary() {
	}

	public JobSummary(String uuid, String jobCode, String jobTitle, LocalDate jobDate) {
		this.uuid = uuid;
		this.jobCode = jobCode;
		this.jobTitle = jobTitle;
		this.jobDate = jobDate;
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getJobCode() {
		return jobCode;
	}

	public String getJobNo() {
		return jobCode;
	}

	public String getJobTitle() {
		return jobTitle;
	}

	public LocalDate getJobDate() {
		return jobDate;
	}

	@Override
	public String toString() {
		return jobCode + " - " + jobTitle;
	}
}
