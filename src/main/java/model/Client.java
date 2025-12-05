package model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Client {

	private IntegerProperty id = new SimpleIntegerProperty();
	private StringProperty businessName = new SimpleStringProperty();
	private StringProperty clientName = new SimpleStringProperty();
	private StringProperty nickName = new SimpleStringProperty();
	private StringProperty phone = new SimpleStringProperty();
	private StringProperty altPhone = new SimpleStringProperty();
	private StringProperty email = new SimpleStringProperty();
	private StringProperty gst = new SimpleStringProperty();
	private StringProperty pan = new SimpleStringProperty();
	private StringProperty billingAddress = new SimpleStringProperty();
	private StringProperty shippingAddress = new SimpleStringProperty();
	private StringProperty notes = new SimpleStringProperty();

	// ---------- CONSTRUCTOR ----------
	public Client(String businessName, String clientName, String nickName, String phone, String altPhone, String email,
			String gst, String pan, String billingAddress, String shippingAddress, String notes) {

		this.businessName.set(businessName);
		this.clientName.set(clientName);
		this.nickName.set(nickName);
		this.phone.set(phone);
		this.altPhone.set(altPhone);
		this.email.set(email);
		this.gst.set(gst);
		this.pan.set(pan);
		this.billingAddress.set(billingAddress);
		this.shippingAddress.set(shippingAddress);
		this.notes.set(notes);
	}

	// ----------- ID ------------
	public int getId() {
		return id.get();
	}

	public void setId(int id) {
		this.id.set(id);
	}

	public IntegerProperty idProperty() {
		return id;
	}

	// ----------- BUSINESS NAME ------------
	public String getBusinessName() {
		return businessName.get();
	}

	public StringProperty businessNameProperty() {
		return businessName;
	}

	// ----------- CLIENT NAME ------------
	public String getClientName() {
		return clientName.get();
	}

	public StringProperty clientNameProperty() {
		return clientName;
	}

	// ----------- NICK NAME ------------
	public String getNickName() {
		return nickName.get();
	}

	public StringProperty nickNameProperty() {
		return nickName;
	}

	// ----------- PHONE ------------
	public String getPhone() {
		return phone.get();
	}

	public StringProperty phoneProperty() {
		return phone;
	}

	// ----------- ALT PHONE ------------
	public String getAltPhone() {
		return altPhone.get();
	}

	public StringProperty altPhoneProperty() {
		return altPhone;
	}

	// ----------- EMAIL ------------
	public String getEmail() {
		return email.get();
	}

	public StringProperty emailProperty() {
		return email;
	}

	// ----------- GST ------------
	public String getGst() {
		return gst.get();
	}

	public StringProperty gstProperty() {
		return gst;
	}

	// ----------- PAN ------------
	public String getPan() {
		return pan.get();
	}

	public StringProperty panProperty() {
		return pan;
	}

	// ----------- BILLING ADDRESS ------------
	public String getBillingAddress() {
		return billingAddress.get();
	}

	public StringProperty billingAddressProperty() {
		return billingAddress;
	}

	// ----------- SHIPPING ADDRESS ------------
	public String getShippingAddress() {
		return shippingAddress.get();
	}

	public StringProperty shippingAddressProperty() {
		return shippingAddress;
	}

	// ----------- NOTES ------------
	public String getNotes() {
		return notes.get();
	}

	public StringProperty notesProperty() {
		return notes;
	}

}
