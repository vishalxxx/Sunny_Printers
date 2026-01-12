package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InvoiceJob {

	private int jobId;
	private String jobNo;
	private String jobName;
	private LocalDate jobDate;

	private List<InvoiceLine> lines = new ArrayList<>();

	// -------- helpers --------

	public void addLine(InvoiceLine line) {
		lines.add(line);
	}

	public double getJobTotal() {
		return lines.stream().mapToDouble(InvoiceLine::getAmount).sum();
	}

	// -------- getters / setters --------

	public int getJobId() {
		return jobId;
	}

	public void setJobId(int jobId) {
		this.jobId = jobId;
	}

	public String getJobNo() {
		return jobNo;
	}

	public void setJobNo(String jobNo) {
		this.jobNo = jobNo;
	}

	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}

	public LocalDate getJobDate() {
		return jobDate;
	}

	public void setJobDate(LocalDate jobDate) {
		this.jobDate = jobDate;
	}

	public List<InvoiceLine> getLines() {
		return lines;
	}
}
