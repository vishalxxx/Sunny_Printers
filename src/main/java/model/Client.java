package model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import utils.ClientIdentifiers;

/**
 * Client row: {@code uuid} (UUID v7 primary key), {@code client_code} (e.g. CL-A7K29X).
 * UI uses {@code getPhone}/{@code getGst}/…; SQL columns are {@code mobile}, {@code gstin}, {@code billing_address}, …
 */
public class Client {

	private StringProperty clientUuid = new SimpleStringProperty(ClientIdentifiers.newUuidString());
	private StringProperty clientCode = new SimpleStringProperty("");
	private StringProperty businessName = new SimpleStringProperty();
	private StringProperty clientName = new SimpleStringProperty();
	private StringProperty phone = new SimpleStringProperty();
	private StringProperty altPhone = new SimpleStringProperty();
	private StringProperty email = new SimpleStringProperty();
	private StringProperty gst = new SimpleStringProperty();
	private StringProperty pan = new SimpleStringProperty();
	private StringProperty billingAddress = new SimpleStringProperty();
	private StringProperty shippingAddress = new SimpleStringProperty();
	private StringProperty notes = new SimpleStringProperty();
	private StringProperty clientType = new SimpleStringProperty("Regular");
	private StringProperty priceCategory = new SimpleStringProperty("");
	private StringProperty paymentTerms = new SimpleStringProperty("");
	private StringProperty balanceType = new SimpleStringProperty("DR");
	private StringProperty syncStatus = new SimpleStringProperty("PENDING");
	private StringProperty deletedAt = new SimpleStringProperty("");
	private StringProperty createdAt = new SimpleStringProperty("");
	private StringProperty updatedAt = new SimpleStringProperty("");
	private StringProperty syncedAt = new SimpleStringProperty("");
	private double creditLimit;
	private double openingBalance;
	private int syncVersion = 1;
	private boolean isDeleted;
	private boolean isActive = true;

	private StringProperty createdByUserUuid = new SimpleStringProperty("");
	private StringProperty updatedByUserUuid = new SimpleStringProperty("");

	// UI Transient Metrics
	private double ltv;
	private double balance;
	private int activityScore;
	private String segment = "Active";
	private String insight = "";

	public Client() {
	}

	public Client(String businessName, String clientName, String phone, String altPhone, String email, String gst,
			String pan, String billingAddress, String shippingAddress, String notes) {
		this.businessName.set(businessName);
		this.clientName.set(clientName);
		this.phone.set(phone);
		this.altPhone.set(altPhone);
		this.email.set(email);
		this.gst.set(gst);
		this.pan.set(pan);
		this.billingAddress.set(billingAddress);
		this.shippingAddress.set(shippingAddress);
		this.notes.set(notes);
	}

	public boolean hasClientUuid() {
		String s = clientUuid.get();
		return s != null && !s.isBlank();
	}

	public String getClientUuid() {
		String s = clientUuid.get();
		if (s == null || s.isBlank()) {
			String gen = ClientIdentifiers.newUuidString();
			clientUuid.set(gen);
			return gen;
		}
		return s;
	}

	public void setClientUuid(String v) {
		if (v == null || v.isBlank()) {
			clientUuid.set(ClientIdentifiers.newUuidV7String());
		} else {
			clientUuid.set(v.trim());
		}
	}

	public StringProperty clientUuidProperty() {
		return clientUuid;
	}

	public String getClientCode() {
		return clientCode.get();
	}

	public void setClientCode(String v) {
		clientCode.set(v != null ? v : "");
	}

