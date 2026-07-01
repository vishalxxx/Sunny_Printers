package model;

public class BankDetails {

	private String uuid;
	private String bankName;
	private String accountHolderName;
	private String accountNo;
	private String branchName;
	private String ifscCode;
	private boolean isDefault;
	private boolean isActive;

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getBankName() {
		return bankName;
	}

	public void setBankName(String bankName) {
		this.bankName = bankName;
	}

	public String getAccountHolderName() {
		return accountHolderName;
	}

	public void setAccountHolderName(String accountHolderName) {
		this.accountHolderName = accountHolderName;
	}

	public String getAccountNo() {
		return accountNo;
	}

	public void setAccountNo(String accountNo) {
		this.accountNo = accountNo;
	}

	public String getBranchName() {
		return branchName;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

	public String getIfscCode() {
		return ifscCode;
	}

	public void setIfscCode(String ifscCode) {
		this.ifscCode = ifscCode;
	}

	/** Backward-compatible combined view used in UI/PDF. */
	public String getBranchIfsc() {
		String b = branchName != null ? branchName.trim() : "";
		String i = ifscCode != null ? ifscCode.trim() : "";
		if (b.isBlank() && i.isBlank()) {
			return "";
		}
		if (b.isBlank()) {
			return i;
		}
		if (i.isBlank()) {
			return b;
		}
		return b + " & " + i;
	}

	/** Backward-compatible setter: attempts to split "Branch & IFSC". */
	public void setBranchIfsc(String branchIfsc) {
		String v = branchIfsc != null ? branchIfsc.trim() : "";
		if (v.isBlank()) {
			this.branchName = "";
			this.ifscCode = "";
			return;
		}
		// Common stored format: "BRANCH & IFSC"
		String[] parts = v.split("\\s*&\\s*", 2);
		if (parts.length == 2) {
			this.branchName = parts[0].trim();
			this.ifscCode = parts[1].trim();
			return;
		}
		this.branchName = v;
		this.ifscCode = "";
	}

	public boolean isDefault() {
		return isDefault;
	}

	public void setDefault(boolean isDefault) {
		this.isDefault = isDefault;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

	@Override
	public String toString() {
		return bankName != null ? bankName : "";
	}
}
