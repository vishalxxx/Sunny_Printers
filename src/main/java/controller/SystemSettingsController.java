package controller;

import java.net.URL;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.ResourceBundle;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.MasterDocumentSeries;
import model.SystemSettings;
import repository.SystemSettingsRepository;
import utils.AtomicDB;
import utils.CompanyProfile;
import utils.DBConnection;
import utils.DocumentNumbering;

public class SystemSettingsController implements Initializable, utils.DirtySupport {

	private final SystemSettingsRepository repo = new SystemSettingsRepository();
	private SystemSettings settings;
	private final Map<MasterDocumentSeries, Spinner<Integer>> seriesSpinners = new EnumMap<>(MasterDocumentSeries.class);
	private final ChangeListener<Node> kpiFocusListener = (obs, prev, cur) -> refreshKpiStrip();

	@Override
	public boolean hasUnsavedChanges() {
		if (settings == null || paddingCombo == null) {
			return false;
		}
		try {
			if (!paddingCombo.getValue().equals(settings.getInvoicePadding())) {
				return true;
			}
			for (MasterDocumentSeries s : MasterDocumentSeries.values()) {
				Spinner<Integer> sp = seriesSpinners.get(s);
				if (sp == null) {
					continue;
				}
				int nextInUi = sp.getValue();
				int nextInDb = settings.getLastSeq(s) + 1;
				if (nextInUi != nextInDb) {
					return true;
				}
			}
			return false;
		} catch (Exception e) {
			return true;
		}
	}

	@FXML
	private HBox breadcrumbContainer;

	@FXML
	private TextField fyField;

	@FXML
	private Label kpiFocusTitle;

	@FXML
	private Label kpiFocusHint;

	@FXML
	private Label kpiPreviewValue;

	@FXML
	private Label kpiPreviewCaption;

	@FXML
	private ComboBox<Integer> paddingCombo;

	@FXML
	private GridPane seriesGrid;

