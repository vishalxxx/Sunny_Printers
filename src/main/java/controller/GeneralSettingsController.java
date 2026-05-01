package controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import model.EmailSettings;
import repository.EmailSettingsRepository;
import utils.CompanyDataLayout;
import utils.CompanyProfile;
import utils.UniversalDownloadPath;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class GeneralSettingsController implements Initializable {

    @FXML private HBox breadcrumbContainer;
    @FXML private TextField smtpHostField;
    @FXML private TextField smtpPortField;
    @FXML private TextField senderEmailField;
    @FXML private PasswordField senderPasswordField;
    @FXML private TextField companyNameField;
    @FXML private TextArea companyAddressArea;
    @FXML private TextField companyPhoneField;
    @FXML private TextField companyEmailField;
    @FXML private TextField companyGstField;

    @FXML private TextField downloadPathField;
    @FXML private Button browseDownloadPathBtn;

    @FXML private Button saveBtn;

    private final EmailSettingsRepository repo = new EmailSettingsRepository();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null,
                () -> MainController.getInstance().handleBack(null));
        loadSettings();
        saveBtn.setOnAction(e -> saveSettings());
        if (browseDownloadPathBtn != null) {
            browseDownloadPathBtn.setOnAction(e -> browseDownloadPath());
        }
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
            if (downloadPathField != null) {
                downloadPathField.setText(UniversalDownloadPath.get());
            }
            loadCompanyForm();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCompanyForm() {
        if (companyNameField != null) {
            companyNameField.setText(CompanyProfile.getName());
        }
        if (companyAddressArea != null) {
            companyAddressArea.setText(CompanyProfile.getAddress());
        }
        if (companyPhoneField != null) {
            companyPhoneField.setText(CompanyProfile.getPhone());
        }
        if (companyEmailField != null) {
            companyEmailField.setText(CompanyProfile.getEmail());
        }
        if (companyGstField != null) {
            companyGstField.setText(CompanyProfile.getGst());
        }
    }

    private void browseDownloadPath() {
        Window w = saveBtn != null && saveBtn.getScene() != null ? saveBtn.getScene().getWindow() : null;
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select default download folder");
        UniversalDownloadPath.prepareDirectoryChooser(dc);
        File current = UniversalDownloadPath.resolveInitialDirectory();
        if (current == null && downloadPathField != null && !downloadPathField.getText().isBlank()) {
            File tryDir = new File(downloadPathField.getText().trim());
            if (tryDir.isDirectory()) {
                dc.setInitialDirectory(tryDir);
            }
        }
        File chosen = dc.showDialog(w);
        if (chosen != null && downloadPathField != null) {
            downloadPathField.setText(chosen.getAbsolutePath());
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

            CompanyProfile.setName(textOrNull(companyNameField));
            CompanyProfile.setAddress(textOrNull(companyAddressArea));
            CompanyProfile.setPhone(textOrNull(companyPhoneField));
            CompanyProfile.setEmail(textOrNull(companyEmailField));
            CompanyProfile.setGst(textOrNull(companyGstField));

            if (downloadPathField != null) {
                String raw = downloadPathField.getText().trim();
                UniversalDownloadPath.set(raw);
                if (!raw.isEmpty()) {
                    new File(raw).mkdirs();
                }
            }

            CompanyDataLayout.ensureStandardFolders(CompanyDataLayout.getDataStoreRoot(), java.time.LocalDate.now());

            showInfo("Settings saved successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to save settings", e);
        }
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg);
        alert.setHeaderText("Success");
        alert.getDialogPane().getStyleClass().add("settings-warm-dialog");
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/settings_screens.css").toExternalForm());
        alert.show();
    }

    private static String textOrNull(TextInputControl field) {
        if (field == null) {
            return null;
        }
        String t = field.getText();
        return t != null ? t.trim() : null;
    }

    private void showError(String msg, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg + "\n" + e.getMessage());
        alert.setHeaderText("Error");
        alert.getDialogPane().getStyleClass().add("settings-warm-dialog");
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/settings_screens.css").toExternalForm());
        alert.show();
    }
}
