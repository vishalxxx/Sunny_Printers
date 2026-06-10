package controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.application.Platform;
import javafx.scene.control.ListCell;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import model.EmailSettings;
import model.CompanyDetails;
import model.BankDetails;
import model.SupabaseSettings;
import repository.EmailSettingsRepository;
import repository.SupabaseSettingsRepository;
import service.BankDetailsService;
import service.CompanyDetailsService;
import utils.CompanyDataLayout;
import utils.CompanyProfile;
import utils.UniversalDownloadPath;
import utils.SupabaseRestProbe;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class GeneralSettingsController implements Initializable {

    @FXML private HBox breadcrumbContainer;
    @FXML private TextField smtpHostField;
    @FXML private TextField smtpPortField;
    @FXML private TextField senderEmailField;
    @FXML private PasswordField senderPasswordField;
    @FXML private ComboBox<CompanyDetails> companyCombo;
    @FXML private ComboBox<BankDetails> bankCombo;
    @FXML private TextArea companyAddressArea;
    @FXML private TextField companyPhoneField;
    @FXML private TextField companyEmailField;
    @FXML private TextField companyGstField;

    @FXML private TextField downloadPathField;
    @FXML private Button browseDownloadPathBtn;

    @FXML private TextField supabaseUrlField;
    @FXML private PasswordField supabaseAnonKeyField;
    @FXML private TextField supabaseEmailField;
    @FXML private PasswordField supabasePasswordField;
    @FXML private Button supabaseVerifyBtn;
    @FXML private Button supabaseSaveBtn;
    @FXML private Button supabaseSyncTestBtn;

    @FXML private Button saveBtn;
    @FXML private Button manageCompaniesBtn;
    @FXML private Button manageBanksBtn;

    private final EmailSettingsRepository repo = new EmailSettingsRepository();
    private final SupabaseSettingsRepository supabaseRepo = new SupabaseSettingsRepository();
    private final CompanyDetailsService companyService = new CompanyDetailsService();
    private final BankDetailsService bankService = new BankDetailsService();

    private CompanyDetails selectedCompany = null;
    private BankDetails selectedBank = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null,
                () -> MainController.getInstance().handleBack(null));
        loadSettings();
        saveBtn.setOnAction(e -> saveSettings());
        if (manageCompaniesBtn != null) {
            manageCompaniesBtn.setOnAction(e -> MainController.getInstance().loadCompanySettings());
        }
        if (manageBanksBtn != null) {
            manageBanksBtn.setOnAction(e -> MainController.getInstance().loadBankSettings());
        }
        if (browseDownloadPathBtn != null) {
            browseDownloadPathBtn.setOnAction(e -> browseDownloadPath());
        }
        wireSupabaseActions();
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
            loadSupabaseFields();
            setupCompanyCombo();
            setupBankCombo();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupCompanyCombo() {
        if (companyCombo == null) {
            return;
        }

        companyCombo.setButtonCell(new CompanyCell());
        companyCombo.setCellFactory(cb -> new CompanyCell());

        java.util.List<CompanyDetails> companies;
        try {
            companies = companyService.listActive();
        } catch (Exception e) {
            companies = java.util.List.of();
        }
        companyCombo.getItems().setAll(companies);

        CompanyDetails def = null;
        try {
            def = companyService.getDefault();
        } catch (Exception ignored) { }

        if (def != null) {
            companyCombo.getSelectionModel().select(def);
            applyCompanyToForm(def);
        } else if (!companies.isEmpty()) {
            companyCombo.getSelectionModel().select(0);
            applyCompanyToForm(companyCombo.getValue());
        } else {
            // fallback to Preferences (single company) if DB is empty
            applyCompanyProfileToForm();
        }

        companyCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                applyCompanyToForm(newV);
            }
        });
    }

    private void applyCompanyToForm(CompanyDetails c) {
        selectedCompany = c;
        if (companyAddressArea != null) companyAddressArea.setText(nz(c != null ? c.getAddress() : ""));
        if (companyPhoneField != null) companyPhoneField.setText(nz(c != null ? c.getPhone() : ""));
        if (companyEmailField != null) companyEmailField.setText(nz(c != null ? c.getEmail() : ""));
        if (companyGstField != null) companyGstField.setText(nz(c != null ? c.getGstin() : ""));
    }

    private void applyCompanyProfileToForm() {
        selectedCompany = null;
        if (companyAddressArea != null) companyAddressArea.setText(CompanyProfile.getAddress());
        if (companyPhoneField != null) companyPhoneField.setText(CompanyProfile.getPhone());
        if (companyEmailField != null) companyEmailField.setText(CompanyProfile.getEmail());
        if (companyGstField != null) companyGstField.setText(CompanyProfile.getGst());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static final class CompanyCell extends ListCell<CompanyDetails> {
        @Override
        protected void updateItem(CompanyDetails item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? "" : nz(item.getTradeName()));
        }
    }

    private void setupBankCombo() {
        if (bankCombo == null) {
            return;
        }

        bankCombo.setButtonCell(new BankCell());
        bankCombo.setCellFactory(cb -> new BankCell());

        java.util.List<BankDetails> banks;
        try {
            banks = bankService.listActive();
        } catch (Exception e) {
            banks = java.util.List.of();
        }
        bankCombo.getItems().setAll(banks);

        BankDetails def = null;
        try {
            def = bankService.getDefault();
        } catch (Exception ignored) { }

        if (def != null) {
            bankCombo.getSelectionModel().select(def);
            selectedBank = def;
        } else if (!banks.isEmpty()) {
            bankCombo.getSelectionModel().select(0);
            selectedBank = bankCombo.getValue();
        } else {
            selectedBank = null;
        }

        bankCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> selectedBank = newV);
    }

    private static final class BankCell extends ListCell<BankDetails> {
        @Override
        protected void updateItem(BankDetails item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? "" : nz(item.getBankName()));
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

    private void loadSupabaseFields() {
        if (supabaseUrlField == null) {
            return;
        }
        try {
            SupabaseSettings s = supabaseRepo.load();
            if (s != null) {
                supabaseUrlField.setText(nz(s.getSupabaseUrl()));
                if (supabaseAnonKeyField != null) {
                    supabaseAnonKeyField.setText(nz(s.getAnonKey()));
                }
                if (supabaseEmailField != null) {
                    supabaseEmailField.setText(nz(s.getAuthEmail()));
                }
                if (supabasePasswordField != null) {
                    supabasePasswordField.setText(s.getAuthPassword() != null ? s.getAuthPassword() : "");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveSupabaseFromForm() throws Exception {
        if (supabaseUrlField == null) {
            return;
        }
        SupabaseSettings s = new SupabaseSettings();
        s.setSupabaseUrl(supabaseUrlField.getText());
        if (supabaseAnonKeyField != null) {
            s.setAnonKey(supabaseAnonKeyField.getText());
        }
        if (supabaseEmailField != null) {
            s.setAuthEmail(supabaseEmailField.getText());
        }
        if (supabasePasswordField != null) {
            s.setAuthPassword(supabasePasswordField.getText());
        }
        supabaseRepo.save(s);
        service.sync.UniversalSyncEngine.schedulePullAsync();
    }

    private void wireSupabaseActions() {
        if (supabaseSaveBtn != null) {
            supabaseSaveBtn.setOnAction(e -> {
                try {
                    saveSupabaseFromForm();
                    showInfo("Supabase settings saved.");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showError("Failed to save Supabase settings", ex);
                }
            });
        }
        if (supabaseVerifyBtn != null) {
            supabaseVerifyBtn.setOnAction(e -> runSupabaseProbe(false));
        }
        if (supabaseSyncTestBtn != null) {
            supabaseSyncTestBtn.setOnAction(e -> runSupabaseProbe(true));
        }
    }

    private void runSupabaseProbe(boolean syncTest) {
        String url = supabaseUrlField != null ? supabaseUrlField.getText() : "";
        String key = supabaseAnonKeyField != null ? supabaseAnonKeyField.getText() : "";
        Button busy = syncTest ? supabaseSyncTestBtn : supabaseVerifyBtn;
        if (busy != null) {
            busy.setDisable(true);
        }
        new Thread(() -> {
            String msg = syncTest
                    ? SupabaseRestProbe.syncTest(url, key)
                    : SupabaseRestProbe.verifyConnection(url, key);
            Platform.runLater(() -> {
                if (busy != null) {
                    busy.setDisable(false);
                }
                boolean failed = msg.startsWith("FAILED");
                if (syncTest) {
                    showNotice(failed ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION, "Sync test", msg);
                } else if (failed) {
                    showNotice(Alert.AlertType.WARNING, "Supabase connection", msg);
                } else {
                    showInfo(msg);
                }
            });
        }, syncTest ? "supabase-sync-test" : "supabase-verify").start();
    }

    private void showNotice(Alert.AlertType type, String header, String content) {
        Alert alert = new Alert(type, content);
        alert.setHeaderText(header);
        alert.getDialogPane().getStyleClass().add("settings-warm-dialog");
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/settings_screens.css").toExternalForm());
        alert.show();
    }

    private void saveSettings() {
        try {
            EmailSettings settings = new EmailSettings();
            settings.setSmtpHost(smtpHostField.getText().trim());
            settings.setSmtpPort(smtpPortField.getText().trim());
            settings.setSenderEmail(senderEmailField.getText().trim());
            settings.setSenderPassword(senderPasswordField.getText().trim());

            repo.save(settings);

            // Set selected company as default (details are managed in Companies screen)
            CompanyDetails chosen = companyCombo != null ? companyCombo.getValue() : null;
            if (chosen != null) {
                companyService.setDefaultCompany(chosen.getId());
                setupCompanyCombo();
            }

            // Set selected bank as default (details are managed in Bank Details screen)
            BankDetails chosenBank = bankCombo != null ? bankCombo.getValue() : null;
            if (chosenBank != null) {
                bankService.setDefaultBank(chosenBank.getId());
                setupBankCombo();
            }

            if (downloadPathField != null) {
                String raw = downloadPathField.getText().trim();
                UniversalDownloadPath.set(raw);
                if (!raw.isEmpty()) {
                    new File(raw).mkdirs();
                }
            }

            CompanyDataLayout.ensureStandardFolders(CompanyDataLayout.getDataStoreRoot(), java.time.LocalDate.now());

            saveSupabaseFromForm();

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
