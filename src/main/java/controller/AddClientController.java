package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class AddClientController {

    @FXML private TextField clientNameField;
    @FXML private TextField clientContactField;
    @FXML private TextField clientEmailField;
    @FXML private TextArea clientAddressField;

    @FXML
    public void initialize() {
        // optional init code
    }

    @FXML
    public void handleSaveClient() {
        String name = clientNameField.getText();
        String contact = clientContactField.getText();
        if (name == null || name.isBlank() || contact == null || contact.isBlank()) {
            showAlert(Alert.AlertType.ERROR, "Validation", "Name and Contact are required.");
            return;
        }
        // For now just show success — hook to DAO later
        showAlert(Alert.AlertType.INFORMATION, "Saved", "Client saved (stub).");
        clientNameField.clear();
        clientContactField.clear();
        clientEmailField.clear();
        clientAddressField.clear();
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
