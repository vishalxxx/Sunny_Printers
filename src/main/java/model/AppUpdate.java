package model;

import com.google.gson.annotations.SerializedName;

public class AppUpdate {
    private String version;

    @SerializedName("minimum_supported_version")
    private String minimumSupportedVersion;

    @SerializedName("storage_path")
    private String storagePath;

    @SerializedName("release_notes")
    private String releaseNotes;

    private boolean mandatory;

    @SerializedName("file_name")
    private String fileName;

    @SerializedName("file_size")
    private long fileSize;

    private String sha256;

    @SerializedName("release_channel")
    private String releaseChannel;

    private boolean published;

    @SerializedName("created_at")
    private String createdAt;

    // Computed / helper fields (Phase 2 Prep)
    private boolean newerThanInstalled;
    private boolean mandatoryUpdate;
    private boolean compatible = true; // default true for standard channels
    private String downloadFileName;

    // Default Constructor
    public AppUpdate() {}

    /**
     * Validates that all required fields are populated and valid.
     */
    public boolean isValidMetadata() {
        return published 
            && storagePath != null && !storagePath.isBlank()
            && fileName != null && !fileName.isBlank()
            && sha256 != null && !sha256.isBlank()
            && fileSize > 0;
    }

    public boolean isNewerThanInstalled() {
        return newerThanInstalled;
    }

    public void setNewerThanInstalled(boolean newerThanInstalled) {
        this.newerThanInstalled = newerThanInstalled;
    }

    public boolean isMandatoryUpdate() {
        return mandatoryUpdate;
    }

    public void setMandatoryUpdate(boolean mandatoryUpdate) {
        this.mandatoryUpdate = mandatoryUpdate;
    }

    public boolean isCompatible() {
        return compatible;
    }

    public void setCompatible(boolean compatible) {
        this.compatible = compatible;
    }

    public String getDownloadFileName() {
        return downloadFileName != null ? downloadFileName : fileName;
    }

    public void setDownloadFileName(String downloadFileName) {
        this.downloadFileName = downloadFileName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getMinimumSupportedVersion() {
        return minimumSupportedVersion;
    }

    public void setMinimumSupportedVersion(String minimumSupportedVersion) {
        this.minimumSupportedVersion = minimumSupportedVersion;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public void setReleaseNotes(String releaseNotes) {
        this.releaseNotes = releaseNotes;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getReleaseChannel() {
        return releaseChannel;
    }

    public void setReleaseChannel(String releaseChannel) {
        this.releaseChannel = releaseChannel;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
