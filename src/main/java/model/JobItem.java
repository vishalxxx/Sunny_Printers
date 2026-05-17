package model;

public class JobItem {

	private String uuid;
	private String jobUuid;
	private String type;
	private String description;
	private double amount;
	private int sortOrder;
	
	private String syncStatus = "PENDING";
	private int syncVersion = 1;
	private int isDeleted = 0;
	private int isActive = 1;
	private String createdAt;
	private String updatedAt;
	private String syncedAt;
	private String deletedAt;

	public JobItem() {
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getJobUuid() {
		return jobUuid;
	}

	public void setJobUuid(String jobUuid) {
		this.jobUuid = jobUuid;
	}

	public String getJobId() {
		return jobUuid;
	}

	public void setJobId(String jobUuid) {
		this.jobUuid = jobUuid;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public double getAmount() {
		return amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public String getSyncStatus() { return syncStatus; }
	public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }

	public int getSyncVersion() { return syncVersion; }
	public void setSyncVersion(int syncVersion) { this.syncVersion = syncVersion; }

	public int getIsDeleted() { return isDeleted; }
	public void setIsDeleted(int isDeleted) { this.isDeleted = isDeleted; }

	public int getIsActive() { return isActive; }
	public void setIsActive(int isActive) { this.isActive = isActive; }

	public String getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

	public String getSyncedAt() { return syncedAt; }
	public void setSyncedAt(String syncedAt) { this.syncedAt = syncedAt; }

	public String getDeletedAt() { return deletedAt; }
	public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }

	public String getCreatedAt() { return createdAt; }
	public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
