package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import model.SystemSettings;
import repository.SystemSettingsRepository;

public class SystemSettingsController {

	@FXML
	private RadioButton autoRadio;
	@FXML
	private RadioButton manualRadio;
	@FXML
	private ToggleGroup modeGroup;

	@FXML
	private VBox manualCard;

	@FXML
	private TextField prefixField;
	@FXML
	private TextField startField;
	@FXML
	private ComboBox<Integer> paddingCombo;

	@FXML
	private Label previewBadge;
	@FXML
	private Label sequencePreview;

	@FXML
	private Button saveBtn;
	@FXML
	private Button resetBtn;

	private final SystemSettingsRepository repo = new SystemSettingsRepository();
	private SystemSettings settings;

	// =====================================================
	@FXML
	private void initialize() {

		paddingCombo.getItems().addAll(3, 4, 5);

		loadSettings();
		bindEvents();
		updateUIState();
		updatePreview();
	}

	// =====================================================
	private void loadSettings() {
		try {
			settings = repo.load();

			if (settings.isAuto())
				autoRadio.setSelected(true);
			else
				manualRadio.setSelected(true);

			prefixField.setText(settings.getInvoicePrefix());
			startField.setText(String.valueOf(settings.getInvoiceStartNo()));
			paddingCombo.setValue(settings.getInvoicePadding());

		} catch (Exception e) {
			showError("Failed to load system settings", e);
		}
	}

	// =====================================================
	private void bindEvents() {

		modeGroup.selectedToggleProperty().addListener((obs, o, n) -> updateUIState());

		prefixField.textProperty().addListener((a, b, c) -> updatePreview());
		startField.textProperty().addListener((a, b, c) -> updatePreview());
		paddingCombo.valueProperty().addListener((a, b, c) -> updatePreview());

		saveBtn.setOnAction(e -> save());
		resetBtn.setOnAction(e -> reset());
	}

	// =====================================================
	private void updateUIState() {

		boolean manual = manualRadio.isSelected();

		manualCard.setDisable(!manual);

		if (!manual) {
			if (!manualCard.getStyleClass().contains("card-disabled"))
				manualCard.getStyleClass().add("card-disabled");
		} else {
			manualCard.getStyleClass().remove("card-disabled");
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
			sequencePreview
					.setText(formatted + ", " + String.format("%s%0" + padding + "d", prefix, start + 1) + "...");

		} catch (Exception ignored) {
			previewBadge.setText("—");
			sequencePreview.setText("Invalid configuration");
		}
	}

	// =====================================================
	private void save() {

		try {
			if (autoRadio.isSelected()) {
				settings.setInvoiceMode("AUTO");
				repo.save(settings);
				showInfo("Automatic invoice numbering enabled.");
				return;
			}

			settings.setInvoiceMode("MANUAL");
			settings.setInvoicePrefix(prefixField.getText());
			settings.setInvoiceStartNo(Integer.parseInt(startField.getText()));
			settings.setInvoicePadding(paddingCombo.getValue());

			repo.save(settings);
			showInfo("Manual invoice numbering saved.");

		} catch (Exception e) {
			showError("Save failed", e);
		}
	}

	// =====================================================
	private void reset() {
		autoRadio.setSelected(true);
		updateUIState();
		updatePreview();
	}

	// =====================================================
	private void showInfo(String msg) {
	    Alert alert = new Alert(Alert.AlertType.INFORMATION);
	    alert.setHeaderText("Message");
	    alert.setContentText(msg);

	    alert.initStyle(javafx.stage.StageStyle.TRANSPARENT);   // ⭐ important
	    alert.getDialogPane().setBackground(null);              // ⭐ important

	    alert.getDialogPane().getStylesheets()
	            .add(getClass().getResource("/css/invoice_settings.css").toExternalForm());

	    alert.showAndWait();
	}

	private void showError(String msg, Exception e) {
	    e.printStackTrace();

	    Alert alert = new Alert(Alert.AlertType.ERROR);
	    alert.setHeaderText("Error");
	    alert.setContentText(msg + "\n" + e.getMessage());

	    alert.initStyle(javafx.stage.StageStyle.TRANSPARENT);   // ⭐ important
	    alert.getDialogPane().setBackground(null);              // ⭐ important

	    alert.getDialogPane().getStylesheets()
	            .add(getClass().getResource("/css/invoice_settings.css").toExternalForm());

	    alert.showAndWait();
	}

}
