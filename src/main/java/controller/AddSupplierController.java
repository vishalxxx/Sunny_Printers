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
    private boolean isUpdatingLocation = false;
    
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
        
        stateCombo.getItems().addAll(utils.GSTINValidator.ALL_STATES);
        if (stateCombo != null) {
            stateCombo.setEditable(true);
        }
        if (cityCombo != null) {
            cityCombo.setEditable(true);
        }
        
        stateCombo.valueProperty().addListener((obs, oldState, newState) -> {
            cityCombo.getItems().clear();
            if (newState != null) {
                String cleanState = newState;
                if (newState.contains("(")) {
                    cleanState = newState.substring(0, newState.indexOf('(')).trim();
                }
                switch (cleanState) {
                    case "Jammu & Kashmir":
                        cityCombo.getItems().addAll("Srinagar", "Jammu", "Anantnag", "Baramulla");
                        break;
                    case "Himachal Pradesh":
                        cityCombo.getItems().addAll("Shimla", "Dharamshala", "Solan", "Mandi");
                        break;
                    case "Punjab":
                        cityCombo.getItems().addAll("Ludhiana", "Amritsar", "Jalandhar", "Patiala", "Bathinda");
                        break;
                    case "Chandigarh":
                        cityCombo.getItems().addAll("Chandigarh");
                        break;
                    case "Uttarakhand":
                        cityCombo.getItems().addAll("Dehradun", "Haridwar", "Roorkee", "Haldwani");
                        break;
                    case "Haryana":
                        cityCombo.getItems().addAll("Gurugram", "Faridabad", "Panipat", "Ambala", "Rohtak");
                        break;
                    case "Delhi":
                        cityCombo.getItems().addAll("New Delhi", "Dwarka", "Rohini");
                        break;
                    case "Rajasthan":
                        cityCombo.getItems().addAll("Jaipur", "Jodhpur", "Udaipur", "Kota", "Ajmer");
                        break;
                    case "Uttar Pradesh":
                        cityCombo.getItems().addAll("Noida", "Lucknow", "Kanpur", "Agra", "Varanasi", "Ghaziabad", "Allahabad");
                        break;
                    case "Bihar":
                        cityCombo.getItems().addAll("Patna", "Gaya", "Bhagalpur", "Muzaffarpur");
                        break;
                    case "Sikkim":
                        cityCombo.getItems().addAll("Gangtok", "Namchi", "Geyzing");
                        break;
                    case "Arunachal Pradesh":
                        cityCombo.getItems().addAll("Itanagar", "Tawang", "Naharlagun");
                        break;
                    case "Nagaland":
                        cityCombo.getItems().addAll("Kohima", "Dimapur", "Mokokchung");
                        break;
                    case "Manipur":
                        cityCombo.getItems().addAll("Imphal", "Thoubal", "Churachandpur");
                        break;
                    case "Mizoram":
                        cityCombo.getItems().addAll("Aizawl", "Lunglei", "Champhai");
                        break;
                    case "Tripura":
                        cityCombo.getItems().addAll("Agartala", "Dharmanagar", "Udaipur");
                        break;
                    case "Meghalaya":
                        cityCombo.getItems().addAll("Shillong", "Tura", "Jowai");
                        break;
                    case "Assam":
                        cityCombo.getItems().addAll("Guwahati", "Dibrugarh", "Silchar", "Jorhat");
                        break;
                    case "West Bengal":
                        cityCombo.getItems().addAll("Kolkata", "Howrah", "Durgapur", "Siliguri", "Asansol");
                        break;
                    case "Jharkhand":
                        cityCombo.getItems().addAll("Ranchi", "Jamshedpur", "Dhanbad", "Bokaro");
                        break;
                    case "Odisha":
                        cityCombo.getItems().addAll("Bhubaneswar", "Cuttack", "Rourkela", "Sambalpur");
                        break;
                    case "Chhattisgarh":
                        cityCombo.getItems().addAll("Raipur", "Bhilai", "Bilaspur", "Korba");
                        break;
                    case "Madhya Pradesh":
                        cityCombo.getItems().addAll("Bhopal", "Indore", "Gwalior", "Jabalpur", "Ujjain");
                        break;
                    case "Gujarat":
                        cityCombo.getItems().addAll("Ahmedabad", "Surat", "Vadodara", "Rajkot", "Bhavnagar");
                        break;
                    case "Daman & Diu":
                        cityCombo.getItems().addAll("Daman", "Diu");
                        break;
                    case "Dadra & Nagar Haveli":
                        cityCombo.getItems().addAll("Silvassa");
                        break;
                    case "Maharashtra":
                        cityCombo.getItems().addAll("Mumbai", "Pune", "Nagpur", "Thane", "Nashik", "Aurangabad", "Navi Mumbai", "Solapur");
                        break;
                    case "Karnataka":
                        cityCombo.getItems().addAll("Bengaluru", "Mysore", "Hubli", "Mangalore", "Belgaum");
                        break;
                    case "Goa":
                        cityCombo.getItems().addAll("Panaji", "Margao", "Vasco da Gama");
                        break;
                    case "Lakshadweep":
                        cityCombo.getItems().addAll("Kavaratti");
                        break;
                    case "Kerala":
                        cityCombo.getItems().addAll("Thiruvananthapuram", "Kochi", "Kozhikode", "Thrissur");
                        break;
                    case "Tamil Nadu":
                        cityCombo.getItems().addAll("Chennai", "Coimbatore", "Madurai", "Trichy", "Salem");
                        break;
                    case "Puducherry":
                        cityCombo.getItems().addAll("Puducherry", "Karaikal");
                        break;
                    case "Andaman & Nicobar Islands":
                        cityCombo.getItems().addAll("Port Blair");
                        break;
                    case "Telangana":
                        cityCombo.getItems().addAll("Hyderabad", "Warangal", "Nizamabad", "Karimnagar");
                        break;
                    case "Andhra Pradesh (New)":
                    case "Andhra Pradesh":
                        cityCombo.getItems().addAll("Visakhapatnam", "Vijayawada", "Guntur", "Nellore", "Tirupati");
                        break;
                    case "Ladakh":
                        cityCombo.getItems().addAll("Leh", "Kargil");
                        break;
                    default:
                        break;
                }
            }
        });
        
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

        if (pincodeField != null) {
            pincodeField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (isUpdatingLocation) return;
                if (newVal != null && newVal.trim().length() == 6) {
                    String pin = newVal.trim();
                    String state = null;
                    String city = null;
                    if (pin.startsWith("4006")) { state = "Maharashtra"; city = "Thane"; }
                    else if (pin.startsWith("400")) { state = "Maharashtra"; city = "Mumbai"; }
                    else if (pin.startsWith("411")) { state = "Maharashtra"; city = "Pune"; }
                    else if (pin.startsWith("440")) { state = "Maharashtra"; city = "Nagpur"; }
                    else if (pin.startsWith("110")) { state = "Delhi"; city = "New Delhi"; }
                    else if (pin.startsWith("380")) { state = "Gujarat"; city = "Ahmedabad"; }
                    else if (pin.startsWith("560")) { state = "Karnataka"; city = "Bengaluru"; }
                    else if (pin.startsWith("600")) { state = "Tamil Nadu"; city = "Chennai"; }
                    else if (pin.startsWith("2013")) { state = "Uttar Pradesh"; city = "Noida"; }
                    else if (pin.startsWith("700")) { state = "West Bengal"; city = "Kolkata"; }
                    else if (pin.startsWith("500")) { state = "Telangana"; city = "Hyderabad"; }
                    else if (pin.startsWith("302")) { state = "Rajasthan"; city = "Jaipur"; }
                    else if (pin.startsWith("141")) { state = "Punjab"; city = "Ludhiana"; }
                    
                    if (state != null) {
                        isUpdatingLocation = true;
                        selectStateByName(state);
                        cityCombo.setValue(city);
                        isUpdatingLocation = false;
                    }
                }
            });
        }

        if (cityCombo != null) {
            cityCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (isUpdatingLocation) return;
                if (newVal != null) {
                    String pin = null;
                    switch (newVal) {
                        case "Mumbai": pin = "400001"; break;
                        case "Pune": pin = "411001"; break;
                        case "Nagpur": pin = "440001"; break;
                        case "Thane": pin = "400601"; break;
                        case "New Delhi": pin = "110001"; break;
                        case "Ahmedabad": pin = "380001"; break;
                        case "Bengaluru": pin = "560001"; break;
                        case "Chennai": pin = "600001"; break;
                        case "Noida": pin = "201301"; break;
                        case "Kolkata": pin = "700001"; break;
                        case "Hyderabad": pin = "500001"; break;
                        case "Jaipur": pin = "302001"; break;
                        case "Ludhiana": pin = "141001"; break;
                    }
                    if (pin != null) {
                        isUpdatingLocation = true;
                        pincodeField.setText(pin);
                        isUpdatingLocation = false;
                    }
                }
            });
        }

        if (creditLimitField != null) {
            creditLimitField.setDisable(true);
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

    private void selectStateByName(String stateName) {
        if (stateName == null || stateCombo == null) return;
        for (String item : stateCombo.getItems()) {
            String clean = item;
            if (item.contains("(")) {
                clean = item.substring(0, item.indexOf('(')).trim();
            }
            if (clean.equalsIgnoreCase(stateName)) {
                stateCombo.setValue(item);
                break;
            }
        }
    }
}
