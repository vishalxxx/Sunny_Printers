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
import javafx.stage.Stage;
import model.Supplier;
import service.SupplierService;
import utils.Toast;

public class AddSupplierController implements Initializable {

    @FXML private HBox breadcrumbContainer;
    
    // Basic Info Fields
    @FXML private TextField supplierNameField;
    @FXML private TextField supplierCodeField;
    @FXML private TextField businessNameField;
    @FXML private TextField gstinField;
    @FXML private TextField mobileField;
    @FXML private TextField phoneField;
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
        if (!isEdit) {
            selectedSupplier = new Supplier();
        }
        
        selectedSupplier.setName(name.trim());
        selectedSupplier.setSupplierCode(supplierCodeField.getText() == null ? "" : supplierCodeField.getText().trim());
        selectedSupplier.setbusinessName(businessNameField.getText() == null ? "" : businessNameField.getText().trim());
        selectedSupplier.setGstNumber(gstinField.getText() == null ? "" : gstinField.getText().trim());
        selectedSupplier.setMobile(mobileField.getText() == null ? "" : mobileField.getText().trim());
        selectedSupplier.setPhone(phoneField.getText() == null ? "" : phoneField.getText().trim());
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
}
