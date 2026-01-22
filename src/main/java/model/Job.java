package model;

import java.time.LocalDate;

public class Job {

    private int id;
    private String jobNo;
    private Integer clientId; // nullable for draft

    private String jobTitle;
    private LocalDate jobDate;

    private String status;
    private String remarks;

    private String createdAt;
    private String updatedAt;

    public Job() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getJobNo() { return jobNo; }
    public void setJobNo(String jobNo) { this.jobNo = jobNo; }

    public Integer getClientId() { return clientId; }
    public void setClientId(Integer clientId) { this.clientId = clientId; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public LocalDate getJobDate() { return jobDate; }
    public void setJobDate(LocalDate jobDate) { this.jobDate = jobDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
