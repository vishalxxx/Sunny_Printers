package controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.Client;
import repository.ClientRepository;
import utils.Toast;

public class EditClientController implements Initializable {

	private Client selectedClient;

	@FXML
	private TextField businessNameField;
	@FXML
	private TextField clientNameField;
	@FXML
	private TextField nickNameField;
	@FXML
	private TextField phoneField;
	@FXML
	private TextField altPhoneField;
	@FXML
	private TextField emailField;
	@FXML
	private TextField gstField;
	@FXML
	private TextField panField;

	@FXML
	private TextArea billingAddressField;
	@FXML
	private TextArea shippingAddressField;
	@FXML
	private TextArea notesField;

	private final ClientRepository repo = new ClientRepository();

	@Override
	public void initialize(URL url, ResourceBundle resourceBundle) {
	}

	// This method is called from ViewClientsController
	public void setClientData(Client client) {
		this.selectedClient = client;

		businessNameField.setText(client.getBusinessName());
		clientNameField.setText(client.getClientName());
		nickNameField.setText(client.getNickName());
		phoneField.setText(client.getPhone());
		altPhoneField.setText(client.getAltPhone());
		emailField.setText(client.getEmail());
		gstField.setText(client.getGst());
		panField.setText(client.getPan());

		billingAddressField.setText(client.getBillingAddress());
		shippingAddressField.setText(client.getShippingAddress());
		notesField.setText(client.getNotes());
	}

	@FXML
	private void handleUpdateClient() {

		if (selectedClient == null)
			return;

		// update fields
		selectedClient.businessNameProperty().set(businessNameField.getText());
		selectedClient.clientNameProperty().set(clientNameField.getText());
		selectedClient.nickNameProperty().set(nickNameField.getText());
		selectedClient.phoneProperty().set(phoneField.getText());
		selectedClient.altPhoneProperty().set(altPhoneField.getText());
		selectedClient.emailProperty().set(emailField.getText());
		selectedClient.gstProperty().set(gstField.getText());
		selectedClient.panProperty().set(panField.getText());
		selectedClient.billingAddressProperty().set(billingAddressField.getText());
		selectedClient.shippingAddressProperty().set(shippingAddressField.getText());
		selectedClient.notesProperty().set(notesField.getText());

		boolean updated = repo.update(selectedClient);

		if (updated) {
			System.out.println("✔ Client updated successfully!");

			// ⭐ Correct Toast usage
			Stage stage = (Stage) businessNameField.getScene().getWindow();
			Toast.show(stage, "Client updated successfully!");

			// Reload View Clients Table
			MainController.getInstance().loadViewClients();

		} else {
			System.out.println("❌ Failed to update client");
		}
	}

}
