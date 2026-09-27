package controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.application.Platform;
import javafx.scene.control.ListCell;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import model.EmailSettings;
import model.CompanyDetails;
import model.BankDetails;
import model.SupabaseSettings;
import model.User;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import javafx.concurrent.Task;
import repository.EmailSettingsRepository;
import repository.SupabaseSettingsRepository;
import service.BankDetailsService;
import service.CompanyDetailsService;
import utils.CompanyDataLayout;
import utils.CompanyProfile;
import utils.UniversalDownloadPath;
import utils.SupabaseRestProbe;
import javafx.scene.image.ImageView;

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

    @FXML private ImageView signaturePreview;
    @FXML private Button btnUploadSignature;
    @FXML private Button btnRemoveSignature;

    @FXML private TextField supabaseUrlField;
    @FXML private PasswordField supabaseAnonKeyField;
    @FXML private TextField supabaseEmailField;
    @FXML private PasswordField supabasePasswordField;
    @FXML private Button supabaseVerifyBtn;
    @FXML private Button supabaseSaveBtn;
    @FXML private Button supabaseSyncTestBtn;

    @FXML private Button saveBtn;
    @FXML private Button resetDbBtn;
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
        if (resetDbBtn != null) {
            boolean admin = !isReadOnly();
            resetDbBtn.setVisible(admin);
            resetDbBtn.setManaged(admin);
            if (admin) {
                resetDbBtn.setOnAction(e -> handleResetDatabase());
            }
        }
        if (manageCompaniesBtn != null) {
            manageCompaniesBtn.setOnAction(e -> MainController.getInstance().loadCompanySettings());
        }
        if (manageBanksBtn != null) {
            manageBanksBtn.setOnAction(e -> MainController.getInstance().loadBankSettings());
        }
        if (browseDownloadPathBtn != null) {
            browseDownloadPathBtn.setOnAction(e -> browseDownloadPath());
        }
        if (btnUploadSignature != null) {
            btnUploadSignature.setOnAction(e -> uploadSignature());
        }
        if (btnRemoveSignature != null) {
            btnRemoveSignature.setOnAction(e -> removeSignature());
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
            loadSignaturePreview();
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
        } catch (Exception e) { service.LoggerService.dbWarn("Failed to load default company for settings: " + e.getMessage()); }

        if (def != null) {
            companyCombo.getSelectionModel().select(def);
            applyCompanyToForm(def);
        } else if (!companies.isEmpty()) {
            companyCombo.getSelectionModel().select(0);
            applyCompanyToForm(companyCombo.getValue());
        } else {
            // Clear all fields if no company is registered
            if (companyAddressArea != null) companyAddressArea.setText("");
            if (companyPhoneField != null) companyPhoneField.setText("");
            if (companyEmailField != null) companyEmailField.setText("");
            if (companyGstField != null) companyGstField.setText("");
            if (downloadPathField != null) downloadPathField.setText("");
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
        } catch (Exception e) { service.LoggerService.dbWarn("Failed to load default bank for settings: " + e.getMessage()); }

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
        Alert alert = new Alert(type);
        alert.setHeaderText(header);
        
        TextArea textArea = new TextArea(content);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        
        javafx.scene.layout.GridPane.setVgrow(textArea, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.GridPane.setHgrow(textArea, javafx.scene.layout.Priority.ALWAYS);
        alert.getDialogPane().setContent(textArea);
        
        alert.getDialogPane().getStyleClass().add("settings-warm-dialog");
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/settings_screens.css").toExternalForm());
        alert.setResizable(true);
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
                companyService.setDefaultCompany(chosen.getUuid());
                setupCompanyCombo();
            }

            // Set selected bank as default (details are managed in Bank Details screen)
            BankDetails chosenBank = bankCombo != null ? bankCombo.getValue() : null;
            if (chosenBank != null) {
                bankService.setDefaultBank(chosenBank.getUuid());
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
            MainController.getInstance().refreshNavigationLocks();
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

    private void loadSignaturePreview() {
        if (signaturePreview == null) return;
        String path = utils.DigitalSignaturePath.get();
        if (path != null && !path.isBlank()) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                try {
                    javafx.scene.image.Image img = new javafx.scene.image.Image(f.toURI().toString());
                    signaturePreview.setImage(img);
                    if (btnRemoveSignature != null) {
                        btnRemoveSignature.setDisable(false);
                    }
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        signaturePreview.setImage(null);
        if (btnRemoveSignature != null) {
            btnRemoveSignature.setDisable(true);
        }
    }

    private void uploadSignature() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Select Digital Signature Image");
        fc.getExtensionFilters().addAll(
            new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );
        File selectedFile = fc.showOpenDialog(saveBtn.getScene().getWindow());
        if (selectedFile != null) {
            try {
                File destDir = new File(System.getProperty("user.home") + "/.sunnyprinters");
                if (!destDir.exists()) {
                    destDir.mkdirs();
                }
                String ext = "";
                String name = selectedFile.getName();
                int idx = name.lastIndexOf('.');
                if (idx > 0) {
                    ext = name.substring(idx);
                }
                File dest = new File(destDir, "digital_signature" + ext);
                java.nio.file.Files.copy(selectedFile.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                utils.DigitalSignaturePath.set(dest.getAbsolutePath());
                loadSignaturePreview();
                showInfo("Signature uploaded successfully.");
            } catch (Exception e) {
                e.printStackTrace();
                showError("Failed to upload signature", e);
            }
        }
    }

    private void removeSignature() {
        String path = utils.DigitalSignaturePath.get();
        if (path != null && !path.isBlank()) {
            try {
                File f = new File(path);
                if (f.exists()) {
                    f.delete();
                }
                utils.DigitalSignaturePath.set(null);
                loadSignaturePreview();
                showInfo("Signature removed successfully.");
            } catch (Exception e) {
                e.printStackTrace();
                showError("Failed to remove signature", e);
            }
        }
    }

    public void refresh() {
        loadSettings();
    }

    private boolean isReadOnly() {
        User currentUser = utils.SessionManager.getInstance().getCurrentUser();
        return currentUser == null || currentUser.getRole() == null || 
               !(currentUser.getRole().equalsIgnoreCase("ADMIN") || 
                 currentUser.getRole().equalsIgnoreCase("ADMINISTRATOR"));
    }

    private final Map<String, CheckBox> checkboxMap = new HashMap<>();
    private final Map<String, List<String>> parentToChildren = Map.of(
        "jobs", List.of("job_items", "job_cancellation_audit", "invoice_job_mapping"),
        "invoice_master", List.of("invoice_job_mapping", "invoice_additional_charges", "invoice_adjustments", "payment_allocations"),
        "payments", List.of("payment_details", "payment_allocations"),
        "clients", List.of("jobs", "invoice_master", "payments")
    );
    private boolean isUpdatingCheckboxes = false;

    private void handleResetDatabase() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reset Database Tables");
        dialog.setHeaderText("Select the tables you want to clear from both local and remote databases.\n"
                + "Dependencies will be automatically managed (checking a table checks its dependencies).");

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogPane.getStyleClass().add("settings-warm-dialog");
        dialogPane.getStylesheets().addAll(
            getClass().getResource("/css/theme.css").toExternalForm(),
            getClass().getResource("/css/settings_screens.css").toExternalForm()
        );

        checkboxMap.clear();
        VBox contentBox = new VBox(8);
        contentBox.setStyle("-fx-padding: 10;");

        // Helper buttons
        HBox helperBox = new HBox(10);
        Button btnSelectAll = new Button("Select All");
        Button btnDeselectAll = new Button("Deselect All");
        btnSelectAll.setOnAction(e -> {
            isUpdatingCheckboxes = true;
            for (CheckBox cb : checkboxMap.values()) {
                cb.setSelected(true);
            }
            isUpdatingCheckboxes = false;
        });
        btnDeselectAll.setOnAction(e -> {
            isUpdatingCheckboxes = true;
            for (CheckBox cb : checkboxMap.values()) {
                cb.setSelected(false);
            }
            isUpdatingCheckboxes = false;
        });
        helperBox.getChildren().addAll(btnSelectAll, btnDeselectAll);
        contentBox.getChildren().add(helperBox);

        // List tables
        List<String> clearable = service.DatabaseCleanupService.getClearableTables();
        for (String table : clearable) {
            CheckBox cb = new CheckBox(service.DatabaseCleanupService.getDisplayName(table) + " (" + table + ")");
            cb.setId(table);
            checkboxMap.put(table, cb);
            contentBox.getChildren().add(cb);
        }

        setupCheckboxListeners();

        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(400);
        scrollPane.setPrefViewportWidth(450);
        dialogPane.setContent(scrollPane);

        dialog.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK) {
                List<String> selected = new ArrayList<>();
                for (Map.Entry<String, CheckBox> entry : checkboxMap.entrySet()) {
                    if (entry.getValue().isSelected()) {
                        selected.add(entry.getKey());
                    }
                }

                if (selected.isEmpty()) {
                    showInfo("No tables selected to clear.");
                    return;
                }

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.getDialogPane().getStyleClass().add("settings-warm-dialog");
                confirm.getDialogPane().getStylesheets().addAll(
                    getClass().getResource("/css/theme.css").toExternalForm(),
                    getClass().getResource("/css/settings_screens.css").toExternalForm()
                );
                confirm.setTitle("Confirm Hard Reset");
                confirm.setHeaderText("Warning: Permanent Data Loss!");
                confirm.setContentText("You have selected " + selected.size() + " tables to clear. "
                        + "This will delete all records from these tables in BOTH local SQLite and remote Supabase databases, "
                        + "and reset their number sequences to 1.\n\nAre you absolutely sure you want to proceed?");
                
                confirm.showAndWait().ifPresent(btn -> {
                    if (btn == ButtonType.OK) {
                        executeCleanup(selected);
                    }
                });
            }
        });
    }

    private void setupCheckboxListeners() {
        for (Map.Entry<String, List<String>> entry : parentToChildren.entrySet()) {
            String parent = entry.getKey();
            List<String> children = entry.getValue();
            CheckBox parentCb = checkboxMap.get(parent);
            if (parentCb == null) continue;

            parentCb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (isUpdatingCheckboxes) return;
                if (newVal) {
                    isUpdatingCheckboxes = true;
                    for (String child : children) {
                        CheckBox childCb = checkboxMap.get(child);
                        if (childCb != null) {
                            childCb.setSelected(true);
                            propagateSelection(child, true);
                        }
                    }
                    isUpdatingCheckboxes = false;
                }
            });
        }

        for (CheckBox cb : checkboxMap.values()) {
            cb.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (isUpdatingCheckboxes) return;
                if (!newVal) {
                    isUpdatingCheckboxes = true;
                    uncheckParentsOf(cb.getId());
                    isUpdatingCheckboxes = false;
                }
            });
        }
    }

    private void propagateSelection(String parent, boolean selected) {
        List<String> children = parentToChildren.get(parent);
        if (children == null) return;
        for (String child : children) {
            CheckBox childCb = checkboxMap.get(child);
            if (childCb != null) {
                childCb.setSelected(selected);
                propagateSelection(child, selected);
            }
        }
    }

    private void uncheckParentsOf(String childId) {
        for (Map.Entry<String, List<String>> entry : parentToChildren.entrySet()) {
            String parent = entry.getKey();
            List<String> children = entry.getValue();
            if (children.contains(childId)) {
                CheckBox parentCb = checkboxMap.get(parent);
                if (parentCb != null && parentCb.isSelected()) {
                    parentCb.setSelected(false);
                    uncheckParentsOf(parent);
                }
            }
        }
    }

    private void executeCleanup(List<String> selected) {
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Clearing Database...");
        progressDialog.setHeaderText("Clearing " + selected.size() + " tables. Please wait...");
        
        DialogPane dialogPane = progressDialog.getDialogPane();
        dialogPane.getStyleClass().add("settings-warm-dialog");
        dialogPane.getStylesheets().addAll(
            getClass().getResource("/css/theme.css").toExternalForm(),
            getClass().getResource("/css/settings_screens.css").toExternalForm()
        );
        
        // Add close button type so we can close programmatically
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        Platform.runLater(() -> {
            javafx.scene.Node closeBtn = dialogPane.lookupButton(ButtonType.CLOSE);
            if (closeBtn != null) {
                closeBtn.setVisible(false);
                closeBtn.setManaged(false);
            }
        });

        ProgressIndicator pi = new ProgressIndicator();
        VBox vbox = new VBox(20, pi);
        vbox.setStyle("-fx-padding: 30; -fx-alignment: center;");
        dialogPane.setContent(vbox);
        progressDialog.getDialogPane().getScene().getWindow().setOnCloseRequest(e -> e.consume());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                service.DatabaseCleanupService.clearTables(selected);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            progressDialog.close();
            showInfo("Successfully cleared selected tables and reset their sequence counters locally and on Supabase!");
        });

        task.setOnFailed(e -> {
            progressDialog.close();
            Throwable ex = task.getException();
            ex.printStackTrace();
            showError("Failed to clear database tables", ex instanceof Exception ? (Exception) ex : new Exception(ex));
        });

        // Start task and show dialog
        new Thread(task).start();
        progressDialog.showAndWait();
    }
}
