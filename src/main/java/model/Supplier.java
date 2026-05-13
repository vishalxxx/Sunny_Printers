package model;

public class Supplier {
	private int id;
	private String name;
	private String type; // CTP, Paper, Binding, Lamination...
	private String phone;
	private String address;
	private String gstNumber;
	private String businessName;

	public Supplier() {
	}

	public Supplier(int id, String name, String type, String phone, String address, String gstNumber) {
		this.id = id;
		this.name = name;
		this.type = type;
		this.phone = phone;
		this.address = address;
		this.gstNumber = gstNumber;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getGstNumber() {
		return gstNumber;
	}

	public void setGstNumber(String gstNumber) {
		this.gstNumber = gstNumber;
	}

	public String getbusinessName() {
		return businessName;
	}

	public void setbusinessName(String businessName) {
		this.businessName = businessName;
	}

	@Override
	public String toString() {
		return name;
	}
}
