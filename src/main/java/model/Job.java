package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Job implements Serializable {
	private Integer jobId; // DB PK (nullable before save)
	private Integer clientId; // FK to client (nullable)
	private String filePath; // Image/pdf path (optional)
	private String fileType; // "IMAGE" or "PDF"
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	// Lists for repeated entries
	private final List<Printing> printingList = new ArrayList<>();
	private final List<CtpPlate> ctpPlateList = new ArrayList<>();
	private final List<Paper> paperList = new ArrayList<>();
	private final List<Binding> bindingList = new ArrayList<>();
	private final List<Lamination> laminationList = new ArrayList<>();

	public Job() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();
	}

	// ====== convenience add/remove helpers ======
	public void addPrinting(Printing p) {
		if (p != null)
			printingList.add(p);
		touch();
	}

	public void removePrinting(Printing p) {
		printingList.remove(p);
		touch();
	}

	public List<Printing> getPrintingList() {
		return printingList;
	}

	public void addCtpPlate(CtpPlate p) {
		if (p != null)
			ctpPlateList.add(p);
		touch();
	}

	public void removeCtpPlate(CtpPlate p) {
		ctpPlateList.remove(p);
		touch();
	}

	public List<CtpPlate> getCtpPlateList() {
		return ctpPlateList;
	}

	public void addPaper(Paper p) {
		if (p != null)
			paperList.add(p);
		touch();
	}

	public void removePaper(Paper p) {
		paperList.remove(p);
		touch();
	}

	public List<Paper> getPaperList() {
		return paperList;
	}

	public void addBinding(Binding b) {
		if (b != null)
			bindingList.add(b);
		touch();
	}

	public void removeBinding(Binding b) {
		bindingList.remove(b);
		touch();
	}

	public List<Binding> getBindingList() {
		return bindingList;
	}

	public void addLamination(Lamination l) {
		if (l != null)
			laminationList.add(l);
		touch();
	}

	public void removeLamination(Lamination l) {
		laminationList.remove(l);
		touch();
	}

	public List<Lamination> getLaminationList() {
		return laminationList;
	}

	// ====== getters / setters ======
	public Integer getJobId() {
		return jobId;
	}

	public void setJobId(Integer jobId) {
		this.jobId = jobId;
		touch();
	}

	public Integer getClientId() {
		return clientId;
	}

	public void setClientId(Integer clientId) {
		this.clientId = clientId;
		touch();
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
		touch();
	}

	public String getFileType() {
		return fileType;
	}

	public void setFileType(String fileType) {
		this.fileType = fileType;
		touch();
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	private void touch() {
		this.updatedAt = LocalDateTime.now();
	}

	// compact job summary (can be used by formatter)
	public String shortSummary() {
		StringBuilder sb = new StringBuilder();
		sb.append("Printing: ").append(printingList.size()).append(" | Plates: ").append(ctpPlateList.size())
				.append(" | Paper: ").append(paperList.size()).append(" | Binding: ").append(bindingList.size())
				.append(" | Lamination: ").append(laminationList.size());
		return sb.toString();
	}

	@Override
	public String toString() {
		return "Job{" + "jobId=" + jobId + ", clientId=" + clientId + ", filePath='" + filePath + '\'' + ", createdAt="
				+ createdAt + ", printingCount=" + printingList.size() + '}';
	}
}
