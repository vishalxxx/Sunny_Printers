package controller;

import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import model.CompanyDetails;
import service.CompanyDetailsService;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;

public class CompanySettingsController implements Initializable {

	@FXML private HBox breadcrumbContainer;

	@FXML private TableView<CompanyDetails> companyTable;
	@FXML private TableColumn<CompanyDetails, String> colTrade;
	@FXML private TableColumn<CompanyDetails, String> colGstin;
	@FXML private TableColumn<CompanyDetails, String> colPhone;
	@FXML private TableColumn<CompanyDetails, String> colAltPhone;
	@FXML private TableColumn<CompanyDetails, String> colEmail;
	@FXML private TableColumn<CompanyDetails, Boolean> colDefault;
	@FXML private TableColumn<CompanyDetails, Boolean> colActive;

	@FXML private TextField tradeNameField;
	@FXML private TextArea addressArea;
	@FXML private TextField phoneField;
	@FXML private TextField altPhoneField;
	@FXML private TextField emailField;
	@FXML private TextField gstinField;
	@FXML private TextField stateField;
	@FXML private CheckBox defaultCheck;
	@FXML private CheckBox activeCheck;

	@FXML private Button newBtn;
	@FXML private Button saveBtn;
	@FXML private Button deleteBtn;

	private final CompanyDetailsService companyService = new CompanyDetailsService();
	private final ObservableList<CompanyDetails> rows = FXCollections.observableArrayList();