	public StringProperty clientCodeProperty() {
		return clientCode;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setIsActive(boolean isActive) {
		this.isActive = isActive;
	}

	public String getSyncStatus() {
		return syncStatus.get();
	}

	public void setSyncStatus(String v) {
		syncStatus.set(v != null ? v : "PENDING");
	}

	public int getSyncVersion() {
		return syncVersion;
	}

	public void setSyncVersion(int syncVersion) {
		this.syncVersion = syncVersion;
	}

	public boolean isDeleted() {
		return isDeleted;
	}

	public void setIsDeleted(boolean isDeleted) {
		this.isDeleted = isDeleted;
	}

	public String getCreatedAt() {
		return createdAt.get();
	}

	public void setCreatedAt(String v) {
		createdAt.set(v != null ? v : "");
	}

	public String getUpdatedAt() {
		return updatedAt.get();
	}

	public void setUpdatedAt(String v) {
		updatedAt.set(v != null ? v : "");
	}

	public String getSyncedAt() {
		return syncedAt.get();
	}

	public void setSyncedAt(String v) {
		syncedAt.set(v != null ? v : "");
	}

	public double getCreditLimit() {
		return creditLimit;
	}

	public void setCreditLimit(double creditLimit) {
		this.creditLimit = creditLimit;
	}

	public double getOpeningBalance() {
		return openingBalance;
	}

	public void setOpeningBalance(double openingBalance) {
		this.openingBalance = openingBalance;
	}

	public String getClientType() {
		return clientType.get();
	}

	public void setClientType(String v) {
		clientType.set(v != null ? v : "Regular");
	}

	public String getPriceCategory() {
		return priceCategory.get();
	}

	public void setPriceCategory(String v) {
		priceCategory.set(v != null ? v : "");
	}

	public String getPaymentTerms() {
		return paymentTerms.get();
	}

	public void setPaymentTerms(String v) {
		paymentTerms.set(v != null ? v : "");
	}

	public String getBalanceType() {
		return balanceType.get();
	}

	public void setBalanceType(String v) {
		balanceType.set(v != null ? v : "DR");
	}

	public String getDeletedAt() {
		return deletedAt.get();
	}

	public void setDeletedAt(String v) {
		deletedAt.set(v != null ? v : "");
	}

	public void setBusinessName(String v) {
		businessName.set(v != null ? v : "");
	}

	public String getBusinessName() {
		return businessName.get();
	}

	public StringProperty businessNameProperty() {
		return businessName;
	}

	public String getClientName() {
		return clientName.get();
	}

	public StringProperty clientNameProperty() {
		return clientName;
	}

	public String getPhone() {
		return phone.get();
	}

	public StringProperty phoneProperty() {
		return phone;
	}

	public String getAltPhone() {
		return altPhone.get();
	}

	public StringProperty altPhoneProperty() {
		return altPhone;
	}

	public String getEmail() {
		return email.get();
	}

	public StringProperty emailProperty() {
		return email;
	}

	public String getGst() {
		return gst.get();
	}

	public StringProperty gstProperty() {
		return gst;
	}

	public String getPan() {
		return pan.get();
	}

	public StringProperty panProperty() {
		return pan;
	}

	public String getBillingAddress() {
		return billingAddress.get();
	}

	public StringProperty billingAddressProperty() {
		return billingAddress;
	}

	public String getShippingAddress() {
		return shippingAddress.get();
	}

	public StringProperty shippingAddressProperty() {
		return shippingAddress;
	}

	public String getNotes() {
		return notes.get();
	}

	public StringProperty notesProperty() {
		return notes;
	}

	@Override
	public String toString() {
		String bn = getBusinessName() == null ? "" : getBusinessName();
		String cn = getClientName() == null ? "" : getClientName();
		if (bn.isBlank() && cn.isBlank()) {
			return "";
		}
		if (cn.isBlank()) {
			return bn;
		}
		if (bn.isBlank()) {
			return cn;
		}
		return bn + " (" + cn + ")";
	}

	public double getLtv() {
		return ltv;
	}

	public void setLtv(double ltv) {
		this.ltv = ltv;
	}

	public double getBalance() {
		return balance;
	}

	public void setBalance(double balance) {
		this.balance = balance;
	}

	public int getActivityScore() {
		return activityScore;
	}

	public void setActivityScore(int activityScore) {
		this.activityScore = activityScore;
	}

	public String getSegment() {
		return segment;
	}

	public void setSegment(String segment) {
		this.segment = segment;
	}

	public String getInsight() {
		return insight;
	}

	public void setInsight(String insight) {
		this.insight = insight;
	}

	public String getCreatedByUserUuid() {
		return createdByUserUuid.get();
	}

	public void setCreatedByUserUuid(String v) {
		createdByUserUuid.set(v != null ? v : "");
	}

	public StringProperty createdByUserUuidProperty() {
		return createdByUserUuid;
	}

	public String getUpdatedByUserUuid() {
		return updatedByUserUuid.get();
	}

	public void setUpdatedByUserUuid(String v) {
		updatedByUserUuid.set(v != null ? v : "");
	}

	public StringProperty updatedByUserUuidProperty() {
		return updatedByUserUuid;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Client client = (Client) o;
		String thisUuid = getClientUuid();
		String thatUuid = client.getClientUuid();
		return thisUuid != null ? thisUuid.equals(thatUuid) : thatUuid == null;
	}

	@Override
	public int hashCode() {
		String thisUuid = getClientUuid();
		return thisUuid != null ? thisUuid.hashCode() : 0;
	}
}
