package model;

import java.time.LocalDate;

public class JobSummary {

    private int id;
    private String jobNo;
    private String jobTitle;
    private LocalDate jobDate;
    private int jobId;

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public JobSummary() {}

    public JobSummary(int id, String jobNo, String jobTitle, LocalDate jobDate) {
        this.id = id;
        this.jobNo = jobNo;
        this.jobTitle = jobTitle;
        this.jobDate = jobDate;
    }

    public int getId() {
        return id;
    }

    public String getJobNo() {
        return jobNo;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public LocalDate getJobDate() {
        return jobDate;
    }

    @Override
    public String toString() {
        return jobNo + " - " + jobTitle;
    }
}
