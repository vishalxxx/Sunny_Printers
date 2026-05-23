package controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.Client;
import repository.ClientRepository;
import utils.Toast;

public class ClientFormController implements Initializable {

	private Client selectedClient;

	private EditFormBaseline editBaseline;

	private record EditFormBaseline(String businessName, String clientName, String phone, String altPhone, String email,
			String gst, String pan, String billing, String shipping, String notes, double creditLimit, double openingBalance) {
	}

	@FXML
	private Label lblTitleName;
	@FXML
	private Label lblClientId;
	@FXML
	private HBox breadcrumbContainer;
	@FXML
	private VBox formShell;
	@FXML
	private VBox pageTitleBlock;
	@FXML
	private Button btnSave;

	@FXML
	private TextField businessNameField;
	@FXML
	private TextField clientNameField;
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
	private TextField creditLimitField;
	@FXML
	private TextField openingBalanceField;

	@FXML
	private TextArea billingAddressField;
	@FXML
	private TextArea shippingAddressField;
	@FXML
	private TextArea notesField;
	@FXML
	private ToggleButton syncAddressToggle;

	private final ClientRepository repo = new ClientRepository();

	@Override
	public void initialize(URL url, ResourceBundle resourceBundle) {
		utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null, () -> handleBack(null));
		setupAddressSyncToggle();
		setupSaveButtonRequiredFields();
	}

	/** Register / Update stays disabled until both business name and client name are non-empty. */
	private void setupSaveButtonRequiredFields() {
		if (btnSave == null || businessNameField == null || clientNameField == null) {
			return;
		}
		btnSave.disableProperty().bind(Bindings.createBooleanBinding(
				() -> !hasRequiredNameFields(),
				businessNameField.textProperty(),
				clientNameField.textProperty()));
	}

	private boolean hasRequiredNameFields() {
		return !nz(businessNameField.getText()).isBlank() && !nz(clientNameField.getText()).isBlank();
	}

	@FXML
	private void handleBack(javafx.event.Event e) {
		MainController.getInstance().handleBack(e);
	}

	public void setClientData(Client client) {
		this.selectedClient = client;

		if (client == null) {
			editBaseline = null;
			if (formShell != null) {
				if (!formShell.getStyleClass().contains("client-form--add")) {
					formShell.getStyleClass().add("client-form--add");
				}
			}
			if (pageTitleBlock != null) {
				pageTitleBlock.setVisible(false);
				pageTitleBlock.setManaged(false);
			}
			if (lblClientId != null) {
				lblClientId.setText("#CL-NEW");
			}
			utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null, () -> handleBack(null));
			if (btnSave != null) {
				btnSave.setText("Register Client");
			}
			clearFields();
			return;
		}

		editBaseline = new EditFormBaseline(nz(client.getBusinessName()), nz(client.getClientName()),
				nz(client.getPhone()), nz(client.getAltPhone()), nz(client.getEmail()), nz(client.getGst()),
				nz(client.getPan()), nz(client.getBillingAddress()), nz(client.getShippingAddress()),
				nz(client.getNotes()), client.getCreditLimit(), client.getOpeningBalance());
		if (formShell != null) {
			formShell.getStyleClass().remove("client-form--add");
		}
		if (pageTitleBlock != null) {
			pageTitleBlock.setVisible(true);
			pageTitleBlock.setManaged(true);
		}
		if (lblTitleName != null) {
			lblTitleName.setVisible(true);
			lblTitleName.setManaged(true);
			lblTitleName.setText(client.getBusinessName());
		}
		if (lblClientId != null) {
			String code = client.getClientCode();
			lblClientId.setText((code != null && !code.isBlank()) ? code : client.getClientUuid());
		}
		utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, "Edit Client", () -> handleBack(null));
		if (btnSave != null) {
			btnSave.setText("Update Profile");
		}

		businessNameField.setText(client.getBusinessName());
		clientNameField.setText(client.getClientName());
		phoneField.setText(client.getPhone());
		altPhoneField.setText(client.getAltPhone());
		emailField.setText(client.getEmail());
		gstField.setText(client.getGst());
		panField.setText(client.getPan());
		creditLimitField.setText(String.valueOf(client.getCreditLimit()));
		openingBalanceField.setText(String.valueOf(client.getOpeningBalance()));
		billingAddressField.setText(client.getBillingAddress());
		shippingAddressField.setText(client.getShippingAddress());
		notesField.setText(client.getNotes());

		applyAddressSyncUiFromLoadedAddresses();
	}

	private static String nz(String s) {
		return s == null ? "" : s.trim();
	}

	private static String normalizeAddress(String s) {
		return s == null ? "" : s.trim();
	}

	private boolean editFormUnchangedFromBaseline() {
		if (editBaseline == null) {
			return false;
		}
		String curBill = nz(billingAddressField.getText());
		String curShip = (syncAddressToggle != null && syncAddressToggle.isSelected()) ? curBill
				: nz(shippingAddressField.getText());
		double cl = 0;
		try { cl = Double.parseDouble(creditLimitField.getText()); } catch (Exception ignored) {}
		double ob = 0;
		try { ob = Double.parseDouble(openingBalanceField.getText()); } catch (Exception ignored) {}
		return nz(businessNameField.getText()).equals(editBaseline.businessName())
				&& nz(clientNameField.getText()).equals(editBaseline.clientName())
				&& nz(phoneField.getText()).equals(editBaseline.phone())
				&& nz(altPhoneField.getText()).equals(editBaseline.altPhone())
				&& nz(emailField.getText()).equals(editBaseline.email())
				&& nz(gstField.getText()).equals(editBaseline.gst()) && nz(panField.getText()).equals(editBaseline.pan())
				&& curBill.equals(editBaseline.billing()) && curShip.equals(editBaseline.shipping())
				&& nz(notesField.getText()).equals(editBaseline.notes())
				&& Double.compare(cl, editBaseline.creditLimit()) == 0
				&& Double.compare(ob, editBaseline.openingBalance()) == 0;
	}

	private void applyAddressSyncUiFromLoadedAddresses() {
		if (syncAddressToggle == null || billingAddressField == null || shippingAddressField == null) {
			return;
		}
		String bill = normalizeAddress(billingAddressField.getText());
		String ship = normalizeAddress(shippingAddressField.getText());
		boolean same = bill.equals(ship);
		syncAddressToggle.setSelected(same);
		shippingAddressField.setDisable(same);
	}

	private void setupAddressSyncToggle() {
		if (syncAddressToggle == null || billingAddressField == null || shippingAddressField == null) {
			return;
		}
		syncAddressToggle.selectedProperty().addListener((obs, wasOn, on) -> {
			if (Boolean.TRUE.equals(on)) {
				shippingAddressField.setText(billingAddressField.getText());
				shippingAddressField.setDisable(true);
			} else {
				shippingAddressField.setDisable(false);
			}
		});
		billingAddressField.textProperty().addListener((obs, oldV, newV) -> {
			if (syncAddressToggle.isSelected()) {
				shippingAddressField.setText(newV == null ? "" : newV);
			}
		});
	}

	private void clearFields() {
		businessNameField.clear();
		clientNameField.clear();
		phoneField.clear();
		altPhoneField.clear();
		emailField.clear();
		gstField.clear();
		panField.clear();
		creditLimitField.setText("0.0");
		openingBalanceField.setText("0.0");
		billingAddressField.clear();
		shippingAddressField.clear();
		notesField.clear();
		if (syncAddressToggle != null) {
			syncAddressToggle.setSelected(false);
		}
		if (shippingAddressField != null) {
			shippingAddressField.setDisable(false);
		}
	}

	@FXML
	private void handleSaveClient() {
		if (!hasRequiredNameFields()) {
			Toast.show((Stage) businessNameField.getScene().getWindow(),
					"Business name and client name are required.");
			return;
		}

		boolean isEdit = (selectedClient != null);

		if (isEdit && editFormUnchangedFromBaseline()) {
			Stage stage = (Stage) businessNameField.getScene().getWindow();
			Toast.show(stage, "No changes to save.");
			return;
		}

		if (!isEdit) {
			selectedClient = new Client();
		}

		selectedClient.businessNameProperty().set(businessNameField.getText());
		selectedClient.clientNameProperty().set(clientNameField.getText());
		selectedClient.phoneProperty().set(phoneField.getText());
		selectedClient.altPhoneProperty().set(altPhoneField.getText());
		selectedClient.emailProperty().set(emailField.getText());
		selectedClient.gstProperty().set(gstField.getText());
		selectedClient.panProperty().set(panField.getText());
		try {
			selectedClient.setCreditLimit(Double.parseDouble(creditLimitField.getText()));
		} catch (Exception e) {
			selectedClient.setCreditLimit(0);
		}
		try {
			selectedClient.setOpeningBalance(Double.parseDouble(openingBalanceField.getText()));
		} catch (Exception e) {
			selectedClient.setOpeningBalance(0);
		}
		String billing = billingAddressField.getText() == null ? "" : billingAddressField.getText();
		selectedClient.billingAddressProperty().set(billing);
		if (syncAddressToggle != null && syncAddressToggle.isSelected()) {
			selectedClient.shippingAddressProperty().set(billing);
			shippingAddressField.setText(billing);
		} else {
			selectedClient.shippingAddressProperty().set(shippingAddressField.getText());
		}
		selectedClient.notesProperty().set(notesField.getText());

		boolean success = false;
		try {
			if (isEdit) {
				success = repo.update(selectedClient);
			} else {
				success = repo.save(selectedClient);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (success) {
			if (!isEdit) {
				String emailAddress = emailField.getText().trim();
				if (!emailAddress.isEmpty()) {
					utils.EmailUtil.sendWelcomeEmail(emailAddress, clientNameField.getText().trim());
				}
			}

			Stage stage = (Stage) businessNameField.getScene().getWindow();
			Toast.show(stage, isEdit ? "Client updated successfully!" : "Client registered successfully!");
			MainController.getInstance().loadViewClients();
		} else {
			Toast.show((Stage) businessNameField.getScene().getWindow(), "Failed to save client data.");
		}
	}

	@FXML
	private void handleCancel() {
		MainController.getInstance().loadEditClientSidebar();
	}
}
