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
import javafx.scene.control.ComboBox;
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
			String gst, String pan, String state, String billing, String shipping, String notes, double creditLimit, double openingBalance) {
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
	private Label lblPhoneValidation;
	@FXML
	private TextField altPhoneField;
	@FXML
	private Label lblAltPhoneValidation;
	@FXML
	private TextField emailField;
	@FXML
	private TextField gstField;
	@FXML
	private Label lblGstValidation;
	@FXML
	private VBox panGstDetails;
	@FXML
	private Label lblGstDetailsGstin;
	@FXML
	private Label lblGstDetailsState;
	@FXML
	private Label lblGstDetailsPan;
	@FXML
	private Label lblGstDetailsType;
	@FXML
	private Label lblGstDetailsStatus;
	@FXML
	private ComboBox<String> stateCombo;
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
		if (stateCombo != null) {
			stateCombo.getItems().addAll(utils.GSTINValidator.ALL_STATES);
		}
		if (gstField != null) {
			gstField.textProperty().addListener((obs, oldVal, newVal) -> validateGstinRealtime(newVal));
		}
		if (phoneField != null) {
			phoneField.textProperty().addListener((obs, oldVal, newVal) -> validatePhoneRealtime(newVal));
		}
		if (altPhoneField != null) {
			altPhoneField.textProperty().addListener((obs, oldVal, newVal) -> validateAltPhoneRealtime(newVal));
		}
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
				nz(client.getPan()), nz(client.getState()), nz(client.getBillingAddress()), nz(client.getShippingAddress()),
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
		if (stateCombo != null) {
			stateCombo.setValue(client.getState());
		}
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
		try { cl = Double.parseDouble(creditLimitField.getText()); } catch (Exception e) { service.LoggerService.debug("Failed to parse credit limit: " + e.getMessage()); }
		double ob = 0;
		try { ob = Double.parseDouble(openingBalanceField.getText()); } catch (Exception e) { service.LoggerService.debug("Failed to parse opening balance: " + e.getMessage()); }
		return nz(businessNameField.getText()).equals(editBaseline.businessName())
				&& nz(clientNameField.getText()).equals(editBaseline.clientName())
				&& nz(phoneField.getText()).equals(editBaseline.phone())
				&& nz(altPhoneField.getText()).equals(editBaseline.altPhone())
				&& nz(emailField.getText()).equals(editBaseline.email())
				&& nz(gstField.getText()).equals(editBaseline.gst()) && nz(panField.getText()).equals(editBaseline.pan())
				&& nz(stateCombo != null ? stateCombo.getValue() : "").equals(editBaseline.state())
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
		if (stateCombo != null) {
			stateCombo.setValue(null);
		}
		if (lblGstValidation != null) {
			lblGstValidation.setText("");
		}
		if (panGstDetails != null) {
			panGstDetails.setVisible(false);
			panGstDetails.setManaged(false);
		}
		if (lblPhoneValidation != null) {
			lblPhoneValidation.setText("");
		}
		if (lblAltPhoneValidation != null) {
			lblAltPhoneValidation.setText("");
		}
	}

	private void validateGstinRealtime(String newVal) {
		if (newVal == null || newVal.trim().isEmpty()) {
			if (lblGstValidation != null) {
				lblGstValidation.setText("");
			}
			if (panGstDetails != null) {
				panGstDetails.setVisible(false);
				panGstDetails.setManaged(false);
			}
			return;
		}
		String clean = newVal.trim().toUpperCase();
		if (!utils.GSTINValidator.isFormatValid(clean)) {
			if (lblGstValidation != null) {
				lblGstValidation.setText("✗ GSTIN Format Invalid");
				lblGstValidation.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
			}
			if (panGstDetails != null) {
				panGstDetails.setVisible(false);
				panGstDetails.setManaged(false);
			}
			return;
		}
		if (!utils.GSTINValidator.isStateCodeValid(clean)) {
			if (lblGstValidation != null) {
				lblGstValidation.setText("✗ Invalid State Code");
				lblGstValidation.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
			}
			if (panGstDetails != null) {
				panGstDetails.setVisible(false);
				panGstDetails.setManaged(false);
			}
			return;
		}
		
		// Auto-populate state
		String code = clean.substring(0, 2);
		String stateVal = utils.GSTINValidator.getStateByCode(code);
		if (stateVal != null && stateCombo != null) {
			stateCombo.setValue(stateVal);
		}

		if (!utils.GSTINValidator.isChecksumValid(clean)) {
			if (lblGstValidation != null) {
				lblGstValidation.setText("✗ GSTIN Checksum Invalid");
				lblGstValidation.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
			}
			if (panGstDetails != null) {
				panGstDetails.setVisible(false);
				panGstDetails.setManaged(false);
			}
			return;
		}

		// Check duplicate
		boolean isEdit = (selectedClient != null);
		boolean duplicate = repo.duplicateGstinExists(clean, isEdit ? selectedClient.getClientUuid() : null);
		if (duplicate) {
			if (lblGstValidation != null) {
				lblGstValidation.setText("✗ Duplicate GSTIN");
				lblGstValidation.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
			}
			if (panGstDetails != null) {
				panGstDetails.setVisible(false);
				panGstDetails.setManaged(false);
			}
			return;
		}

		if (lblGstValidation != null) {
			lblGstValidation.setText("✓ GSTIN Format Valid\n✓ GSTIN Checksum Valid");
			lblGstValidation.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 11px;");
		}

		// Auto-extract PAN (characters 3-12)
		String panVal = "";
		if (clean.length() >= 12) {
			panVal = clean.substring(2, 12);
			if (panField != null) {
				panField.setText(panVal);
			}
		}

		// Show dynamic extraction panel
		if (panGstDetails != null) {
			if (lblGstDetailsGstin != null) lblGstDetailsGstin.setText(clean);
			if (lblGstDetailsState != null) lblGstDetailsState.setText(stateVal != null ? stateVal : "Unknown (" + code + ")");
			if (lblGstDetailsPan != null) lblGstDetailsPan.setText(panVal);
			if (lblGstDetailsType != null) lblGstDetailsType.setText(utils.GSTINValidator.getRegistrationType(clean));
			if (lblGstDetailsStatus != null) lblGstDetailsStatus.setText("✓ Active / Offline Verified");
			panGstDetails.setVisible(true);
			panGstDetails.setManaged(true);
		}
	}

	private void validatePhoneRealtime(String newVal) {
		if (newVal == null || newVal.trim().isEmpty()) {
			if (lblPhoneValidation != null) {
				lblPhoneValidation.setText("");
			}
			return;
		}
		String clean = utils.PhoneValidator.sanitize(newVal);
		
		// Digit check
		if (!clean.matches("[0-9]+")) {
			if (lblPhoneValidation != null) {
				lblPhoneValidation.setText("✗ Must consist of only digits");
				lblPhoneValidation.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
			}
			return;
		}
		
		// Starting digit check
		char first = clean.charAt(0);
		if (first < '6' || first > '9') {
			if (lblPhoneValidation != null) {
				lblPhoneValidation.setText("✗ Invalid starting digit");
				lblPhoneValidation.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
			}
			return;
		}

		// Length check
		if (clean.length() != 10) {
			if (lblPhoneValidation != null) {
				lblPhoneValidation.setText("✗ Must be 10 digits");
				lblPhoneValidation.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
			}
			return;
		}

		// Duplicate mobile check
		boolean isEdit = (selectedClient != null);
		boolean duplicate = repo.duplicateMobileExists(clean, isEdit ? selectedClient.getClientUuid() : null);
		if (duplicate) {
			if (lblPhoneValidation != null) {
				lblPhoneValidation.setText("✗ Duplicate mobile number");
				lblPhoneValidation.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
			}
			return;
		}

		if (lblPhoneValidation != null) {
			lblPhoneValidation.setText("✓ Valid Mobile Number");
			lblPhoneValidation.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 11px;");
		}
	}

	private void validateAltPhoneRealtime(String newVal) {
		if (newVal == null || newVal.trim().isEmpty()) {
			if (lblAltPhoneValidation != null) {
				lblAltPhoneValidation.setText("");
			}
			return;
		}
		String clean = utils.PhoneValidator.sanitize(newVal);
		
		// Digit check
		if (!clean.matches("[0-9]+")) {
			if (lblAltPhoneValidation != null) {
				lblAltPhoneValidation.setText("✗ Only digits allowed");
				lblAltPhoneValidation.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
			}
			return;
		}

		// Length check
		if (clean.length() < 8 || clean.length() > 15) {
			if (lblAltPhoneValidation != null) {
				lblAltPhoneValidation.setText("✗ Length must be between 8 and 15 digits");
				lblAltPhoneValidation.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px;");
			}
			return;
		}

		if (lblAltPhoneValidation != null) {
			lblAltPhoneValidation.setText("✓ Valid Alternate Phone");
			lblAltPhoneValidation.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 11px;");
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

		// Mobile Validation
		String mobileVal = phoneField.getText() == null ? "" : phoneField.getText().trim();
		if (!mobileVal.isEmpty()) {
			if (!utils.PhoneValidator.isValidMobile(mobileVal)) {
				Toast.show((Stage) phoneField.getScene().getWindow(), "Invalid Mobile Number");
				return;
			}
			if (repo.duplicateMobileExists(mobileVal, isEdit ? selectedClient.getClientUuid() : null)) {
				Toast.show((Stage) phoneField.getScene().getWindow(), "Duplicate Mobile Number");
				return;
			}
		}

		// Landline/Alternate Validation
		String landlineVal = altPhoneField.getText() == null ? "" : altPhoneField.getText().trim();
		if (!landlineVal.isEmpty()) {
			if (!utils.PhoneValidator.isValidLandline(landlineVal)) {
				Toast.show((Stage) altPhoneField.getScene().getWindow(), "Invalid Alternate/Landline Number");
				return;
			}
		}

		String gstinVal = gstField.getText() == null ? "" : gstField.getText().trim().toUpperCase();
		if (!gstinVal.isEmpty()) {
			if (!utils.GSTINValidator.isFormatValid(gstinVal)) {
				Toast.show((Stage) gstField.getScene().getWindow(), "GSTIN Format Invalid");
				return;
			}
			if (!utils.GSTINValidator.isStateCodeValid(gstinVal)) {
				Toast.show((Stage) gstField.getScene().getWindow(), "Invalid State Code");
				return;
			}
			if (!utils.GSTINValidator.isChecksumValid(gstinVal)) {
				Toast.show((Stage) gstField.getScene().getWindow(), "GSTIN Checksum Invalid");
				return;
			}
			
			// Duplicate check
			if (repo.duplicateGstinExists(gstinVal, isEdit ? selectedClient.getClientUuid() : null)) {
				Toast.show((Stage) gstField.getScene().getWindow(), "Duplicate GSTIN");
				return;
			}

			// Consistency check
			String selectedState = stateCombo != null ? stateCombo.getValue() : null;
			if (selectedState == null || selectedState.isBlank()) {
				Toast.show((Stage) gstField.getScene().getWindow(), "Please select a state.");
				return;
			}
			String gstinStateCode = gstinVal.substring(0, 2);
			String expectedStateStr = utils.GSTINValidator.getStateByCode(gstinStateCode);
			if (expectedStateStr == null || !expectedStateStr.equals(selectedState)) {
				Toast.show((Stage) gstField.getScene().getWindow(), "State Mismatch");
				return;
			}
		}

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
		selectedClient.setState(stateCombo != null ? stateCombo.getValue() : "");
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
