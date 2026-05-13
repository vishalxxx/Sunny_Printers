package controller;

import java.net.URL;
import java.util.ResourceBundle;

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
	@FXML
	private ToggleButton syncAddressToggle;

	private final ClientRepository repo = new ClientRepository();

	@Override
	public void initialize(URL url, ResourceBundle resourceBundle) {
        // Initial population
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null, () -> handleBack(null));
		setupAddressSyncToggle();
	}

    @FXML
    private void handleBack(javafx.event.Event e) {
        MainController.getInstance().handleBack(e);
    }

	// This method is called from ViewClientsController or MainController
	public void setClientData(Client client) {
		this.selectedClient = client;

        if (client == null) {
            // "ADD MODE"
            if (formShell != null) {
                if (!formShell.getStyleClass().contains("client-form--add")) {
                    formShell.getStyleClass().add("client-form--add");
                }
            }
            if (pageTitleBlock != null) {
                pageTitleBlock.setVisible(false);
                pageTitleBlock.setManaged(false);
            }
            if (lblClientId != null) lblClientId.setText("#CL-NEW");
            utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null, () -> handleBack(null));
            if (btnSave != null) btnSave.setText("Register Client");
            
            clearFields();
            return;
        }

        // "EDIT MODE"
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
        if (lblClientId != null) lblClientId.setText("#CL-" + client.getId());
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, "Edit Client", () -> handleBack(null));
        if (btnSave != null) btnSave.setText("Update Profile");

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

		applyAddressSyncUiFromLoadedAddresses();
	}

	private static String normalizeAddress(String s) {
		return s == null ? "" : s.trim();
	}

	/** After billing/shipping fields are filled, turn sync on when they match (edit profile). */
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
        nickNameField.clear();
        phoneField.clear();
        altPhoneField.clear();
        emailField.clear();
        gstField.clear();
        panField.clear();
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
        boolean isEdit = (selectedClient != null);
        
        if (!isEdit) {
            selectedClient = new Client();
        }

		// update fields
		selectedClient.businessNameProperty().set(businessNameField.getText());
		selectedClient.clientNameProperty().set(clientNameField.getText());
		selectedClient.nickNameProperty().set(nickNameField.getText());
		selectedClient.phoneProperty().set(phoneField.getText());
		selectedClient.altPhoneProperty().set(altPhoneField.getText());
		selectedClient.emailProperty().set(emailField.getText());
		selectedClient.gstProperty().set(gstField.getText());
		selectedClient.panProperty().set(panField.getText());
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
            // Send Welcome Email if it's a new registration
            if (!isEdit) {
                String emailAddress = emailField.getText().trim();
                if (!emailAddress.isEmpty()) {
                    utils.EmailUtil.sendWelcomeEmail(emailAddress, clientNameField.getText().trim());
                }
            }

			Stage stage = (Stage) businessNameField.getScene().getWindow();
			Toast.show(stage, isEdit ? "Client updated successfully!" : "Client registered successfully!");

			// Reload View Clients Table
			MainController.getInstance().loadViewClients();
		} else {
            Toast.show((Stage) businessNameField.getScene().getWindow(), "Failed to save client data.");
		}
	}

	@FXML
	private void handleCancel() {
		// Just navigate back to selection without showing any toast
		MainController.getInstance().loadEditClientSidebar();
	}

}
