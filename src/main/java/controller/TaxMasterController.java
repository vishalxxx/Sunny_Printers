package controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import model.TaxMasterItem;
import service.TaxMasterService;
import utils.Toast;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class TaxMasterController implements Initializable {

	private static final String ALL_CATEGORIES = "All Categories";
	private static final String ALL_TYPES = "All Types";
	private static final int PAGE_SIZE = 10;

	private final TaxMasterService taxService = new TaxMasterService();
	private final ObservableList<TaxMasterItem> tableRows = FXCollections.observableArrayList();

	private int currentPage;
	/** {@code 1} = active list, {@code 0} = inactive-only list */
	private int activeListFilter = 1;
	private TaxMasterItem editingSnapshot;

	@FXML private ScrollPane taxMasterScroll;
	@FXML private HBox breadcrumbContainer;
	@FXML private SplitPane splitPane;
	@FXML private TextField txtSearch;
	@FXML private ComboBox<String> comboCategory;
	@FXML private ComboBox<String> comboTypeFilter;
	@FXML private TableView<TaxMasterItem> tableTax;
	@FXML private TableColumn<TaxMasterItem, TaxMasterItem> colNum;
	@FXML private TableColumn<TaxMasterItem, String> colItemName;
	@FXML private TableColumn<TaxMasterItem, TaxMasterItem> colCategory;
	@FXML private TableColumn<TaxMasterItem, String> colCode;
	@FXML private TableColumn<TaxMasterItem, TaxMasterItem> colKind;
	@FXML private TableColumn<TaxMasterItem, TaxMasterItem> colGst;
	@FXML private TableColumn<TaxMasterItem, TaxMasterItem> colStatus;
	@FXML private TableColumn<TaxMasterItem, TaxMasterItem> colActions;
	@FXML private Label lblPageInfo;
	@FXML private Button btnPrev;
	@FXML private Button btnNext;
	@FXML private HBox boxPageNumbers;
	@FXML private Button btnToggleInactive;
	@FXML private Label lblFormTitle;
	@FXML private TextField fldItemName;
	@FXML private ComboBox<String> fldCategory;
	@FXML private TextField fldHsnSac;
	@FXML private ComboBox<String> fldCodeType;
	@FXML private ComboBox<String> fldGstRate;
	@FXML private TextField fldUnit;
	@FXML private TextArea fldDescription;
	@FXML private javafx.scene.control.CheckBox chkFavorite;
	@FXML private Button btnCancelForm;
	@FXML private Button btnSave;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null,
				() -> MainController.getInstance().handleBack(null));

		setupFilterCombos();
		setupFormCombos();
		setupTable();
		wireListeners();

		if (btnToggleInactive != null) {
			btnToggleInactive.setText("View Inactive Items");
		}
		try {
			taxService.importDefaults();
		} catch (Exception e) { service.LoggerService.dbWarn("Failed to format tax rate: " + e.getMessage()); }
		reloadCategories();
		reloadTable();

	}

	public void refresh() {
		reloadCategories();
		reloadTable();
	}

	private void setupFilterCombos() {
		if (comboCategory != null) {
			comboCategory.getItems().clear();
			comboCategory.getItems().add(ALL_CATEGORIES);
			comboCategory.getSelectionModel().selectFirst();
		}
		if (comboTypeFilter != null) {
			comboTypeFilter.getItems().setAll(ALL_TYPES, "HSN", "SAC");
			comboTypeFilter.getSelectionModel().selectFirst();
		}
	}

	private void setupFormCombos() {
		if (fldCodeType != null) {
			fldCodeType.getItems().setAll("HSN", "SAC");
			fldCodeType.getSelectionModel().selectFirst();
		}
		if (fldGstRate != null) {
			fldGstRate.getItems().setAll("0%", "5%", "12%", "18%", "28%");
			fldGstRate.getSelectionModel().select("18%");
		}
	}

	private void reloadCategories() {
		List<String> fromDb = new ArrayList<>();
		try {
			fromDb.addAll(taxService.listCategories());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		List<String> standard = List.of("PRINTING", "PAPER", "BINDING", "LAMINATION", "CTP");
		java.util.LinkedHashSet<String> mergedSet = new java.util.LinkedHashSet<>(fromDb);
		for (String s : standard) {
			mergedSet.add(s);
		}
		List<String> merged = new ArrayList<>(mergedSet);
		utils.ComboBoxSorter.sortStrings(merged);

		String catSel = comboCategory != null && comboCategory.getValue() != null ? comboCategory.getValue() : ALL_CATEGORIES;
		if (comboCategory != null) {
			comboCategory.getItems().clear();
			comboCategory.getItems().add(ALL_CATEGORIES);
			comboCategory.getItems().addAll(merged);
			if (comboCategory.getItems().contains(catSel)) {
				comboCategory.getSelectionModel().select(catSel);
			} else {
				comboCategory.getSelectionModel().selectFirst();
			}
		}

		String formCat = fldCategory != null && fldCategory.getValue() != null ? fldCategory.getValue() : null;
		if (fldCategory != null) {
			fldCategory.getItems().clear();
			fldCategory.getItems().addAll(merged);
			if (formCat != null && fldCategory.getItems().contains(formCat)) {
				fldCategory.getSelectionModel().select(formCat);
			} else if (!fldCategory.getItems().isEmpty()) {
				fldCategory.getSelectionModel().selectFirst();
			}
		}
	}

	private void setupTable() {
		if (tableTax == null) {
			return;
		}
		tableTax.setItems(tableRows);
		tableTax.setPlaceholder(new Label("No tax items match your filters."));
		/* Variable row height so wrapped Item Name / HSN text is fully visible */
		tableTax.setFixedCellSize(-1);

		if (colNum != null) {
			colNum.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
			colNum.setCellFactory(col -> new TableCell<>() {
				@Override
				protected void updateItem(TaxMasterItem item, boolean empty) {
					super.updateItem(item, empty);
					if (empty || item == null) {
						setText(null);
					} else {
						int idx = getIndex() + 1 + currentPage * PAGE_SIZE;
						setText(Integer.toString(idx));
						setAlignment(Pos.CENTER);
					}
				}
			});
		}

		if (colItemName != null) {
			colItemName.setCellValueFactory(c -> new ReadOnlyStringWrapper(nz(c.getValue().getItemName())));
			colItemName.setCellFactory(col -> new TableCell<>() {
				private final Text textNode = new Text();

				{
					textNode.setTextAlignment(TextAlignment.CENTER);
					textNode.wrappingWidthProperty().bind(widthProperty().subtract(12));
				}

				@Override
				protected void updateItem(String item, boolean empty) {
					super.updateItem(item, empty);
					if (empty || item == null) {
						setGraphic(null);
						setText(null);
						setTooltip(null);
					} else {
						textNode.setText(item);
						setGraphic(textNode);
						setText(null);
						setAlignment(Pos.CENTER);
						Tooltip tip = new Tooltip(item);
						tip.setWrapText(true);
						tip.setMaxWidth(480);
						setTooltip(tip);
					}
				}
			});
		}

		if (colCategory != null) {
			colCategory.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
			colCategory.setCellFactory(col -> new TableCell<>() {
				@Override
				protected void updateItem(TaxMasterItem row, boolean empty) {
					super.updateItem(row, empty);
					if (empty || row == null) {
						setGraphic(null);
						return;
					}
					String cat = row.categoryLabel();
					Label pill = new Label(cat);
					pill.getStyleClass().add("tax-master-pill-category");
					pill.setAlignment(Pos.CENTER);
					Tooltip tip = new Tooltip(cat);
					tip.setWrapText(true);
					tip.setMaxWidth(320);
					pill.setTooltip(tip);
					setGraphic(pill);
					setAlignment(Pos.CENTER);
				}
			});
		}

		if (colCode != null) {
			colCode.setCellValueFactory(c -> new ReadOnlyStringWrapper(nz(c.getValue().getHsnSac())));
			colCode.setCellFactory(col -> new TableCell<>() {
				private final Text textNode = new Text();

				{
					textNode.setTextAlignment(TextAlignment.CENTER);
					textNode.wrappingWidthProperty().bind(widthProperty().subtract(12));
				}

				@Override
				protected void updateItem(String item, boolean empty) {
					super.updateItem(item, empty);
					if (empty || item == null) {
						setGraphic(null);
						setText(null);
						setTooltip(null);
					} else {
						String s = nz(item);
						textNode.setText(s);
						setGraphic(textNode);
						setText(null);
						setAlignment(Pos.CENTER);
						Tooltip tip = new Tooltip(s);
						tip.setWrapText(true);
						tip.setMaxWidth(360);
						setTooltip(tip);
					}
				}
			});
		}

		if (colKind != null) {
			colKind.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
			colKind.setCellFactory(col -> new TableCell<>() {
				@Override
				protected void updateItem(TaxMasterItem row, boolean empty) {
					super.updateItem(row, empty);
					if (empty || row == null) {
						setGraphic(null);
						return;
					}
					String k = nz(row.getCodeType()).toUpperCase(Locale.ROOT);
					if (k.isEmpty()) {
						k = "HSN";
					}
					Label pill = new Label(k);
					String css = "SAC".equals(k) ? "tax-master-pill-sac" : "tax-master-pill-hsn";
					pill.getStyleClass().add(css);
					pill.setAlignment(Pos.CENTER);
					setGraphic(pill);
					setAlignment(Pos.CENTER);
				}
			});
		}

		if (colGst != null) {
			colGst.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
			colGst.setCellFactory(col -> new TableCell<>() {
				@Override
				protected void updateItem(TaxMasterItem row, boolean empty) {
					super.updateItem(row, empty);
					if (empty || row == null) {
						setGraphic(null);
						return;
					}
					HBox box = new HBox(8);
					box.setAlignment(Pos.CENTER);
					Region dot = new Region();
					dot.getStyleClass().addAll("tax-master-gst-dot", gstDotSuffix(row.getGstRate()));
					Label pct = new Label(formatGst(row.getGstRate()));
					pct.getStyleClass().add("tax-master-gst-label");
					box.getChildren().addAll(dot, pct);
					setGraphic(box);
					setAlignment(Pos.CENTER);
				}
			});
		}

		if (colStatus != null) {
			colStatus.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
			colStatus.setCellFactory(col -> new TableCell<>() {
				@Override
				protected void updateItem(TaxMasterItem row, boolean empty) {
					super.updateItem(row, empty);
					if (empty || row == null) {
						setGraphic(null);
						return;
					}
					Label lab = new Label(row.isActive() ? "Active" : "Inactive");
					lab.getStyleClass().add(row.isActive() ? "tax-master-pill-status-active" : "tax-master-pill-status-inactive");
					lab.setAlignment(Pos.CENTER);
					setGraphic(lab);
					setAlignment(Pos.CENTER);
				}
			});
		}

		if (colActions != null) {
			colActions.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
			colActions.setCellFactory(col -> new TableCell<>() {
				@Override
				protected void updateItem(TaxMasterItem row, boolean empty) {
					super.updateItem(row, empty);
					if (empty || row == null) {
						setGraphic(null);
						return;
					}
					Button edit = new Button();
					edit.getStyleClass().add("tax-master-icon-btn");
					edit.getStyleClass().add("tax-master-icon-edit");
					Region editIcon = new Region();
					editIcon.getStyleClass().add("tax-master-inline-icon-edit");
					edit.setGraphic(editIcon);
					edit.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
					edit.setOnAction(e -> beginEdit(row));

					Button del = new Button();
					del.getStyleClass().addAll("tax-master-icon-btn", "tax-master-icon-danger");
					Region trashIcon = new Region();
					trashIcon.getStyleClass().add("tax-master-inline-icon-trash");
					del.setGraphic(trashIcon);
					del.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
					del.setOnAction(e -> confirmDeactivate(row));

					HBox actions = new HBox(6, edit, del);
					actions.setAlignment(Pos.CENTER);
					setGraphic(actions);
					setAlignment(Pos.CENTER);
				}
			});
		}
	}

	private void wireListeners() {
		if (txtSearch != null) {
			txtSearch.textProperty().addListener((o, a, b) -> {
				currentPage = 0;
				reloadTable();
			});
		}
		if (comboCategory != null) {
			comboCategory.valueProperty().addListener((o, a, b) -> {
				currentPage = 0;
				reloadTable();
			});
		}
		if (comboTypeFilter != null) {
			comboTypeFilter.valueProperty().addListener((o, a, b) -> {
				currentPage = 0;
				reloadTable();
			});
		}
	}

	private String filterCategoryValue() {
		if (comboCategory == null) {
			return null;
		}
		String v = comboCategory.getValue();
		if (v == null || ALL_CATEGORIES.equals(v)) {
			return null;
		}
		return v.trim();
	}

	private String filterTypeValue() {
		if (comboTypeFilter == null) {
			return null;
		}
		String v = comboTypeFilter.getValue();
		if (v == null || ALL_TYPES.equals(v)) {
			return null;
		}
		return v.trim();
	}

	private void reloadTable() {
		String q = txtSearch != null ? txtSearch.getText() : "";
		Integer af = activeListFilter;
		int total;
		try {
			total = taxService.countFiltered(q, filterCategoryValue(), filterTypeValue(), af);
		} catch (Exception ex) {
			ex.printStackTrace();
			toast("Could not load tax items.");
			return;
		}

		int totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
		if (currentPage > totalPages - 1) {
			currentPage = Math.max(0, totalPages - 1);
		}

		List<TaxMasterItem> page;
		try {
			page = taxService.listPage(q, filterCategoryValue(), filterTypeValue(), af,
					currentPage * PAGE_SIZE, PAGE_SIZE);
		} catch (Exception ex) {
			ex.printStackTrace();
			toast("Could not load tax items.");
			return;
		}

		tableRows.setAll(page);
		tableTax.refresh();

		if (lblPageInfo != null) {
			if (total == 0) {
				lblPageInfo.setText("Showing 0 of 0 items");
			} else {
				int from = currentPage * PAGE_SIZE + 1;
				int to = Math.min(total, (currentPage + 1) * PAGE_SIZE);
				lblPageInfo.setText("Showing " + from + " to " + to + " of " + total + " items");
			}
		}
		if (btnPrev != null) {
			btnPrev.setDisable(currentPage <= 0);
		}
		if (btnNext != null) {
			btnNext.setDisable(currentPage >= totalPages - 1 || total == 0);
		}
		rebuildPageNumberButtons(totalPages);
	}

	private void rebuildPageNumberButtons(int totalPages) {
		if (boxPageNumbers == null) {
			return;
		}
		boxPageNumbers.getChildren().clear();
		if (totalPages <= 1) {
			return;
		}
		final int maxButtons = 9;
		int start = 0;
		int end = totalPages;
		if (totalPages > maxButtons) {
			start = Math.max(0, currentPage - maxButtons / 2);
			end = Math.min(totalPages, start + maxButtons);
			if (end - start < maxButtons) {
				start = Math.max(0, end - maxButtons);
			}
		}
		for (int p = start; p < end; p++) {
			final int pageIdx = p;
			Button b = new Button(Integer.toString(pageIdx + 1));
			b.setFocusTraversable(false);
			b.getStyleClass().add("tax-master-page-num");
			if (pageIdx == currentPage) {
				b.getStyleClass().add("tax-master-page-num-active");
			}
			b.setOnAction(e -> {
				currentPage = pageIdx;
				reloadTable();
			});
			boxPageNumbers.getChildren().add(b);
		}
	}

	@FXML
	private void handlePrevPage() {
		if (currentPage > 0) {
			currentPage--;
			reloadTable();
		}
	}

	@FXML
	private void handleNextPage() {
		currentPage++;
		reloadTable();
	}

	@FXML
	private void handleToggleInactive() {
		activeListFilter = activeListFilter == 1 ? 0 : 1;
		currentPage = 0;
		if (btnToggleInactive != null) {
			btnToggleInactive.setText(activeListFilter == 1 ? "View Inactive Items" : "View Active Items");
		}
		reloadTable();
	}

	@FXML
	private void handleCancelForm() {
		editingSnapshot = null;
		if (lblFormTitle != null) {
			lblFormTitle.setText("Add New Tax Item");
		}
		clearForm(false);
	}

	private void clearForm(boolean selectDefaults) {
		if (fldItemName != null) {
			fldItemName.clear();
		}
		if (fldHsnSac != null) {
			fldHsnSac.clear();
		}
		if (fldUnit != null) {
			fldUnit.clear();
		}
		if (fldDescription != null) {
			fldDescription.clear();
		}
		if (chkFavorite != null) {
			chkFavorite.setSelected(false);
		}
		if (fldCodeType != null) {
			fldCodeType.getSelectionModel().selectFirst();
		}
		if (fldGstRate != null) {
			fldGstRate.getSelectionModel().select("18%");
		}
		reloadCategories();
		if (selectDefaults && fldCategory != null && !fldCategory.getItems().isEmpty()) {
			fldCategory.getSelectionModel().selectFirst();
		}
	}

	private void beginEdit(TaxMasterItem row) {
		if (row == null) {
			return;
		}
		TaxMasterItem fresh = taxService.findByUuid(row.getUuid());
		if (fresh == null) {
			toast("Could not load this row.");
			reloadTable();
			return;
		}
		editingSnapshot = fresh;
		if (lblFormTitle != null) {
			lblFormTitle.setText("Edit Tax Item");
		}
		if (fldItemName != null) {
			fldItemName.setText(fresh.getItemName());
		}
		if (fldCategory != null) {
			reloadCategories();
			String cat = fresh.getItemType();
			if (!fldCategory.getItems().contains(cat)) {
				fldCategory.getItems().add(cat);
			}
			fldCategory.getSelectionModel().select(cat);
		}
		if (fldHsnSac != null) {
			fldHsnSac.setText(fresh.getHsnSac());
		}
		if (fldCodeType != null) {
			String t = fresh.getCodeType().toUpperCase(Locale.ROOT);
			if (!fldCodeType.getItems().contains(t)) {
				fldCodeType.getItems().add(t);
			}
			fldCodeType.getSelectionModel().select(t);
		}
		if (fldGstRate != null) {
			String pctLabel = toGstComboLabel(fresh.getGstRate());
			if (!fldGstRate.getItems().contains(pctLabel)) {
				fldGstRate.getItems().add(pctLabel);
			}
			fldGstRate.getSelectionModel().select(pctLabel);
		}
		if (fldUnit != null) {
			fldUnit.setText(fresh.getUnitDefault());
		}
		if (fldDescription != null) {
			fldDescription.setText(fresh.getDescription());
		}
		if (chkFavorite != null) {
			chkFavorite.setSelected(fresh.isFavorite());
		}
	}

	private void confirmDeactivate(TaxMasterItem row) {
		if (row == null) {
			return;
		}
		if (!row.isActive()) {
			toast("Already inactive. Delete from DB is not supported here.");
			return;
		}
		if (!confirm("Deactivate item",
				"Deactivate \"" + row.getItemName() + "\"?\nPrefer this over delete so past invoices stay consistent.")) {
			return;
		}
		try {
			taxService.setActive(row.getUuid(), false);
			toast("Item deactivated.");
			reloadTable();
			if (editingSnapshot != null && editingSnapshot.getUuid().equals(row.getUuid())) {
				editingSnapshot.setActive(false);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			toast("Could not deactivate: " + ex.getMessage());
		}
	}

	@FXML
	private void handleSave() {
		String name = fldItemName != null ? fldItemName.getText().trim() : "";
		String cat = fldCategory != null && fldCategory.getValue() != null ? fldCategory.getValue().trim() : "";
		String code = fldHsnSac != null ? fldHsnSac.getText().trim() : "";
		String type = fldCodeType != null && fldCodeType.getValue() != null ? fldCodeType.getValue().trim() : "HSN";

		if (name.isEmpty()) {
			toast("Enter item name.");
			return;
		}
		if (cat.isEmpty()) {
			toast("Select category.");
			return;
		}
		if (!isValidHsnSac(code)) {
			toast("Enter a valid HSN/SAC code (mandatory for GST invoices).");
			return;
		}

		double gst = parseGstCombo(fldGstRate != null ? fldGstRate.getValue() : null);

		TaxMasterItem row = new TaxMasterItem();
		if (editingSnapshot != null) {
			row.setUuid(editingSnapshot.getUuid());
			row.setActive(editingSnapshot.isActive());
		} else {
			row.setActive(true);
		}
		row.setItemType(cat.toUpperCase(Locale.ROOT));
		row.setItemName(name);
		row.setKeyword(name);
		row.setCodeType(type);
		row.setHsnSac(code);
		row.setGstRate(gst);
		row.setUnitDefault(fldUnit != null ? fldUnit.getText().trim() : "");
		row.setDescription(fldDescription != null ? fldDescription.getText().trim() : "");
		row.setFavorite(chkFavorite != null && chkFavorite.isSelected());

		try {
			taxService.save(row);
			toast(editingSnapshot != null ? "Tax item updated." : "Tax item saved.");
			editingSnapshot = (row.getUuid() != null && !row.getUuid().isBlank()) ? taxService.findByUuid(row.getUuid()) : null;
			if (lblFormTitle != null && editingSnapshot != null) {
				lblFormTitle.setText("Edit Tax Item");
			}
			reloadCategories();
			reloadTable();
		} catch (IllegalArgumentException ex) {
			toast(ex.getMessage());
		} catch (Exception ex) {
			ex.printStackTrace();
			toast("Save failed: " + ex.getMessage());
		}
	}

	private static boolean isValidHsnSac(String raw) {
		if (raw == null || raw.isBlank()) {
			return false;
		}
		String t = raw.trim();
		if ("—".equals(t) || "-".equals(t) || ".".equals(t)) {
			return false;
		}
		if (t.equalsIgnoreCase("na") || t.equalsIgnoreCase("n/a") || t.equalsIgnoreCase("none") || t.equalsIgnoreCase("nil")) {
			return false;
		}
		return true;
	}

	private static double parseGstCombo(String label) {
		if (label == null || label.isBlank()) {
			return 0.18;
		}
		String s = label.trim().replace("%", "");
		try {
			double v = Double.parseDouble(s);
			return v / 100.0;
		} catch (NumberFormatException e) {
			return 0.18;
		}
	}

	private static String toGstComboLabel(double rate) {
		double pct = rate * 100.0;
		if (Math.abs(pct - Math.rint(pct)) < 1e-6) {
			return (int) Math.rint(pct) + "%";
		}
		return String.format(Locale.ROOT, "%.2f%%", pct);
	}

	private static String formatGst(double rate) {
		double pct = rate * 100.0;
		if (Math.abs(pct - Math.rint(pct)) < 1e-6) {
			return (int) Math.rint(pct) + "%";
		}
		return String.format(Locale.ROOT, "%.2f%%", pct);
	}

	private static String gstDotSuffix(double rate) {
		double pct = rate * 100.0;
		if (pct <= 0.5) {
			return "gst-0";
		}
		if (pct <= 6) {
			return "gst-5";
		}
		if (pct <= 10) {
			return "gst-12";
		}
		if (pct <= 15) {
			return "gst-18";
		}
		return "gst-28";
	}

	private boolean confirm(String title, String msg) {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(msg);
		try {
			java.net.URL css = getClass().getResource("/css/theme.css");
			if (css != null) {
				alert.getDialogPane().getStylesheets().add(css.toExternalForm());
			}
			alert.getDialogPane().getStyleClass().add("atelier-alert");
		} catch (Exception e) { service.LoggerService.dbWarn("Failed to export template: " + e.getMessage()); }
		return alert.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) == javafx.scene.control.ButtonType.OK;
	}

	private void toast(String message) {
		Node anchor = tableTax != null ? tableTax : breadcrumbContainer;
		if (anchor == null || anchor.getScene() == null) {
			return;
		}
		Stage stage = (Stage) anchor.getScene().getWindow();
		Toast.show(stage, message);
	}

	private static String nz(String s) {
		return s != null ? s : "";
	}
}
