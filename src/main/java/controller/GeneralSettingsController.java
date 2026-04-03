package controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import model.EmailSettings;
import repository.EmailSettingsRepository;

import java.net.URL;
import java.util.ResourceBundle;

public class GeneralSettingsController implements Initializable {

    @FXML private TextField smtpHostField;
    @FXML private TextField smtpPortField;
    @FXML private TextField senderEmailField;
    @FXML private PasswordField senderPasswordField;

    @FXML private Button saveBtn;

    private final EmailSettingsRepository repo = new EmailSettingsRepository();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        loadSettings();
        saveBtn.setOnAction(e -> saveSettings());
    }

    private void loadSettings() {
        try {
            EmailSettings settings = repo.load();
            if (settings != null) {
                smtpHostField.setText(settings.getSmtpHost());
                smtpPortField.setText(settings.getSmtpPort());
                senderEmailField.setText(settings.getSenderEmail());
                senderPasswordField.setText(settings.getSenderPassword());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveSettings() {
        try {
            EmailSettings settings = new EmailSettings();
            settings.setSmtpHost(smtpHostField.getText().trim());
            settings.setSmtpPort(smtpPortField.getText().trim());
            settings.setSenderEmail(senderEmailField.getText().trim());
            settings.setSenderPassword(senderPasswordField.getText().trim());

            repo.save(settings);
            showInfo("Email settings saved safely!");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to save email settings", e);
        }
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg);
        alert.setHeaderText("Success");
        alert.show();
    }

    private void showError(String msg, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg + "\n" + e.getMessage());
        alert.setHeaderText("Error");
        alert.show();
    }
}