	@FXML
	private Button saveBtn;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null,
				() -> MainController.getInstance().handleBack(null));

		if (seriesGrid == null) {
			return;
		}
		paddingCombo.setItems(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6));

		buildSeriesGrid();

		paddingCombo.valueProperty().addListener((o, prev, cur) -> {
			for (Spinner<Integer> sp : seriesSpinners.values()) {
				applyPaddedEditorText(sp);
			}
			refreshKpiStrip();
		});

		installKpiFocusTracking();

		loadSettings();

		saveBtn.setOnAction(e -> save());
	}

	private void buildSeriesGrid() {
		seriesGrid.getChildren().clear();
		seriesSpinners.clear();

		MasterDocumentSeries[] vals = MasterDocumentSeries.values();
		int row = 0;
		for (int i = 0; i < vals.length; i += 2) {
			VBox left = createSeriesField(vals[i]);
			GridPane.setHgrow(left, Priority.ALWAYS);
			seriesGrid.add(left, 0, row);
			if (i + 1 < vals.length) {
				VBox right = createSeriesField(vals[i + 1]);
				GridPane.setHgrow(right, Priority.ALWAYS);
				seriesGrid.add(right, 1, row);
			}
			row++;
		}
	}

	private VBox createSeriesField(MasterDocumentSeries s) {
		VBox box = new VBox(8);
		Label head = new Label(s.getLabel() + " (" + s.getTypeCode() + ")");
		head.getStyleClass().add("field-heading-sm");
		head.setWrapText(true);

		Spinner<Integer> sp = new Spinner<>();
		sp.getStyleClass().add("settings-series-spinner");
		sp.setEditable(true);
		sp.setMaxWidth(Double.MAX_VALUE);
		SpinnerValueFactory.IntegerSpinnerValueFactory fact = new SpinnerValueFactory.IntegerSpinnerValueFactory(1,
				9_999_999, 1);
		sp.setValueFactory(fact);

		sp.valueProperty().addListener((obs, oldV, newV) -> {
			if (newV != null) {
				applyPaddedEditorText(sp);
				refreshKpiStrip();
			}
		});
		sp.getEditor().focusedProperty().addListener((obs, was, focused) -> {
			if (Boolean.FALSE.equals(focused)) {
				commitSpinnerEditor(sp);
			}
		});

		box.getChildren().addAll(head, sp);
		seriesSpinners.put(s, sp);
		return box;
	}

	private int effectiveDigitWidth() {
		Integer p = paddingCombo != null ? paddingCombo.getValue() : null;
		return Math.max(1, p != null ? p : 4);
	}

	private void applyPaddedEditorText(Spinner<Integer> sp) {
		if (sp == null) {
			return;
		}
		int pad = effectiveDigitWidth();
		int v = sp.getValue() != null ? sp.getValue() : 1;
		sp.getEditor().setText(String.format("%0" + pad + "d", v));
	}

	private void commitSpinnerEditor(Spinner<Integer> sp) {
		if (sp == null) {
			return;
		}
		try {
			String raw = sp.getEditor().getText().trim().replaceAll("[^0-9]", "");
			if (raw.isEmpty()) {
				raw = "1";
			}
			int val = Integer.parseInt(raw);
			val = Math.max(1, Math.min(9_999_999, val));
			sp.getValueFactory().setValue(val);
		} catch (NumberFormatException ex) {
			sp.getValueFactory().setValue(((SpinnerValueFactory.IntegerSpinnerValueFactory) sp.getValueFactory()).getValue());
		}
		applyPaddedEditorText(sp);
	}

	private void formatAllSeriesSpinners() {
		for (Spinner<Integer> sp : seriesSpinners.values()) {
			applyPaddedEditorText(sp);
		}
	}

	private void loadSettings() {
		try {
			settings = AtomicDB.run(con -> {
				try {
					SystemSettings s = repo.load(con);
					s.alignFinancialYearTo(LocalDate.now());
					repo.save(con, s);
					return s;
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});

			int pad = settings.getInvoicePadding() > 0 ? settings.getInvoicePadding() : 4;
			paddingCombo.setValue(pad);

			for (MasterDocumentSeries s : MasterDocumentSeries.values()) {
				Spinner<Integer> sp = seriesSpinners.get(s);
				if (sp != null) {
					int next = settings.getLastSeq(s) + 1;
					((SpinnerValueFactory.IntegerSpinnerValueFactory) sp.getValueFactory()).setValue(next);
				}
			}

			formatAllSeriesSpinners();
			updateFinancialYearField();
			refreshKpiStrip();
		} catch (Exception e) {
			showError("Failed to load system settings", e);
		}
	}

	private void updateFinancialYearField() {
		if (fyField != null && settings != null) {
			String fy = settings.getNumberingFy();
			if (fy == null || fy.isBlank()) {
				fy = DocumentNumbering.financialYearLabel(LocalDate.now());
			}
			fyField.setText(fy + "  ·  Apr – Mar");
		}
		refreshKpiStrip();
	}

	private void installKpiFocusTracking() {
		Node anchor = fyField != null ? fyField : paddingCombo;
		if (anchor == null) {
			anchor = seriesGrid;
		}
		if (anchor == null) {
			return;
		}
		anchor.sceneProperty().addListener((obs, oldScene, newScene) -> {
			if (oldScene != null) {
				oldScene.focusOwnerProperty().removeListener(kpiFocusListener);
			}
			if (newScene != null) {
				newScene.focusOwnerProperty().addListener(kpiFocusListener);
			}
			refreshKpiStrip();
		});
	}

	private String fyLabelFromField() {
		if (fyField == null || fyField.getText() == null) {
			return DocumentNumbering.financialYearLabel(LocalDate.now());
		}
		String t = fyField.getText().trim();
		int dot = t.indexOf('·');
		if (dot > 0) {
			t = t.substring(0, dot).trim();
		}
		return t.isBlank() ? DocumentNumbering.financialYearLabel(LocalDate.now()) : t;
	}

	private static boolean isNodeInside(Node n, Node container) {
		if (n == null || container == null) {
			return false;
		}
		for (Node x = n; x != null; x = x.getParent()) {
			if (x == container) {
				return true;
			}
		}
		return false;
	}

	private MasterDocumentSeries focusedSeriesForNode(Node n) {
		for (Node x = n; x != null; x = x.getParent()) {
			for (Map.Entry<MasterDocumentSeries, Spinner<Integer>> e : seriesSpinners.entrySet()) {
				if (x == e.getValue()) {
					return e.getKey();
				}
			}
		}
		return null;
	}

	private void refreshKpiStrip() {
		if (kpiFocusTitle == null || kpiFocusHint == null || kpiPreviewValue == null
				|| kpiPreviewCaption == null) {
			return;
		}

		Node focus = null;
		if (fyField != null && fyField.getScene() != null) {
			focus = fyField.getScene().getFocusOwner();
		}

		String cp = DocumentNumbering.companyPrefixFromTradeName(CompanyProfile.getName());
		int pad = effectiveDigitWidth();
		String fy = fyLabelFromField();
		MasterDocumentSeries series = focusedSeriesForNode(focus);

		Spinner<Integer> invSpinner = seriesSpinners.get(MasterDocumentSeries.GST_INVOICE);
		int sampleSeq = invSpinner != null && invSpinner.getValue() != null ? invSpinner.getValue() : 1;

		if (focus != null && fyField != null && isNodeInside(focus, fyField)) {
			kpiFocusTitle.setText("Current financial year");
			kpiFocusHint.setText("Indian FY (Apr–Mar) shown between the type code and the sequence.");
			kpiPreviewValue.setText(
					DocumentNumbering.formatMasterLine(cp, MasterDocumentSeries.GST_INVOICE.getTypeCode(), fy, sampleSeq, pad));
			kpiPreviewCaption.setText("Example: next GST invoice with your current prefix and padding.");
		} else if (focus != null && paddingCombo != null && isNodeInside(focus, paddingCombo)) {
			kpiFocusTitle.setText("Digit width");
			kpiFocusHint.setText("Leading zeros in the last segment (for example 0001 versus 01).");
			kpiPreviewValue.setText(
					DocumentNumbering.formatMasterLine(cp, MasterDocumentSeries.GST_INVOICE.getTypeCode(), fy, sampleSeq, pad));
			kpiPreviewCaption.setText("Example: same GST invoice; change digit width to update padding.");
		} else if (series != null) {
			kpiFocusTitle.setText(series.getLabel() + " (" + series.getTypeCode() + ")");
			kpiFocusHint.setText("Full number format: PREFIX / TYPE / FY / SEQUENCE.");
			Spinner<Integer> sp = seriesSpinners.get(series);
			int seq = sp != null && sp.getValue() != null ? sp.getValue() : 1;
			kpiPreviewValue.setText(DocumentNumbering.formatMasterLine(cp, series.getTypeCode(), fy, seq, pad));
			kpiPreviewCaption.setText(
					"Company prefix comes from the trade name in General Settings.");
		} else {
			kpiFocusTitle.setText("Document numbering");
			kpiFocusHint.setText("Focus a field below to see title, hint, and a live formatted example.");
			kpiPreviewValue.setText(
					DocumentNumbering.formatMasterLine(cp, MasterDocumentSeries.GST_INVOICE.getTypeCode(), fy, sampleSeq, pad));
			kpiPreviewCaption.setText("Example: next GST invoice — click any row to preview that type.");
		}
	}

	private void save() {
		try (Connection con = DBConnection.getConnection()) {
			con.setAutoCommit(false);
			try {
				SystemSettings s = repo.load(con);
				LocalDate today = LocalDate.now();
				s.alignFinancialYearTo(today);

				s.setInvoiceMode("MANUAL");
				int pad = paddingCombo.getValue();
				s.setInvoicePadding(pad);
				s.setJobPadding(pad);

				for (MasterDocumentSeries series : MasterDocumentSeries.values()) {
					Spinner<Integer> sp = seriesSpinners.get(series);
					if (sp != null) {
						int next = sp.getValue();
						s.setLastSeq(series, Math.max(0, next - 1));
					}
				}

				repo.save(con, s);
				con.commit();
				settings = s;
				updateFinancialYearField();
				showInfo("Configuration saved.");
			} catch (Exception e) {
				con.rollback();
				throw e;
			}
		} catch (Exception e) {
			showError("Save failed", e);
		}
	}

	private void showInfo(String msg) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setHeaderText("Message");
		alert.setContentText(msg);
		alert.initStyle(javafx.stage.StageStyle.TRANSPARENT);
		alert.getDialogPane().setBackground(null);
		alert.getDialogPane().getStyleClass().add("settings-warm-dialog");
		alert.getDialogPane().getStylesheets()
				.add(getClass().getResource("/css/theme.css").toExternalForm());
		alert.getDialogPane().getStylesheets()
				.add(getClass().getResource("/css/settings_screens.css").toExternalForm());
		alert.showAndWait();
	}

	private void showError(String msg, Exception e) {
		e.printStackTrace();
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setHeaderText("Error");
		alert.setContentText(msg + "\n" + e.getMessage());
		alert.initStyle(javafx.stage.StageStyle.TRANSPARENT);
		alert.getDialogPane().setBackground(null);
		alert.getDialogPane().getStyleClass().add("settings-warm-dialog");
		alert.getDialogPane().getStylesheets()
				.add(getClass().getResource("/css/theme.css").toExternalForm());
		alert.getDialogPane().getStylesheets()
				.add(getClass().getResource("/css/settings_screens.css").toExternalForm());
		alert.showAndWait();
	}
}
