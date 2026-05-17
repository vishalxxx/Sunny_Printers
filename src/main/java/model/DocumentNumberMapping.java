package model;

/**
 * Audit row: offline TEMP-* number promoted to a permanent number from remote number_sequences.
 */
public class DocumentNumberMapping {

	public static final String SOURCE_REMOTE = "remote";

	private String uuid;
	private String entityType;
	private String entityUuid;
	private String sequenceKey;
	private String temporaryNumber;
	private String permanentNumber;
	private String allocationSource;
	private String createdAt;

	public String getUuid() { return uuid; }
	public void setUuid(String uuid) { this.uuid = uuid; }
	public String getEntityType() { return entityType; }
	public void setEntityType(String entityType) { this.entityType = entityType; }
	public String getEntityUuid() { return entityUuid; }
	public void setEntityUuid(String entityUuid) { this.entityUuid = entityUuid; }
	public String getSequenceKey() { return sequenceKey; }
	public void setSequenceKey(String sequenceKey) { this.sequenceKey = sequenceKey; }
	public String getTemporaryNumber() { return temporaryNumber; }
	public void setTemporaryNumber(String temporaryNumber) { this.temporaryNumber = temporaryNumber; }
	public String getPermanentNumber() { return permanentNumber; }
	public void setPermanentNumber(String permanentNumber) { this.permanentNumber = permanentNumber; }
	public String getAllocationSource() { return allocationSource; }
	public void setAllocationSource(String allocationSource) { this.allocationSource = allocationSource; }
	public String getCreatedAt() { return createdAt; }
	public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}