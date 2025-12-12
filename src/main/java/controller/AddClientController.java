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

public class AddClientController implements Initializable {

	// ---------- FORM FIELDS ----------
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

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		// Nothing required yet
	}

	// ============================================================
	// SAVE CLIENT BUTTON
	// ============================================================
	@FXML
	private void handleSaveClient() {

		// Basic validation
		if (businessNameField.getText().isEmpty() || clientNameField.getText().isEmpty()
				|| phoneField.getText().isEmpty()) {

			System.out.println("⚠ Required fields missing!");
			return;
		}

		// Create Client object
		Client client = new Client(businessNameField.getText(), clientNameField.getText(), nickNameField.getText(),
				phoneField.getText(), altPhoneField.getText(), emailField.getText(), gstField.getText(),
				panField.getText(), billingAddressField.getText(), shippingAddressField.getText(),
				notesField.getText());

		// Save to database
		ClientRepository repo = new ClientRepository();
		boolean saved = repo.save(client);

		if (saved) {
			System.out.println("✔ Client saved successfully!");

			// Clear fields after saving
			clearForm();

			// Future step: show toast
			// Toast.show("Client saved successfully!");
			Stage stage = (Stage) businessNameField.getScene().getWindow();
			Toast.show(stage, "Client saved successfully!");
		} else {
			System.out.println("❌ Failed to save client.");
		}
	}

	// ============================================================
	// CLEAR FORM FIELDS
	// ============================================================
	private void clearForm() {
		businessNameField.clear();
		clientNameField.clear();
		nickNameField.clear();
		phoneField.clear();
		altPhoneField.clear();
		emailField.clear();
		gstField.clear();
		panField.clear();

		billingAddressField.clear();
		shippingAddressField.clear();
		notesField.clear();
	}
}
