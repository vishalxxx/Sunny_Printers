package controller;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.Supplier;
import service.SupplierService;
import utils.Toast;
import utils.GSTINValidator;
import utils.PhoneValidator;

public class AddSupplierController implements Initializable {

    @FXML private HBox breadcrumbContainer;
    
    // Basic Info Fields
    @FXML private TextField supplierNameField;
    @FXML private TextField supplierCodeField;
    @FXML private TextField businessNameField;
    @FXML private TextField gstinField;
    @FXML private Label lblGstValidation;
    @FXML private VBox panGstDetails;
    @FXML private Label lblGstDetailsGstin;
    @FXML private Label lblGstDetailsState;
    @FXML private Label lblGstDetailsPan;
    @FXML private Label lblGstDetailsType;
    @FXML private Label lblGstDetailsStatus;

    @FXML private TextField mobileField;
    @FXML private Label lblPhoneValidation;

    @FXML private TextField phoneField;
    @FXML private Label lblAltPhoneValidation;

    @FXML private TextField emailField;
    @FXML private TextField websiteField;
    
    // Address Fields
    @FXML private TextArea addressField;
    @FXML private ComboBox<String> stateCombo;
    @FXML private ComboBox<String> cityCombo;
    @FXML private TextField pincodeField;
    
    // Additional Info Fields
    @FXML private ComboBox<String> supplierTypeCombo;
    @FXML private ComboBox<String> paymentTermsCombo;
    @FXML private TextField creditLimitField;
    @FXML private TextArea notesField;
    
    // Overview Labels
    @FXML private Label lblCreatedBy;
    @FXML private Label lblCreatedOn;
    @FXML private Label lblLastUpdated;
    
