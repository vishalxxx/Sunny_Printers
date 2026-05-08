package model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class InvoiceHistoryRow {

	private final StringProperty invoiceNo = new SimpleStringProperty();
	private final StringProperty clientName = new SimpleStringProperty();
	private final StringProperty date = new SimpleStringProperty();
	private final DoubleProperty amount = new SimpleDoubleProperty();
	private final StringProperty type = new SimpleStringProperty();
	private final StringProperty status = new SimpleStringProperty();

	public InvoiceHistoryRow(String invoiceNo, String clientName, String date, double amount, String type,
			String status) {
		this.invoiceNo.set(invoiceNo);
		this.clientName.set(clientName);
		this.date.set(date);
		this.amount.set(amount);
		this.type.set(type);
		this.status.set(status);
	}

	public String getInvoiceNo() {
		return invoiceNo.get();
	}

	public String getClientName() {
		return clientName.get();
	}

	public String getDate() {
		return date.get();
	}

	public double getAmount() {
		return amount.get();
	}

	public String getType() {
		return type.get();
	}

	public String getStatus() {
		return status.get();
	}

	public StringProperty invoiceNoProperty() {
		return invoiceNo;
	}

	public StringProperty clientNameProperty() {
		return clientName;
	}

	public StringProperty dateProperty() {
		return date;
	}

	public DoubleProperty amountProperty() {
		return amount;
	}

	public StringProperty typeProperty() {
		return type;
	}

	public StringProperty statusProperty() {
		return status;
	}
}