	private CompanyDetails editing = null;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null,
				() -> MainController.getInstance().handleBack(null));

		setupTable();
		wirePhoneFormatters();
		wireActions();
		loadData();
		selectFirstRow();
	}

	private void setupTable() {
		if (companyTable == null) return;

		companyTable.setItems(rows);
		companyTable.setEditable(false);

		if (colTrade != null) colTrade.setCellValueFactory(c -> new ReadOnlyStringWrapper(nz(c.getValue().getTradeName())));
		if (colGstin != null) colGstin.setCellValueFactory(c -> new ReadOnlyStringWrapper(nz(c.getValue().getGstin())));
		if (colPhone != null) colPhone.setCellValueFactory(c -> new ReadOnlyStringWrapper(nz(c.getValue().getPhone())));
		if (colAltPhone != null) colAltPhone.setCellValueFactory(c -> new ReadOnlyStringWrapper(nz(c.getValue().getAltPhone())));
		if (colEmail != null) colEmail.setCellValueFactory(c -> new ReadOnlyStringWrapper(nz(c.getValue().getEmail())));
		if (colDefault != null) colDefault.setCellValueFactory(c -> new ReadOnlyBooleanWrapper(c.getValue().isDefault()));
		if (colActive != null) colActive.setCellValueFactory(c -> new ReadOnlyBooleanWrapper(c.getValue().isActive()));

		companyTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
			if (newV != null) {
				beginEdit(copyOf(newV));
			}
		});
	}

	private void wireActions() {
		if (newBtn != null) newBtn.setOnAction(e -> beginEdit(newBlank()));
		if (saveBtn != null) saveBtn.setOnAction(e -> save());
		if (deleteBtn != null) deleteBtn.setOnAction(e -> deleteSelected());
	}

	private void loadData() {
		rows.clear();
		List<CompanyDetails> list = companyService.listAllIncludingInactive();
		if (list != null) rows.addAll(list);
	}

	private void selectFirstRow() {
		if (companyTable == null) return;
		if (!rows.isEmpty()) {
			companyTable.getSelectionModel().select(0);
		} else {
			beginEdit(newBlank());
		}
	}

	private void beginEdit(CompanyDetails c) {
		editing = c;
		applyToForm(c);
		updateDeleteState();
	}

	private void updateDeleteState() {
		if (deleteBtn == null) return;
		deleteBtn.setDisable(editing == null || editing.getId() <= 0);
	}

	private void applyToForm(CompanyDetails c) {
		if (c == null) return;
		if (tradeNameField != null) tradeNameField.setText(nz(c.getTradeName()));
		if (addressArea != null) addressArea.setText(nz(c.getAddress()));
		if (phoneField != null) phoneField.setText(nz(c.getPhone()));
		if (altPhoneField != null) altPhoneField.setText(nz(c.getAltPhone()));
		if (emailField != null) emailField.setText(nz(c.getEmail()));
		if (gstinField != null) gstinField.setText(nz(c.getGstin()));
		if (stateField != null) stateField.setText(nz(c.getState()));
		if (defaultCheck != null) defaultCheck.setSelected(c.isDefault());
		if (activeCheck != null) activeCheck.setSelected(c.isActive());
	}

	private void readFromForm(CompanyDetails c) {
		c.setTradeName(text(tradeNameField));
		c.setAddress(text(addressArea));
		c.setPhone(text(phoneField));
		c.setAltPhone(text(altPhoneField));
		c.setEmail(text(emailField));
		c.setGstin(text(gstinField));
		c.setState(text(stateField));
		c.setDefault(defaultCheck != null && defaultCheck.isSelected());
		c.setActive(activeCheck == null || activeCheck.isSelected());
	}

	private void save() {
		if (editing == null) editing = newBlank();

		readFromForm(editing);
		if (editing.getTradeName() == null || editing.getTradeName().isBlank()) {
			showWarn("Company / trade name is required.");
			return;
		}
		if (!isSinglePhone(editing.getPhone())) {
			showWarn("Phone must be a single phone number (no commas or multiple numbers).");
			return;
		}
		if (!isSinglePhone(editing.getAltPhone())) {
			showWarn("Alt phone must be a single phone number (no commas or multiple numbers).");
			return;
		}

		CompanyDetails saved = companyService.save(editing);
		loadData();

		if (companyTable != null) {
			for (int i = 0; i < rows.size(); i++) {
				if (rows.get(i).getId() == saved.getId()) {
					companyTable.getSelectionModel().select(i);
					break;
				}
			}
		}

		showInfo("Company saved.");
	}

	private void deleteSelected() {
		if (companyTable == null) return;
		CompanyDetails selected = companyTable.getSelectionModel().getSelectedItem();
		if (selected == null || selected.getId() <= 0) return;

		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
				"Delete company '" + nz(selected.getTradeName()) + "'?",
				ButtonType.CANCEL, ButtonType.OK);
		confirm.setHeaderText("Confirm delete");
		styleDialog(confirm);
		ButtonType res = confirm.showAndWait().orElse(ButtonType.CANCEL);
		if (res != ButtonType.OK) return;

		companyService.delete(selected.getId());
		loadData();
		selectFirstRow();
		showInfo("Deleted.");
	}

	private static CompanyDetails newBlank() {
		CompanyDetails c = new CompanyDetails();
		c.setActive(true);
		c.setDefault(false);
		return c;
	}

	private static CompanyDetails copyOf(CompanyDetails src) {
		CompanyDetails c = new CompanyDetails();
		c.setId(src.getId());
		c.setTradeName(src.getTradeName());
		c.setAddress(src.getAddress());
		c.setPhone(src.getPhone());
		c.setAltPhone(src.getAltPhone());
		c.setEmail(src.getEmail());
		c.setGstin(src.getGstin());
		c.setState(src.getState());
		c.setDefault(src.isDefault());
		c.setActive(src.isActive());
		return c;
	}

	private void wirePhoneFormatters() {
		UnaryOperator<TextFormatter.Change> filter = change -> {
			if (change == null) return null;
			String next = change.getControlNewText();
			if (next == null) return change;
			// Disallow obvious multi-number separators / newlines.
			if (next.contains(",") || next.contains(";") || next.contains("/") || next.contains("|") || next.contains("\n") || next.contains("\r")) {
				return null;
			}
			return change;
		};
		if (phoneField != null) phoneField.setTextFormatter(new TextFormatter<>(filter));
		if (altPhoneField != null) altPhoneField.setTextFormatter(new TextFormatter<>(filter));
	}

	private static boolean isSinglePhone(String raw) {
		if (raw == null) return true;
		String t = raw.trim();
		if (t.isEmpty()) return true;
		if (t.contains(",") || t.contains(";") || t.contains("/") || t.contains("|") || t.contains("\n") || t.contains("\r")) {
			return false;
		}
		// Must contain digits; keep it permissive about formatting characters.
		String digits = t.replaceAll("[^0-9]", "");
		return !digits.isEmpty() && digits.length() <= 15;
	}

	private static String text(TextInputControl f) {
		if (f == null) return "";
		String t = f.getText();
		return t == null ? "" : t.trim();
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	private void showInfo(String msg) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION, msg);
		alert.setHeaderText("Success");
		styleDialog(alert);
		alert.show();
	}

	private void showWarn(String msg) {
		Alert alert = new Alert(Alert.AlertType.WARNING, msg);
		alert.setHeaderText("Check");
		styleDialog(alert);
		alert.show();
	}

	private static void styleDialog(Alert alert) {
		alert.getDialogPane().getStyleClass().add("settings-warm-dialog");
		alert.getDialogPane().getStylesheets().add(CompanySettingsController.class.getResource("/css/theme.css").toExternalForm());
		alert.getDialogPane().getStylesheets().add(CompanySettingsController.class.getResource("/css/settings_screens.css").toExternalForm());
	}
}