    private final SupplierService supplierService = new SupplierService();
    private Supplier selectedSupplier;
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Populate breadcrumbs
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, "Add Supplier", () -> handleCancel());
        
        // Setup dropdown options
        supplierTypeCombo.setItems(FXCollections.observableArrayList(
            "Paper", "CTP", "Binding", "Lamination", "Ink", "Plates", "Packaging", "Digital", "Other"
        ));
        
        paymentTermsCombo.setItems(FXCollections.observableArrayList(
            "Cash on Delivery", "Advance Payment", "7 Days", "15 Days", "30 Days", "45 Days", "60 Days"
        ));
        
        stateCombo.setItems(FXCollections.observableArrayList(
            "Maharashtra", "Delhi", "Gujarat", "Karnataka", "Tamil Nadu", "Uttar Pradesh", "West Bengal", "Telangana", "Rajasthan", "Punjab"
        ));
        
        cityCombo.setItems(FXCollections.observableArrayList(
            "Mumbai", "New Delhi", "Ahmedabad", "Bengaluru", "Chennai", "Noida", "Kolkata", "Hyderabad", "Jaipur", "Ludhiana"
        ));
        
        // Initial setup for default user session info if available
        utils.SessionManager session = utils.SessionManager.getInstance();
        if (session != null && session.getCurrentUser() != null) {
            lblCreatedBy.setText(session.getCurrentUser().getUsername());
        }
        
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy");
        lblCreatedOn.setText(java.time.LocalDate.now().format(dtf));
        
        if (gstinField != null) {
            gstinField.textProperty().addListener((obs, oldVal, newVal) -> validateGstinRealtime(newVal));
        }
        if (mobileField != null) {
            mobileField.textProperty().addListener((obs, oldVal, newVal) -> validatePhoneRealtime(newVal));
        }
        if (phoneField != null) {
            phoneField.textProperty().addListener((obs, oldVal, newVal) -> validateAltPhoneRealtime(newVal));
        }

        // Clear fields and set initial code to SUP-NEW
        setSupplierData(null);
    }
    
    public void setSupplierData(Supplier s) {
        this.selectedSupplier = s;
        if (s == null) {
            supplierCodeField.setText("SUP-NEW");
            utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, "Add Supplier", () -> handleCancel());
            clearFields();
            return;
        }
        
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, "Edit Supplier", () -> handleCancel());
        supplierNameField.setText(s.getName());
        String code = s.getSupplierCode();
        if (code == null || code.isBlank()) {
            code = s.getUuid() != null && s.getUuid().length() > 8 ? s.getUuid().substring(0, 8).toUpperCase() : s.getUuid();
        }
        supplierCodeField.setText(code);
        businessNameField.setText(s.getbusinessName());
        gstinField.setText(s.getGstNumber());
        mobileField.setText(s.getMobile());
        phoneField.setText(s.getPhone());
        emailField.setText(s.getEmail());
        websiteField.setText(s.getWebsite());
        addressField.setText(s.getAddress());
        stateCombo.setValue(s.getState());
        cityCombo.setValue(s.getCity());
        pincodeField.setText(s.getPincode());
        supplierTypeCombo.setValue(s.getType());
        paymentTermsCombo.setValue(s.getPaymentTerms());
        creditLimitField.setText(String.valueOf(s.getCreditLimit()));
        notesField.setText(s.getNotes());
        
        lblLastUpdated.setText(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")));
    }
    
    private void clearFields() {
        supplierNameField.clear();
        businessNameField.clear();
        gstinField.clear();
        mobileField.clear();
        phoneField.clear();
        emailField.clear();
        websiteField.clear();
        addressField.clear();
        stateCombo.setValue(null);
        cityCombo.setValue(null);
        pincodeField.clear();
        supplierTypeCombo.setValue(null);
        paymentTermsCombo.setValue(null);
        creditLimitField.setText("0.00");
        notesField.clear();
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
    
    @FXML
    private void handleCancel() {
        MainController.getInstance().loadViewSuppliers();
    }
    
    @FXML
    private void handleUploadLogo() {
        Stage stage = (Stage) supplierNameField.getScene().getWindow();
        Toast.show(stage, "Logo uploading feature is simulated.");
    }
    
    @FXML
    private void handleSaveSupplier() {
        String name = supplierNameField.getText();
        if (name == null || name.trim().isEmpty()) {
            Stage stage = (Stage) supplierNameField.getScene().getWindow();
            Toast.show(stage, "Supplier Name is required.");
            return;
        }
        
        boolean isEdit = (selectedSupplier != null);
        String excludeUuid = isEdit ? selectedSupplier.getUuid() : null;

        // Mobile Validation
        String mobileVal = mobileField.getText() == null ? "" : mobileField.getText().trim();
        if (!mobileVal.isEmpty()) {
            if (!PhoneValidator.isValidMobile(mobileVal)) {
                Toast.show((Stage) mobileField.getScene().getWindow(), "Invalid Mobile Number");
                return;
            }
            if (supplierService.duplicateMobileExists(mobileVal, excludeUuid)) {
                Toast.show((Stage) mobileField.getScene().getWindow(), "Duplicate Mobile Number");
                return;
            }
        }

        // Alternate Phone/Landline Validation
        String phoneVal = phoneField.getText() == null ? "" : phoneField.getText().trim();
        if (!phoneVal.isEmpty()) {
            if (!PhoneValidator.isValidLandline(phoneVal)) {
                Toast.show((Stage) phoneField.getScene().getWindow(), "Invalid Alternate/Landline Number");
                return;
            }
        }

        // GSTIN Validation
        String gstinVal = gstinField.getText() == null ? "" : gstinField.getText().trim().toUpperCase();
        if (!gstinVal.isEmpty()) {
            if (!GSTINValidator.isFormatValid(gstinVal)) {
                Toast.show((Stage) gstinField.getScene().getWindow(), "GSTIN Format Invalid");
                return;
            }
            if (!GSTINValidator.isStateCodeValid(gstinVal)) {
                Toast.show((Stage) gstinField.getScene().getWindow(), "Invalid State Code");
                return;
            }
            if (!GSTINValidator.isChecksumValid(gstinVal)) {
                Toast.show((Stage) gstinField.getScene().getWindow(), "GSTIN Checksum Invalid");
                return;
            }
            if (supplierService.duplicateGstinExists(gstinVal, excludeUuid)) {
                Toast.show((Stage) gstinField.getScene().getWindow(), "Duplicate GSTIN");
                return;
            }

            // Consistency check with stateCombo
            String selectedState = stateCombo.getValue();
            if (selectedState != null && !selectedState.isBlank()) {
                String gstinStateCode = gstinVal.substring(0, 2);
                String expectedStateStr = GSTINValidator.getStateByCode(gstinStateCode);
                if (expectedStateStr == null || !expectedStateStr.contains(selectedState)) {
                    Toast.show((Stage) gstinField.getScene().getWindow(), "State Mismatch");
                    return;
                }
            }
        }
        
        if (!isEdit) {
            selectedSupplier = new Supplier();
        }
        
        selectedSupplier.setName(name.trim());
        selectedSupplier.setSupplierCode(supplierCodeField.getText() == null ? "" : supplierCodeField.getText().trim());
        selectedSupplier.setbusinessName(businessNameField.getText() == null ? "" : businessNameField.getText().trim());
        selectedSupplier.setGstNumber(gstinVal);
        selectedSupplier.setMobile(mobileVal);
        selectedSupplier.setPhone(phoneVal);
        selectedSupplier.setEmail(emailField.getText() == null ? "" : emailField.getText().trim());
        selectedSupplier.setWebsite(websiteField.getText() == null ? "" : websiteField.getText().trim());
        selectedSupplier.setAddress(addressField.getText() == null ? "" : addressField.getText().trim());
        selectedSupplier.setState(stateCombo.getValue() == null ? "" : stateCombo.getValue());
        selectedSupplier.setCity(cityCombo.getValue() == null ? "" : cityCombo.getValue());
        selectedSupplier.setPincode(pincodeField.getText() == null ? "" : pincodeField.getText().trim());
        selectedSupplier.setType(supplierTypeCombo.getValue() == null ? "Other" : supplierTypeCombo.getValue());
        selectedSupplier.setPaymentTerms(paymentTermsCombo.getValue() == null ? "" : paymentTermsCombo.getValue());
        
        try {
            double cl = Double.parseDouble(creditLimitField.getText() == null || creditLimitField.getText().isBlank() ? "0" : creditLimitField.getText().trim());
            selectedSupplier.setCreditLimit(cl);
        } catch (NumberFormatException e) {
            selectedSupplier.setCreditLimit(0);
        }
        selectedSupplier.setNotes(notesField.getText() == null ? "" : notesField.getText().trim());
        
        try {
            if (isEdit) {
                supplierService.updateSupplier(selectedSupplier);
                Toast.show((Stage) supplierNameField.getScene().getWindow(), "Supplier updated successfully!");
            } else {
                supplierService.addSupplier(selectedSupplier);
                Toast.show((Stage) supplierNameField.getScene().getWindow(), "Supplier registered successfully!");
            }
            MainController.getInstance().loadViewSuppliers();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.show((Stage) supplierNameField.getScene().getWindow(), "Error saving supplier details.");
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
        if (!GSTINValidator.isFormatValid(clean)) {
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
        if (!GSTINValidator.isStateCodeValid(clean)) {
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
        String stateVal = GSTINValidator.getStateByCode(code);
        if (stateVal != null) {
            int idx = stateVal.indexOf(" (");
            String cleanState = idx != -1 ? stateVal.substring(0, idx) : stateVal;
            if (stateCombo != null) {
                stateCombo.setValue(cleanState);
            }
        }

        if (!GSTINValidator.isChecksumValid(clean)) {
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
        boolean isEdit = (selectedSupplier != null);
        boolean duplicate = supplierService.duplicateGstinExists(clean, isEdit ? selectedSupplier.getUuid() : null);
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
        }

        // Show dynamic extraction panel
        if (panGstDetails != null) {
            if (lblGstDetailsGstin != null) lblGstDetailsGstin.setText(clean);
            if (lblGstDetailsState != null) lblGstDetailsState.setText(stateVal != null ? stateVal : "Unknown (" + code + ")");
            if (lblGstDetailsPan != null) lblGstDetailsPan.setText(panVal);
            if (lblGstDetailsType != null) lblGstDetailsType.setText(GSTINValidator.getRegistrationType(clean));
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
        String clean = PhoneValidator.sanitize(newVal);
        
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
        boolean isEdit = (selectedSupplier != null);
        boolean duplicate = supplierService.duplicateMobileExists(clean, isEdit ? selectedSupplier.getUuid() : null);
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
        String clean = PhoneValidator.sanitize(newVal);
        
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
}
