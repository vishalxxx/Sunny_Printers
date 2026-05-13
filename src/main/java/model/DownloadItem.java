package model;

import java.time.LocalDateTime;

public class DownloadItem {
    private String fileName;
    private String fileType; // PDF, EXCEL, etc.
    private String fileSize;
    private LocalDateTime downloadDate;
    private String filePath;

    public DownloadItem(String fileName, String fileType, String fileSize, LocalDateTime downloadDate, String filePath) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.downloadDate = downloadDate;
        this.filePath = filePath;
    }

    public String getFileName() { return fileName; }
    public String getFileType() { return fileType; }
    public String getFileSize() { return fileSize; }
    public LocalDateTime getDownloadDate() { return downloadDate; }
    public String getFilePath() { return filePath; }
}
