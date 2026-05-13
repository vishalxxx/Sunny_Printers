package controller;

import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import model.BankDetails;
import service.BankDetailsService;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class BankSettingsController implements Initializable {

	@FXML private HBox breadcrumbContainer;

	@FXML private TableView<BankDetails> bankTable;
	@FXML private TableColumn<BankDetails, String> colBankName;
	@FXML private TableColumn<BankDetails, String> colHolder;
	@FXML private TableColumn<BankDetails, String> colAccount;
	@FXML private TableColumn<BankDetails, String> colIfsc;
	@FXML private TableColumn<BankDetails, Boolean> colDefault;
	@FXML private TableColumn<BankDetails, Boolean> colActive;

	@FXML private TextField bankNameField;
	@FXML private TextField holderField;
	@FXML private TextField accountNoField;
	@FXML private TextField branchField;
	@FXML private TextField ifscCodeField;
	@FXML private CheckBox defaultCheck;
	@FXML private CheckBox activeCheck;

	@FXML private Button newBtn;
	@FXML private Button saveBtn;
	@FXML private Button editRowBtn;
	@FXML private Button deleteBtn;

	private final BankDetailsService bankService = new BankDetailsService();
	private final ObservableList<BankDetails> rows = FXCollections.observableArrayList();

	private BankDetails editing = null;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null,
				() -> MainController.getInstance().handleBack(null));

		setupTable();
		wireActions();
		loadData();
		selectFirstRow();
	}

	private void setupTable() {
		if (bankTable == null) return;

		bankTable.setItems(rows);
		bankTable.setEditable(false);

		if (colBankName != null) colBankName.setCellValueFactory(c -> new ReadOnlyStringWrapper(nz(c.getValue().getBankName())));
		if (colHolder != null) colHolder.setCellValueFactory(c -> new ReadOnlyStringWrapper(nz(c.getValue().getAccountHolderName())));
		if (colAccount != null) colAccount.setCellValueFactory(c -> new ReadOnlyStringWrapper(nz(c.getValue().getAccountNo())));
		if (colIfsc != null) colIfsc.setCellValueFactory(c -> new ReadOnlyStringWrapper(nz(c.getValue().getBranchIfsc())));
		if (colDefault != null) colDefault.setCellValueFactory(c -> new ReadOnlyBooleanWrapper(c.getValue().isDefault()));
		if (colActive != null) colActive.setCellValueFactory(c -> new ReadOnlyBooleanWrapper(c.getValue().isActive()));

		bankTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
			if (newV != null) {
				beginEdit(copyOf(newV));
			}
			updateRowActionButtonsState();
		});
	}

	private void wireActions() {
		if (newBtn != null) newBtn.setOnAction(e -> beginEdit(newBlank()));
		if (saveBtn != null) saveBtn.setOnAction(e -> save());
		if (editRowBtn != null) {
			editRowBtn.setOnAction(e -> {
				BankDetails sel = bankTable != null ? bankTable.getSelectionModel().getSelectedItem() : null;
				if (sel != null) {
					beginEdit(copyOf(sel));
				}
			});
		}
		if (deleteBtn != null) deleteBtn.setOnAction(e -> deleteSelected());
	}

	private void loadData() {
		rows.clear();
		List<BankDetails> list = bankService.listAllIncludingInactive();
		if (list != null) rows.addAll(list);
		updateRowActionButtonsState();
	}

	private void selectFirstRow() {
		if (bankTable == null) return;
		if (!rows.isEmpty()) {
			bankTable.getSelectionModel().select(0);
		} else {
			beginEdit(newBlank());
		}
	}

	private void beginEdit(BankDetails b) {
		editing = b;
		applyToForm(b);
		updateRowActionButtonsState();
	}

	/** Pencil / trash availability follows table selection (delete only for persisted rows). */
	private void updateRowActionButtonsState() {
		if (bankTable == null) {
			return;
		}
		BankDetails sel = bankTable.getSelectionModel().getSelectedItem();
		boolean hasSelection = sel != null;
		boolean canDelete = hasSelection && sel.getId() > 0;
		if (editRowBtn != null) {
			editRowBtn.setDisable(!hasSelection);
		}
		if (deleteBtn != null) {
			deleteBtn.setDisable(!canDelete);
		}
	}

	private void applyToForm(BankDetails b) {
		if (b == null) return;
		if (bankNameField != null) bankNameField.setText(nz(b.getBankName()));
		if (holderField != null) holderField.setText(nz(b.getAccountHolderName()));
		if (accountNoField != null) accountNoField.setText(nz(b.getAccountNo()));
		if (branchField != null) branchField.setText(nz(b.getBranchName()));
		if (ifscCodeField != null) ifscCodeField.setText(nz(b.getIfscCode()));
		if (defaultCheck != null) defaultCheck.setSelected(b.isDefault());
		if (activeCheck != null) activeCheck.setSelected(b.isActive());
	}

	private void readFromForm(BankDetails b) {
		b.setBankName(text(bankNameField));
		b.setAccountHolderName(text(holderField));
		b.setAccountNo(text(accountNoField));
		b.setBranchName(text(branchField));
		b.setIfscCode(text(ifscCodeField));
		b.setDefault(defaultCheck != null && defaultCheck.isSelected());
		b.setActive(activeCheck == null || activeCheck.isSelected());
	}

	private void save() {
		if (editing == null) {
			editing = newBlank();
		}

		readFromForm(editing);
		if (editing.getBankName() == null || editing.getBankName().isBlank()) {
			showWarn("Bank name is required.");
			return;
		}

		BankDetails saved = bankService.save(editing);
		loadData();

		// reselect saved row
		if (bankTable != null) {
			for (int i = 0; i < rows.size(); i++) {
				if (rows.get(i).getId() == saved.getId()) {
					bankTable.getSelectionModel().select(i);
					break;
				}
			}
		}

		showInfo("Bank details saved.");
	}

	private void deleteSelected() {
		if (bankTable == null) return;
		BankDetails selected = bankTable.getSelectionModel().getSelectedItem();
		if (selected == null || selected.getId() <= 0) return;

		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
				"Delete bank '" + nz(selected.getBankName()) + "'?",
				ButtonType.CANCEL, ButtonType.OK);
		confirm.setHeaderText("Confirm delete");
		styleDialog(confirm);
		ButtonType res = confirm.showAndWait().orElse(ButtonType.CANCEL);
		if (res != ButtonType.OK) return;

		bankService.delete(selected.getId());
		loadData();
		selectFirstRow();
		showInfo("Deleted.");
	}

	private static BankDetails newBlank() {
		BankDetails b = new BankDetails();
		b.setActive(true);
		b.setDefault(false);
		return b;
	}

	private static BankDetails copyOf(BankDetails src) {
		BankDetails b = new BankDetails();
		b.setId(src.getId());
		b.setBankName(src.getBankName());
		b.setAccountHolderName(src.getAccountHolderName());
		b.setAccountNo(src.getAccountNo());
		b.setBranchName(src.getBranchName());
		b.setIfscCode(src.getIfscCode());
		b.setDefault(src.isDefault());
		b.setActive(src.isActive());
		return b;
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
		alert.getDialogPane().getStylesheets().add(BankSettingsController.class.getResource("/css/theme.css").toExternalForm());
		alert.getDialogPane().getStylesheets().add(BankSettingsController.class.getResource("/css/settings_screens.css").toExternalForm());
	}
}

