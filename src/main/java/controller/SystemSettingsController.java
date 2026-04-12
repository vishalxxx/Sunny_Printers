package controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import model.SystemSettings;
import repository.SystemSettingsRepository;
import javafx.collections.FXCollections;
import utils.AtomicDB;
import utils.DBConnection;

import java.net.URL;
import java.sql.Connection;
import java.util.ResourceBundle;

public class SystemSettingsController implements Initializable, utils.DirtySupport {

	@Override
	public boolean hasUnsavedChanges() {
		if (settings == null)
			return false;

		try {
			boolean changed = !prefixField.getText().equals(settings.getInvoicePrefix())
					|| Integer.parseInt(startField.getText()) != settings.getInvoiceStartNo()
					|| !paddingCombo.getValue().equals(settings.getInvoicePadding())
					|| !jobPrefixField.getText().equals(settings.getJobPrefix())
					|| Integer.parseInt(jobStartField.getText()) != settings.getJobStartNo()
					|| !jobPaddingCombo.getValue().equals(settings.getJobPadding());

			return changed;
		} catch (Exception e) {
			return true; // If parsing fails, something is being typed
		}
	}

@FXML private VBox manualCard;

@FXML private TextField prefixField;
@FXML private TextField startField;
@FXML private ComboBox<Integer> paddingCombo;

@FXML private Label previewBadge;
@FXML private Label sequencePreview;

@FXML private TextField jobPrefixField;
@FXML private TextField jobStartField;
@FXML private ComboBox<Integer> jobPaddingCombo;

@FXML private Label jobPreviewBadge;
@FXML private Label jobSequencePreview;

@FXML private Button saveBtn;
@FXML private Button resetBtn;

private final SystemSettingsRepository repo = new SystemSettingsRepository();
private SystemSettings settings;

// =====================================================
@Override
public void initialize(URL location, ResourceBundle resources) {
    // Populate Padding Combo
    paddingCombo.setItems(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6));

    loadSettings();

    // Listeners to update preview dynamically
    prefixField.textProperty().addListener((obs, oldV, newV) -> updatePreview());
    startField.textProperty().addListener((obs, oldV, newV) -> updatePreview());
    paddingCombo.valueProperty().addListener((obs, oldV, newV) -> updatePreview());
    
    jobPaddingCombo.setItems(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6));

    jobPrefixField.textProperty().addListener((obs, oldV, newV) -> updateJobPreview());
    jobStartField.textProperty().addListener((obs, oldV, newV) -> updateJobPreview());
    jobPaddingCombo.valueProperty().addListener((obs, oldV, newV) -> updateJobPreview());

    saveBtn.setOnAction(e -> save());
    resetBtn.setOnAction(e -> reset());
}

// =====================================================
private void loadSettings() {
    try {
        settings = AtomicDB.run(con -> {
            try {
                return repo.load(con);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        prefixField.setText(settings.getInvoicePrefix());
        startField.setText(String.valueOf(settings.getInvoiceStartNo()));
        paddingCombo.setValue(settings.getInvoicePadding());
        
        jobPrefixField.setText(settings.getJobPrefix() != null ? settings.getJobPrefix() : "SUN-");
        jobStartField.setText(String.valueOf(settings.getJobStartNo() > 0 ? settings.getJobStartNo() : 1));
        jobPaddingCombo.setValue(settings.getJobPadding() > 0 ? settings.getJobPadding() : 3);
        
        updatePreview();
        updateJobPreview();

    } catch (Exception e) {
        showError("Failed to load system settings", e);
    }
}

// =====================================================
private void updatePreview() {

    try {
        String prefix = prefixField.getText();
        int start = Integer.parseInt(startField.getText());
        int padding = paddingCombo.getValue();

        String formatted = String.format("%s%0" + padding + "d", prefix, start);

        previewBadge.setText(formatted);
        sequencePreview.setText(
                formatted + ", " +
                String.format("%s%0" + padding + "d", prefix, start + 1) +
                "..."
        );

    } catch (Exception ignored) {
        previewBadge.setText("—");
        sequencePreview.setText("Invalid configuration");
    }
}

// =====================================================
private void updateJobPreview() {
    try {
        String pPrefix = jobPrefixField.getText();
        int pStart = Integer.parseInt(jobStartField.getText());
        int pPadding = jobPaddingCombo.getValue();

        String formatted = String.format("%s%0" + pPadding + "d", pPrefix, pStart);

        jobPreviewBadge.setText(formatted);
        jobSequencePreview.setText(
                formatted + ", " +
                String.format("%s%0" + pPadding + "d", pPrefix, pStart + 1) +
                "..."
        );
    } catch (Exception ignored) {
        jobPreviewBadge.setText("—");
        jobSequencePreview.setText("Invalid configuration");
    }
}

// =====================================================
private void save() {

    try (Connection con = DBConnection.getConnection()) {
        con.setAutoCommit(false);
        try {
            SystemSettings settings = repo.load(con);

            settings.setInvoiceMode("MANUAL");
            settings.setInvoicePrefix(prefixField.getText());
            settings.setInvoiceStartNo(Integer.parseInt(startField.getText()));
            settings.setInvoicePadding(paddingCombo.getValue());
            
            settings.setJobPrefix(jobPrefixField.getText());
            settings.setJobStartNo(Integer.parseInt(jobStartField.getText()));
            settings.setJobPadding(jobPaddingCombo.getValue());

            repo.save(con, settings);

            con.commit();
            showInfo("System settings saved successfully.");
        } catch (Exception e) {
            con.rollback();
            throw e;
        }
    } catch (Exception e) {
        showError("Save failed", e);
    }
}

// =====================================================
private void reset() {
    loadSettings();
}

// =====================================================
private void showInfo(String msg) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setHeaderText("Message");
    alert.setContentText(msg);

    alert.initStyle(javafx.stage.StageStyle.TRANSPARENT);
    alert.getDialogPane().setBackground(null);

    alert.getDialogPane().getStylesheets()
            .add(getClass().getResource("/css/invoice_settings.css").toExternalForm());

    alert.showAndWait();
}

private void showError(String msg, Exception e) {
    e.printStackTrace();

    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setHeaderText("Error");
    alert.setContentText(msg + "\n" + e.getMessage());

    alert.initStyle(javafx.stage.StageStyle.TRANSPARENT);
    alert.getDialogPane().setBackground(null);

    alert.getDialogPane().getStylesheets()
            .add(getClass().getResource("/css/invoice_settings.css").toExternalForm());

    alert.showAndWait();
}


}
