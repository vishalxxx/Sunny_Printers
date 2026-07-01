package model;

import java.time.LocalDate;

public class Job {

	private String uuid;
	private String jobCode;
	private String clientUuid;
	private String invoiceUuid;

	private String jobTitle;
	private String jobType;
	private String description;
	private LocalDate jobDate;
	private LocalDate deliveryDate;

	private String status;
	private String childStatus;
	private String remarks;
	private String jobNumberMode;
	private String imagePath;

	private double amount;
	private int isDeleted;
	private int isActive = 1;
	private String syncStatus = "PENDING";
	private int syncVersion = 1;

	private Double jobTotal;
	private String createdAt;
	private String updatedAt;
	private String syncedAt;
	private String createdByUserUuid;
	private String updatedByUserUuid;

	private String clientBusinessName;
	private String invoiceNo;
	private String invoiceStatus;
	private String invoiceType;

	public Job() {
	}

	public boolean hasUuid() {
		return uuid != null && !uuid.isBlank();
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid != null ? uuid.trim() : null;
	}

	public String getJobCode() {
		return jobCode;
	}

	public void setJobCode(String jobCode) {
		this.jobCode = jobCode;
	}

	/** Display / legacy alias for {@link #getJobCode()}. */
	public String getJobNo() {
		return jobCode;
	}

	public void setJobNo(String jobNo) {
		this.jobCode = jobNo;
	}

	public String getClientUuid() {
		return clientUuid;
	}

	public void setClientUuid(String clientUuid) {
		this.clientUuid = clientUuid;
	}

	/** Alias used across controllers/repos. */
	public String getClientId() {
		return clientUuid;
	}

	public void setClientId(String clientId) {
		this.clientUuid = clientId;
	}

	public String getInvoiceUuid() {
		return invoiceUuid;
	}

	public void setInvoiceUuid(String invoiceUuid) {
		this.invoiceUuid = invoiceUuid;
	}

	public String getJobTitle() {
		return jobTitle;
	}

	public void setJobTitle(String jobTitle) {
		this.jobTitle = jobTitle;
	}

	public String getJobType() {
		return jobType;
	}

	public void setJobType(String jobType) {
		this.jobType = jobType;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalDate getJobDate() {
		return jobDate;
	}

	public void setJobDate(LocalDate jobDate) {
		this.jobDate = jobDate;
	}

	public LocalDate getDeliveryDate() {
		return deliveryDate;
	}

	public void setDeliveryDate(LocalDate deliveryDate) {
		this.deliveryDate = deliveryDate;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getChildStatus() {
		return childStatus;
	}

	public void setChildStatus(String childStatus) {
		this.childStatus = childStatus;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}

	public String getJobNumberMode() {
		return jobNumberMode;
	}

	public void setJobNumberMode(String jobNumberMode) {
		this.jobNumberMode = jobNumberMode;
	}

	public String getImagePath() {
		return imagePath;
	}

	public void setImagePath(String imagePath) {
		this.imagePath = imagePath;
	}

	public double getAmount() {
		return amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}

	public int getIsDeleted() {
		return isDeleted;
	}

	public void setIsDeleted(int isDeleted) {
		this.isDeleted = isDeleted;
	}

	public int getIsActive() {
		return isActive;
	}

	public void setIsActive(int isActive) {
		this.isActive = isActive;
	}

	public String getSyncStatus() {
		return syncStatus;
	}

	public void setSyncStatus(String syncStatus) {
		this.syncStatus = syncStatus;
	}

	public int getSyncVersion() {
		return syncVersion;
	}

	public void setSyncVersion(int syncVersion) {
		this.syncVersion = syncVersion;
	}

	public Double getJobTotal() {
		return jobTotal;
	}

	public void setJobTotal(Double jobTotal) {
		this.jobTotal = jobTotal;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}

	public String getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(String updatedAt) {
		this.updatedAt = updatedAt;
	}

	public String getSyncedAt() {
		return syncedAt;
	}

	public void setSyncedAt(String syncedAt) {
		this.syncedAt = syncedAt;
	}

	public String getClientBusinessName() {
		return clientBusinessName;
	}

	public void setClientBusinessName(String clientBusinessName) {
		this.clientBusinessName = clientBusinessName;
	}

	public String getInvoiceNo() {
		return invoiceNo;
	}

	public void setInvoiceNo(String invoiceNo) {
		this.invoiceNo = invoiceNo;
	}

	public String getInvoiceStatus() {
		return invoiceStatus;
	}

	public void setInvoiceStatus(String invoiceStatus) {
		this.invoiceStatus = invoiceStatus;
	}

	public String getInvoiceType() {
		return invoiceType;
	}

	public void setInvoiceType(String invoiceType) {
		this.invoiceType = invoiceType;
	}

	private final javafx.beans.property.BooleanProperty selected = new javafx.beans.property.SimpleBooleanProperty(
			false);

	public javafx.beans.property.BooleanProperty selectedProperty() {
		return selected;
	}

	public boolean isSelected() {
		return selected.get();
	}

	public void setSelected(boolean val) {
		this.selected.set(val);
	}

	public String getCreatedByUserUuid() {
		return createdByUserUuid;
	}

	public void setCreatedByUserUuid(String createdByUserUuid) {
		this.createdByUserUuid = createdByUserUuid;
	}

	public String getUpdatedByUserUuid() {
		return updatedByUserUuid;
	}

	public void setUpdatedByUserUuid(String updatedByUserUuid) {
		this.updatedByUserUuid = updatedByUserUuid;
	}
}
