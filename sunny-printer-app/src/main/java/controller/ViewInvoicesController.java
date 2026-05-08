package controller;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import model.Client;
import model.Invoice;
import model.InvoiceMaster;
import model.InvoiceAdjustment;
import service.ClientService;
import service.InvoiceBuilderService;
import service.InvoiceMasterService;
import service.PdfInvoiceService;
import utils.DocumentNumbering;
import utils.Toast;

import java.net.URL;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ViewInvoicesController {

    @FXML private ComboBox<Client> clientComboBox;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TextField invoiceSearchField;

    @FXML private TableView<InvoiceMaster> invoiceTable;
    @FXML private TableColumn<InvoiceMaster, String> colSelect;
    @FXML private TableColumn<InvoiceMaster, String> colInvoiceNo;
    @FXML private TableColumn<InvoiceMaster, String> colClient;
    @FXML private TableColumn<InvoiceMaster, String> colIssueDate;
    @FXML private TableColumn<InvoiceMaster, String> colDueDate;
    @FXML private TableColumn<InvoiceMaster, Double> colAmount;
    @FXML private TableColumn<InvoiceMaster, String> colStatus;
    @FXML private TableColumn<InvoiceMaster, Void> colActions;

    @FXML private Button btnEdit, btnFinalize, btnSend, btnPayment, btnCancel, btnRevised, btnRaiseCnDn;
    @FXML private Label paginationInfoLabel;
    @FXML private HBox paginationPagesBox;
    @FXML private TextField goToPageField;
    @FXML private Arc viDonutArcPaid;
    @FXML private Arc viDonutArcUnpaid;
    @FXML private Arc viDonutArcOverdue;
    @FXML private Label viDonutCenterCount;
    @FXML private Label viDonutCenterSubtitle;
    @FXML private Label viDonutLegendPaid;
    @FXML private Label viDonutLegendUnpaid;
    @FXML private Label viDonutLegendOverdue;
    /** Paid/unpaid/overdue donut card; visible only when no invoice row is selected. */
    @FXML private VBox viStatusOverviewBox;
    @FXML private Label sumCountLabel;
    @FXML private Label sumBaseLabel;
    @FXML private Label sumAdjustmentLabel;
    @FXML private Label sumNetLabel;
    @FXML private Label sumPaidLabel;
    @FXML private Label sumDueLabel;
    /** Subtitle: date range + count when no row selected; invoice + client when selected. */
    @FXML private Label summaryContextLabel;
    @FXML private VBox summarySingleInvoiceSection;
    @FXML private VBox summaryBreakdownBox;
    /** Full screen: click outside table clears row selection (toolbar excluded). */
    @FXML private VBox viScreenRoot;
    @FXML private ToggleButton quickAllBtn;
    @FXML private ToggleButton quickPaidBtn;
    @FXML private ToggleButton quickUnpaidBtn;
    @FXML private ToggleButton quickOverdueBtn;
    @FXML private HBox breadcrumbContainer;
    /** Top bulk-action bar; hidden when no invoice row is selected. */
    @FXML private HBox viToolbarRow;

    public static String pendingSearchInvoiceNo;
    private static final int ALL_CLIENTS_ID = 0;

    private static final DateTimeFormatter ISSUE_DUE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
    /** Placeholder band when toolbar actions hidden — same height as last shown bar avoids table jump. */
    private static final double VI_TOOLBAR_MIN_SLOT = 52;

    private final Map<Integer, String> clientIdToEmail = new HashMap<>();
    private final Set<Integer> selectedInvoiceIds = new HashSet<>();

    private final ClientService clientService = new ClientService();
    private final InvoiceMasterService invoiceMasterService = new InvoiceMasterService();
    /** Current page rows shown in the table (same object references as {@link #fullInvoiceResults}). */
    private final ObservableList<InvoiceMaster> tablePageItems = FXCollections.observableArrayList();
    private final List<InvoiceMaster> fullInvoiceResults = new ArrayList<>();
    private int currentPageIndex = 0;
    private int pageSize = 20;
    /** Last laid-out height of {@link #viToolbarRow} with buttons visible; drives reserved slot when hidden. */
    private double viToolbarReservedHeight = VI_TOOLBAR_MIN_SLOT;

    private static ViewInvoicesController instance;
    public static ViewInvoicesController getInstance() { return instance; }

    public void refresh() {
        handleSearch(null);
    }

    @FXML
    public void initialize() {
        try {
            instance = this;
            setupTableColumns();
            loadClients();
            loadStatuses();
            
            utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null, () -> handleBack(null));
            
            setupAutoPopupDatePicker(startDatePicker);
            setupAutoPopupDatePicker(endDatePicker);

            if (startDatePicker != null) {
                startDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
            }
            if (endDatePicker != null) {
                endDatePicker.setValue(LocalDate.now());
            }

            setupStatusDonut();
            setupQuickFilterSync();
            setupLiveFilters();

            invoiceTable.setItems(tablePageItems);
            invoiceTable.setFixedCellSize(58);

            invoiceTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
                updateButtonStates(newSel);
                refreshInvoiceSummaryPanel();
            });

            setupTableDeselectOnOutsideClick();

            invoiceTable.setRowFactory(tv -> {
                TableRow<InvoiceMaster> row = new TableRow<>();
                /*
                 * Checkbox column explicitly calls selectionModel.select(inv). Clicks on other
                 * cells should drive the same "one selected invoice" UX (toolbar + summary). Some
                 * skins/picks do not update selection reliably from every cell; selecting the
                 * row item on primary press keeps behavior consistent.
                 */
                row.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                    if (e.getButton() != MouseButton.PRIMARY || row.isEmpty() || invoiceTable == null) {
                        return;
                    }
                    InvoiceMaster item = row.getItem();
                    if (item != null) {
                        invoiceTable.getSelectionModel().select(item);
                    }
                });
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && (!row.isEmpty())) {
                        InvoiceMaster selected = row.getItem();
                        String stat = selected.getStatus() != null ? selected.getStatus().toUpperCase() : "";
                        if ("DRAFT".equals(stat)) {
                            handleEditAction(null);
                        } else {
                            handleViewOnlyAction(selected);
                        }
                    }
                });
                return row;
            });

            updateButtonStates(null);

            Platform.runLater(() -> {
                try {
                    if (pendingSearchInvoiceNo != null) {
                        if (invoiceSearchField != null) invoiceSearchField.setText(pendingSearchInvoiceNo);
                        pendingSearchInvoiceNo = null;
                    }
                    handleSearch(null);
                    if (invoiceTable != null) {
                        ensureViewInvoicesMenuCss(invoiceTable.getScene());
                    }
                } catch (Exception e) { e.printStackTrace(); }
            });
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR initializing ViewInvoicesController: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clear selection on empty row / table body filler, or outside the table.
     * <p>
     * Important: {@link MouseEvent#MOUSE_CLICKED} is delivered <em>after</em> the row
     * selection is applied on {@link MouseEvent#MOUSE_RELEASED}. A {@code addEventFilter}
     * on the table runs in the capture phase of {@code MOUSE_CLICKED} and was calling
     * {@code clearSelection()}, which wiped the row the user had just selected — toolbar
     * flashed then disappeared. Table-body deselect therefore uses {@code addEventHandler}
     * (bubbling) so it runs after default selection handling.
     */
    private void setupTableDeselectOnOutsideClick() {
        if (invoiceTable != null) {
            invoiceTable.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }
                Node t = (Node) e.getTarget();
                if (isUnderInvoiceTableNonRowChrome(t)) {
                    return;
                }
                if (isClickOnDataInvoiceRow(t)) {
                    return;
                }
                TableRow<?> hitRow = nearestTableRow(t);
                if (hitRow != null) {
                    if (!hitRow.isEmpty()) {
                        return;
                    }
                    invoiceTable.getSelectionModel().clearSelection();
                    return;
                }
                for (Node n = t; n != null; n = n.getParent()) {
                    if (n == invoiceTable) {
                        invoiceTable.getSelectionModel().clearSelection();
                        return;
                    }
                }
            });
        }
        if (viScreenRoot != null && invoiceTable != null) {
            viScreenRoot.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }
                Node t = (Node) e.getTarget();
                if (isUnderViewInvoicesSidebar(t)) {
                    return;
                }
                Node n = t;
                while (n != null) {
                    if (n == invoiceTable || n == viToolbarRow) {
                        return;
                    }
                    n = n.getParent();
                }
                invoiceTable.getSelectionModel().clearSelection();
            });
        }
    }

    private static boolean isUnderViewInvoicesSidebar(Node target) {
        for (Node n = target; n != null; n = n.getParent()) {
            if (n.getStyleClass().contains("vi-sidebar")) {
                return true;
            }
        }
        return false;
    }

    /** True if the click is on a non-empty data row (cell or row chrome under that row). */
    private static boolean isClickOnDataInvoiceRow(Node target) {
        for (Node n = target; n != null; n = n.getParent()) {
            if (n instanceof TableCell) {
                TableRow<?> row = ((TableCell<?, ?>) n).getTableRow();
                if (row != null && !row.isEmpty() && row.getItem() != null) {
                    return true;
                }
            }
            if (n instanceof TableRow) {
                TableRow<?> tr = (TableRow<?>) n;
                if (!tr.isEmpty() && tr.getItem() != null) {
                    return true;
                }
            }
            if (n instanceof TableView) {
                break;
            }
        }
        return false;
    }

    private static TableRow<?> nearestTableRow(Node target) {
        for (Node n = target; n != null; n = n.getParent()) {
            if (n instanceof TableRow) {
                return (TableRow<?>) n;
            }
            if (n instanceof TableCell) {
                return ((TableCell<?, ?>) n).getTableRow();
            }
            if (n instanceof TableView) {
                break;
            }
        }
        return null;
    }

    private static boolean isUnderInvoiceTableNonRowChrome(Node target) {
        for (Node n = target; n != null; n = n.getParent()) {
            if (n instanceof ScrollBar) {
                return true;
            }
            String cn = n.getClass().getName();
            if (cn.contains("TableHeaderRow") || cn.contains("NestedTableColumnHeader")) {
                return true;
            }
        }
        return false;
    }

    private void setupLiveFilters() {
        if (invoiceSearchField != null) {
            invoiceSearchField.textProperty().addListener((obs, oldVal, newVal) -> handleSearch(null));
        }
        if (clientComboBox != null) {
            clientComboBox.valueProperty().addListener((obs, oldVal, newVal) -> handleSearch(null));
        }
        if (statusComboBox != null) {
            statusComboBox.valueProperty().addListener((obs, oldVal, newVal) -> handleSearch(null));
        }
        if (startDatePicker != null) {
            startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> handleSearch(null));
        }
        if (endDatePicker != null) {
            endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> handleSearch(null));
        }
    }

    @FXML
    private void handleBack(ActionEvent event) {
        MainController.getInstance().handleBack(event);
    }

    private void setupStatusDonut() {
        for (Arc a : new Arc[] { viDonutArcPaid, viDonutArcUnpaid, viDonutArcOverdue }) {
            if (a != null) {
                a.setType(ArcType.OPEN);
            }
        }
        if (viDonutCenterSubtitle != null) {
            viDonutCenterSubtitle.setText("INVOICES");
        }
    }

    private void setupQuickFilterSync() {
        if (statusComboBox != null) {
            statusComboBox.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> syncQuickFilterButtons());
        }
        syncQuickFilterButtons();
    }

    private void syncQuickFilterButtons() {
        String s = statusComboBox != null ? statusComboBox.getValue() : "All";
        if (s == null) {
            s = "All";
        }
        setQuickFilterActive(switch (s.toUpperCase()) {
            case "PAID" -> quickPaidBtn;
            case "UNPAID" -> quickUnpaidBtn;
            case "OVERDUE" -> quickOverdueBtn;
            default -> quickAllBtn;
        });
    }

    private void setQuickFilterActive(ToggleButton active) {
        for (ToggleButton b : new ToggleButton[] { quickAllBtn, quickPaidBtn, quickUnpaidBtn, quickOverdueBtn }) {
            if (b == null) continue;
            b.setSelected(b == active);
        }
    }

    @FXML
    private void handleQuickFilterAll(ActionEvent e) {
        if (statusComboBox != null) {
            statusComboBox.getSelectionModel().select("All");
        }
        setQuickFilterActive(quickAllBtn);
        handleSearch(null);
    }

    @FXML
    private void handleQuickFilterPaid(ActionEvent e) {
        if (statusComboBox != null) {
            statusComboBox.getSelectionModel().select("PAID");
        }
        setQuickFilterActive(quickPaidBtn);
        handleSearch(null);
    }

    @FXML
    private void handleQuickFilterUnpaid(ActionEvent e) {
        if (statusComboBox != null) {
            statusComboBox.getSelectionModel().select("UNPAID");
        }
        setQuickFilterActive(quickUnpaidBtn);
        handleSearch(null);
    }

    @FXML
    private void handleQuickFilterOverdue(ActionEvent e) {
        if (statusComboBox != null) {
            statusComboBox.getSelectionModel().select("OVERDUE");
        }
        setQuickFilterActive(quickOverdueBtn);
        handleSearch(null);
    }

    private void applyButtonIcons() {
        if (btnEdit != null) btnEdit.setGraphic(createIcon("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z"));
        if (btnFinalize != null) btnFinalize.setGraphic(createIcon("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"));
        if (btnSend != null) btnSend.setGraphic(createIcon("M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"));
        if (btnPayment != null) btnPayment.setGraphic(createIcon("M11.8 10.9c-2.27-.59-3-1.2-3-2.15 0-1.09 1.01-1.85 2.7-1.85 1.78 0 2.44.85 2.5 2.1h2.21c-.07-1.72-1.12-3.3-3.21-3.81V3h-3v2.16c-1.94.42-3.5 1.68-3.5 3.61 0 2.31 1.91 3.46 4.7 4.13 2.5.6 3 1.48 3 2.41 0 .69-.49 1.79-2.7 1.79-2.06 0-2.87-.92-2.98-2.1h-2.2c.12 2.19 1.76 3.42 3.68 3.83V21h3v-2.15c1.95-.37 3.5-1.5 3.5-3.55 0-2.84-2.43-3.81-4.7-4.4z"));
        if (btnCancel != null) btnCancel.setGraphic(createIcon("M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"));
        if (btnRevised != null) btnRevised.setGraphic(createIcon("M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"));
        if (btnRaiseCnDn != null) btnRaiseCnDn.setGraphic(createIcon("M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 14h-3v3h-2v-3H8v-2h3v-3h2v3h3v2zm-3-7V3.5L18.5 9H13z"));
    }

    private void setupTableColumns() {
        if (colSelect != null) {
            colSelect.setCellValueFactory(c -> new SimpleStringProperty(""));
            colSelect.setCellFactory(col -> new TableCell<>() {
                private final CheckBox checkBox = new CheckBox();

                {
                    checkBox.getStyleClass().add("vi-row-select-cb");
                    checkBox.setFocusTraversable(false);
                    checkBox.setMnemonicParsing(false);
                    checkBox.setOnAction(e -> {
                        InvoiceMaster inv = getTableRow().getItem();
                        if (inv == null) {
                            return;
                        }
                        if (checkBox.isSelected()) {
                            selectedInvoiceIds.add(inv.getId());
                            if (invoiceTable != null) {
                                Platform.runLater(() -> invoiceTable.getSelectionModel().select(inv));
                            }
                        } else {
                            selectedInvoiceIds.remove(inv.getId());
                            if (invoiceTable != null
                                    && invoiceTable.getSelectionModel().getSelectedItem() == inv) {
                                Platform.runLater(() -> invoiceTable.getSelectionModel().clearSelection());
                            }
                        }
                    });
                }

                @Override
                protected void updateItem(String s, boolean empty) {
                    super.updateItem(s, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        InvoiceMaster inv = getTableRow().getItem();
                        if (inv == null) {
                            setGraphic(null);
                            return;
                        }
                        checkBox.setSelected(selectedInvoiceIds.contains(inv.getId()));
                        setAlignment(Pos.CENTER);
                        setGraphic(checkBox);
                    }
                }
            });
        }
        if (colInvoiceNo != null) {
            colInvoiceNo.setCellValueFactory(new PropertyValueFactory<>("invoiceNo"));
            colInvoiceNo.setCellFactory(c -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setAlignment(Pos.CENTER_LEFT);
                    getStyleClass().remove("vi-invoice-id");
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(formatInvoiceId(item));
                        getStyleClass().add("vi-invoice-id");
                    }
                }
            });
        }
        if (colClient != null) {
            colClient.setCellValueFactory(p -> {
                InvoiceMaster inv = p.getValue();
                return new SimpleStringProperty(inv != null ? inv.getClientName() : "");
            });
            colClient.setCellFactory(c -> new TableCell<InvoiceMaster, String>() {
                private final Label nameLabel = new Label();
                private final Label emailLabel = new Label();
                private final VBox box = new VBox(2, nameLabel, emailLabel);

                {
                    VBox.setVgrow(nameLabel, Priority.NEVER);
                    nameLabel.getStyleClass().add("vi-client-name");
                    emailLabel.getStyleClass().add("vi-client-email");
                    box.setAlignment(Pos.CENTER_LEFT);
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        InvoiceMaster inv = getTableRow() != null ? getTableRow().getItem() : null;
                        if (inv == null) {
                            setGraphic(null);
                            return;
                        }
                        nameLabel.setText(inv.getClientName() != null ? inv.getClientName() : "—");
                        int cid = inv.getClientId();
                        emailLabel.setText(clientIdToEmail.getOrDefault(cid, "—"));
                        setAlignment(Pos.CENTER_LEFT);
                        setGraphic(box);
                    }
                }
            });
        }
        if (colIssueDate != null) {
            colIssueDate.setCellValueFactory(p -> {
                LocalDate d = p.getValue() != null ? p.getValue().getInvoiceDate() : null;
                return new SimpleStringProperty(d == null ? "" : d.format(ISSUE_DUE_FORMAT));
            });
            colIssueDate.setCellFactory(c -> new TableCell<>() {
                @Override
                protected void updateItem(String t, boolean empty) {
                    super.updateItem(t, empty);
                    getStyleClass().remove("vi-date");
                    if (empty || t == null || t.isEmpty()) {
                        setText(null);
                    } else {
                        setText(t);
                        getStyleClass().add("vi-date");
                    }
                    setAlignment(Pos.CENTER_LEFT);
                }
            });
        }
        if (colDueDate != null) {
            colDueDate.setCellValueFactory(p -> {
                LocalDate d = p.getValue() != null ? effectiveDueDate(p.getValue()) : null;
                return new SimpleStringProperty(d == null ? "" : d.format(ISSUE_DUE_FORMAT));
            });
            colDueDate.setCellFactory(c -> new TableCell<>() {
                @Override
                protected void updateItem(String t, boolean empty) {
                    super.updateItem(t, empty);
                    getStyleClass().removeAll("vi-date", "vi-date-overdue");
                    if (empty || t == null || t.isEmpty()) {
                        setText(null);
                    } else {
                        setText(t);
                        InvoiceMaster inv = getTableRow() != null ? getTableRow().getItem() : null;
                        if (inv != null && isDueLineOverdue(inv)) {
                            getStyleClass().add("vi-date-overdue");
                        } else {
                            getStyleClass().add("vi-date");
                        }
                    }
                    setAlignment(Pos.CENTER_LEFT);
                }
            });
        }
        if (colAmount != null) {
            colAmount.setCellValueFactory(p -> {
                InvoiceMaster inv = p.getValue();
                double n = inv != null ? inv.getNetAmount() : 0;
                return new ReadOnlyObjectWrapper<>(n);
            });
            colAmount.setCellFactory(c -> new TableCell<InvoiceMaster, Double>() {
                @Override
                protected void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("vi-amount");
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(fmtRupee(item));
                        getStyleClass().add("vi-amount");
                    }
                    setAlignment(Pos.CENTER_RIGHT);
                }
            });
        }
        if (colStatus != null) {
            colStatus.setCellValueFactory(p -> new SimpleStringProperty(
                    p.getValue() == null ? "" : refPillKey(p.getValue())));
            colStatus.setCellFactory(col -> new RefStatusCell());
        }
        if (colActions != null) {
            colActions.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setAlignment(Pos.CENTER);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        InvoiceMaster inv = getTableRow() != null ? getTableRow().getItem() : null;
                        if (inv == null) {
                            setGraphic(null);
                        } else {
                            setGraphic(buildRowActionsMenuButton(inv));
                        }
                    }
                }
            });
        }
    }

    private Node createIcon(String pathStr) {
        SVGPath svg = new SVGPath();
        svg.setContent(pathStr);
        svg.setFill(Color.WHITE);
        svg.setScaleX(0.7); svg.setScaleY(0.7);
        StackPane pane = new StackPane(svg);
        pane.setPrefSize(16, 16);
        return pane;
    }

    private static String fmtRupee(double v) {
        return String.format("₹ %,.2f", v);
    }

    private static String formatInvoiceId(String no) {
        String s = DocumentNumbering.stripLeadingHash(no);
        return s != null ? s : "";
    }

    private static LocalDate effectiveDueDate(InvoiceMaster inv) {
        if (inv.getPeriodTo() != null) {
            return inv.getPeriodTo();
        }
        if (inv.getInvoiceDate() == null) {
            return null;
        }
        return inv.getInvoiceDate().plusDays(30);
    }

    /** True when DB summaries show at least one credit or debit note (drives net amount link). */
    private static boolean invoiceHasCnOrDn(InvoiceMaster inv) {
        return inv != null && (inv.getCnCount() > 0 || inv.getDnCount() > 0);
    }

    private static boolean isDueLineOverdue(InvoiceMaster inv) {
        if (inv == null) {
            return false;
        }
        String pay = inv.getPaymentStatus() != null ? inv.getPaymentStatus().toUpperCase() : "";
        if ("OVERDUE".equals(pay)) {
            return true;
        }
        LocalDate due = effectiveDueDate(inv);
        if (due == null) {
            return false;
        }
        return due.isBefore(LocalDate.now()) && inv.getDueAmount() > 0.01;
    }

    /** One of: PAID, OVERDUE, PARTIAL, PENDING — matches reference pill set */
    private static String refPillKey(InvoiceMaster inv) {
        if (inv == null) {
            return "PENDING";
        }
        if (inv.getStatus() != null && "CANCELLED".equalsIgnoreCase(inv.getStatus())) {
            return "PENDING";
        }
        String pay = inv.getPaymentStatus() != null
                ? inv.getPaymentStatus().toUpperCase().replace(' ', '_')
                : "";
        if ("PAID".equals(pay)) {
            return "PAID";
        }
        if ("PARTIAL_PAID".equals(pay) || "PARTIAL".equals(pay)) {
            return "PARTIAL";
        }
        if ("OVERDUE".equals(pay) || (isDueLineOverdue(inv) && inv.getDueAmount() > 0.01)) {
            return "OVERDUE";
        }
        return "PENDING";
    }

    private void applyRefPillStyle(Label pill, Region dot, String key) {
        if (pill == null) {
            return;
        }
        String k = key != null ? key : "PENDING";
        String slug = switch (k) {
            case "PAID" -> "paid";
            case "OVERDUE" -> "overdue";
            case "PARTIAL" -> "partial";
            default -> "pending";
        };
        pill.getStyleClass().setAll("ref-pill", "ref-pill--" + slug);
        if (dot != null) {
            dot.getStyleClass().setAll("ref-pill-status-dot", "ref-pill-status-dot--" + slug);
        }
    }

    private class RefStatusCell extends TableCell<InvoiceMaster, String> {
        private final HBox pillRow = new HBox(6);
        private final Region statusDot = new Region();
        private final Label pill = new Label();

        {
            pillRow.setAlignment(Pos.CENTER);
            statusDot.setMinSize(6, 6);
            statusDot.setMaxSize(6, 6);
            statusDot.getStyleClass().add("ref-pill-status-dot");
            pillRow.getChildren().addAll(statusDot, pill);
        }

        @Override
        protected void updateItem(String key, boolean empty) {
            super.updateItem(key, empty);
            if (empty) {
                setGraphic(null);
                setText(null);
                return;
            }
            InvoiceMaster inv = getTableRow() != null ? getTableRow().getItem() : null;
            String k = inv != null ? refPillKey(inv) : (key != null ? key : "PENDING");
            String label;
            if ("PAID".equals(k)) {
                label = "PAID";
            } else if ("OVERDUE".equals(k)) {
                label = "OVERDUE";
            } else if ("PARTIAL".equals(k)) {
                label = "PARTIAL";
            } else {
                label = "PENDING";
            }
            pill.setText(label);
            applyRefPillStyle(pill, statusDot, k);
            setAlignment(Pos.CENTER);
            setGraphic(pillRow);
            setText(null);
        }
    }

    /**
     * Which invoice actions are allowed for a row. Mirrors prior enable/disable rules on toolbar buttons.
     */
    private static class InvoiceActionState {
        final boolean edit;
        final boolean finalize;
        final boolean send;
        final boolean revised;
        final boolean payment;
        final boolean raiseCnDn;
        final boolean cancel;
        final String sendText;
        final String cancelText;

        private InvoiceActionState(boolean edit, boolean finalize, boolean send, boolean revised,
                boolean payment, boolean raiseCnDn, boolean cancel, String sendText, String cancelText) {
            this.edit = edit;
            this.finalize = finalize;
            this.send = send;
            this.revised = revised;
            this.payment = payment;
            this.raiseCnDn = raiseCnDn;
            this.cancel = cancel;
            this.sendText = sendText;
            this.cancelText = cancelText;
        }

        static InvoiceActionState from(InvoiceMaster inv) {
            if (inv == null) {
                return new InvoiceActionState(
                        false, false, false, false, false, false, false, "Send", "Cancel");
            }
        String status = inv.getStatus() != null ? inv.getStatus().toUpperCase() : "";
        String pStatus = inv.getPaymentStatus() != null ? inv.getPaymentStatus().toUpperCase() : "";
        boolean isDraft = "DRAFT".equals(status);
        boolean isFinal = "FINAL".equals(status);
        boolean isSent = "SENT TO CLIENT".equals(status) || "SENT".equals(status);
        boolean isPaid = "PAID".equals(pStatus);
        boolean isPartialPaid = "PARTIAL PAID".equals(pStatus);
        boolean hasPayments = inv.getPaidAmount() > 0;
        
            boolean edit = "DRAFT".equals(status) || "FINAL".equals(status);
            boolean finalize = isDraft;
            boolean send = isFinal || isSent;
            String sendText = isSent ? "Send Again" : "Send";
            boolean revised = (isFinal || isSent) && !hasPayments;
            boolean payment = isSent && !isPaid;
            boolean raiseCnDn = isSent && !"UNPAID".equals(pStatus);
            boolean cancel = !(isPaid || isPartialPaid || "REVISED".equals(status) || "CANCELLED".equals(status));
            String invNo = inv.getInvoiceNo();
            boolean isTemp = isDraft || (invNo != null && invNo.startsWith("TEMP-"));
            String cancelText = isTemp ? "Delete" : "Cancel";
            return new InvoiceActionState(
                    edit, finalize, send, revised, payment, raiseCnDn, cancel, sendText, cancelText);
        }
    }

    private void updateButtonStates(InvoiceMaster inv) {
        applyToolbarFromState(InvoiceActionState.from(inv), inv);
    }

    /**
     * Toolbar when a row is selected and at least one action applies.
     * The bar always keeps a reserved vertical slot (invisible when idle) so showing buttons
     * does not push the table down under the cursor — that used to look like an "outside"
     * click and cleared selection while real outside-click deselect still works.
     */
    private void applyToolbarFromState(InvoiceActionState s, InvoiceMaster inv) {
        // Toolbar row should stay visible; actions are disabled when no selection.
        if (viToolbarRow != null) {
            viToolbarRow.setManaged(true);
            viToolbarRow.setVisible(true);
            viToolbarRow.setOpacity(1);
            viToolbarRow.setMouseTransparent(false);
            viToolbarRow.setMinHeight(Region.USE_COMPUTED_SIZE);
            viToolbarRow.setPrefHeight(Region.USE_COMPUTED_SIZE);
            viToolbarRow.setMaxHeight(Region.USE_COMPUTED_SIZE);
        }
        setToolbarButton(btnEdit, s.edit, null);
        setToolbarButton(btnFinalize, s.finalize, null);
        setToolbarButton(btnSend, s.send, s.sendText);
        setToolbarButton(btnRevised, s.revised, null);
        setToolbarButton(btnPayment, s.payment, null);
        setToolbarButton(btnRaiseCnDn, s.raiseCnDn, null);
        setToolbarButton(btnCancel, s.cancel, s.cancelText);
    }

    private void setToolbarButton(Button b, boolean show, String text) {
        if (b == null) {
            return;
        }
        // Buttons should remain visible; just enable/disable based on selection + state.
        b.setManaged(true);
        b.setVisible(true);
        b.setDisable(!show);
        if (text != null) {
            b.setText(text);
        }
    }

    private void runWithSelection(InvoiceMaster inv, Runnable then) {
        if (inv == null || invoiceTable == null) {
            return;
        }
        invoiceTable.getSelectionModel().select(inv);
        if (then != null) {
            then.run();
        }
    }

    private void handleEditFromRow(InvoiceMaster inv) {
        runWithSelection(inv, () -> handleEditAction(null));
    }

    private void handleFinalizeFromRow(InvoiceMaster inv) {
        runWithSelection(inv, () -> handleFinalizeAction(null));
    }

    private void handleSendFromRow(InvoiceMaster inv) {
        runWithSelection(inv, () -> handleSendAction(null));
    }

    private void handleRevisedFromRow(InvoiceMaster inv) {
        runWithSelection(inv, () -> handleRevisedAction(null));
    }

    private void handlePaymentFromRow(InvoiceMaster inv) {
        runWithSelection(inv, () -> handlePaymentAction(null));
    }

    private void handleRaiseCnDnFromRow(InvoiceMaster inv) {
        runWithSelection(inv, () -> handleRaiseCnDnAction(null));
    }

    private void handleCancelFromRow(InvoiceMaster inv) {
        runWithSelection(inv, () -> handleCancelAction(null));
    }

    private void handleDownloadInvoiceFromRow(InvoiceMaster inv) {
        if (inv == null || inv.getId() <= 0) {
            return;
        }
        runWithSelection(inv, null);
        Stage stage = invoiceTable != null && invoiceTable.getScene() != null
                && invoiceTable.getScene().getWindow() instanceof Stage
                ? (Stage) invoiceTable.getScene().getWindow() : null;
        try {
            InvoiceBuilderService builder = new InvoiceBuilderService();
            Invoice full = builder.buildInvoiceFromMasterForPdfExport(inv.getId());
            File created = new PdfInvoiceService().generateSingleInvoicePDF(full);
            if (stage != null) {
                Toast.showSmall(stage, "Invoice PDF saved");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Could not save PDF: " + ex.getMessage(), ButtonType.OK)
                    .showAndWait();
        }
    }

    /**
     * ContextMenu popups are not child nodes of the FXML node; their CSS must be on
     * the window {@link Scene} so .vi-invoice-actions-menu rules apply.
     */
    private void ensureViewInvoicesMenuCss(Scene scene) {
        if (scene == null) {
            return;
        }
        URL u = getClass().getResource("/css/view_invoices.css");
        if (u == null) {
            return;
        }
        String ext = u.toExternalForm();
        if (!scene.getStylesheets().contains(ext)) {
            scene.getStylesheets().add(ext);
        }
    }

    private ContextMenu buildActionsOverflowMenu(InvoiceMaster inv) {
        ContextMenu m = new ContextMenu();
        m.getStyleClass().add("vi-invoice-actions-menu");
        InvoiceActionState s = InvoiceActionState.from(inv);

        MenuItem view = new MenuItem("View details");
        view.setOnAction(e -> handleViewOnlyAction(inv));
        m.getItems().add(view);

        MenuItem downloadInv = new MenuItem("Download Invoice");
        downloadInv.setOnAction(e -> handleDownloadInvoiceFromRow(inv));
        m.getItems().add(downloadInv);
        
        boolean addedSeparator = false;

        if (s.edit) {
            if (!addedSeparator) { m.getItems().add(new SeparatorMenuItem()); addedSeparator = true; }
            MenuItem edit = new MenuItem("Edit");
            edit.setOnAction(e -> handleEditFromRow(inv));
            m.getItems().add(edit);
        }

        if (s.finalize) {
            if (!addedSeparator) { m.getItems().add(new SeparatorMenuItem()); addedSeparator = true; }
            MenuItem fin = new MenuItem("Finalize");
            fin.setOnAction(e -> handleFinalizeFromRow(inv));
            m.getItems().add(fin);
        }

        if (s.send) {
            if (!addedSeparator) { m.getItems().add(new SeparatorMenuItem()); addedSeparator = true; }
            MenuItem send = new MenuItem(s.sendText);
            send.setOnAction(e -> handleSendFromRow(inv));
            m.getItems().add(send);
        }

        if (s.revised) {
            if (!addedSeparator) { m.getItems().add(new SeparatorMenuItem()); addedSeparator = true; }
            MenuItem rev = new MenuItem("Revised");
            rev.setOnAction(e -> handleRevisedFromRow(inv));
            m.getItems().add(rev);
        }

        if (s.payment) {
            if (!addedSeparator) { m.getItems().add(new SeparatorMenuItem()); addedSeparator = true; }
            MenuItem pay = new MenuItem("Payment");
            pay.setOnAction(e -> handlePaymentFromRow(inv));
            m.getItems().add(pay);
        }

        if (s.raiseCnDn) {
            if (!addedSeparator) { m.getItems().add(new SeparatorMenuItem()); addedSeparator = true; }
            MenuItem cndn = new MenuItem("Raise CN/DN");
            cndn.setOnAction(e -> handleRaiseCnDnFromRow(inv));
            m.getItems().add(cndn);
        }

        if (s.cancel) {
            if (!addedSeparator) { m.getItems().add(new SeparatorMenuItem()); addedSeparator = true; }
            String cancelLine = s.cancelText != null && "Delete".equals(s.cancelText) ? "Delete" : "Cancel invoice";
            MenuItem cxl = new MenuItem(cancelLine);
            cxl.setOnAction(e -> handleCancelFromRow(inv));
            m.getItems().add(cxl);
        }

        return m;
    }

    /** Only the ⋯ control is shown; all actions (Edit, Finalize, etc.) open from the menu. */
    private Button buildRowActionsMenuButton(InvoiceMaster inv) {
        Button btn = new Button("⋯");
        btn.getStyleClass().add("vi-ellipsis-btn");
        btn.setFocusTraversable(false);
        btn.setMnemonicParsing(false);
        btn.setTooltip(new Tooltip("Invoice actions"));
        btn.setOnAction(e -> {
            ensureViewInvoicesMenuCss(btn.getScene());
            ContextMenu menu = buildActionsOverflowMenu(inv);
            menu.show(btn, javafx.geometry.Side.BOTTOM, 0, 0);
        });
        return btn;
    }

    /** Invoked from live filter listeners and programmatic refresh; not an FXML button. */
    private void handleSearch(ActionEvent event) {
        final Integer clientId = (clientComboBox != null && clientComboBox.getValue() != null
                && clientComboBox.getValue().getId() != ALL_CLIENTS_ID)
                ? clientComboBox.getValue().getId()
                : null;
        String status = (statusComboBox != null) ? statusComboBox.getValue() : "All";
        LocalDate start = (startDatePicker != null) ? startDatePicker.getValue() : null;
        LocalDate end = (endDatePicker != null) ? endDatePicker.getValue() : null;
        String invoiceNo = (invoiceSearchField != null) ? invoiceSearchField.getText() : "";

        InvoiceMaster selected = (invoiceTable != null) ? invoiceTable.getSelectionModel().getSelectedItem() : null;
        final Integer selectedId = (selected != null) ? selected.getId() : null;

        new Thread(() -> {
            try {
                List<InvoiceMaster> results = invoiceMasterService.getFilteredInvoices(clientId, status, start, end, invoiceNo);
                Platform.runLater(() -> {
                    fullInvoiceResults.clear();
                    fullInvoiceResults.addAll(results);
                    selectedInvoiceIds.clear();
                    currentPageIndex = 0;
                    repaginate();

                    if (invoiceTable != null) {
                        if (selectedId != null) {
                            int idx = -1;
                            for (int i = 0; i < fullInvoiceResults.size(); i++) {
                                if (fullInvoiceResults.get(i).getId() == selectedId) {
                                    idx = i;
                                break;
                            }
                        }
                            if (idx >= 0) {
                                currentPageIndex = idx / pageSize;
                                repaginate();
                                InvoiceMaster sel = fullInvoiceResults.get(idx);
                                invoiceTable.getSelectionModel().select(sel);
                                invoiceTable.requestFocus();
                            } else {
                                invoiceTable.getSelectionModel().clearSelection();
                            }
                        } else {
                            invoiceTable.getSelectionModel().clearSelection();
                        }
                    }
                    refreshInvoiceSummaryPanel();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void repaginate() {
        if (paginationInfoLabel == null) {
            tablePageItems.clear();
            int total = fullInvoiceResults.size();
            int from = currentPageIndex * pageSize;
            int to = Math.min(from + pageSize, total);
            if (from < to) {
                tablePageItems.addAll(fullInvoiceResults.subList(from, to));
            }
            return;
        }
        int total = fullInvoiceResults.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        if (currentPageIndex >= pages) {
            currentPageIndex = Math.max(0, pages - 1);
        }
        int from = currentPageIndex * pageSize;
        int to = Math.min(from + pageSize, total);
        tablePageItems.clear();
        if (from < to) {
            tablePageItems.addAll(fullInvoiceResults.subList(from, to));
        }
        int fromDisplay = total == 0 ? 0 : from + 1;
        paginationInfoLabel.setText(String.format("Showing %d to %d of %d invoices", fromDisplay, to, total));
        rebuildPaginationControls(pages);
    }

    private void pageChange(int newIndex) {
        int total = fullInvoiceResults.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        if (newIndex < 0 || newIndex >= pages) {
            return;
        }
        currentPageIndex = newIndex;
        repaginate();
    }

    private void rebuildPaginationControls(int pages) {
        if (paginationPagesBox == null) return;
        paginationPagesBox.getChildren().clear();

        if (pages <= 1) return;

        Button prev = new Button("<");
        prev.getStyleClass().add("vi-page-btn");
        prev.setDisable(currentPageIndex <= 0);
        prev.setOnAction(e -> pageChange(currentPageIndex - 1));

        Button next = new Button(">");
        next.getStyleClass().add("vi-page-btn");
        next.setDisable(currentPageIndex >= pages - 1);
        next.setOnAction(e -> pageChange(currentPageIndex + 1));

        paginationPagesBox.getChildren().add(prev);

        // Logic to show a max of 3 pages (sliding window) - matching view_client.css style
        int start = Math.max(0, currentPageIndex - 1);
        int end = Math.min(pages, start + 3);
        if (end - start < 3 && start > 0) {
            start = Math.max(0, end - 3);
        }

        for (int p = start; p < end; p++) {
            final int pi = p;
            Button b = new Button(String.valueOf(p + 1));
            b.getStyleClass().add("vi-page-btn");
            if (p == currentPageIndex) {
                b.getStyleClass().add("vi-page-btn-active");
            }
            b.setOnAction(e -> pageChange(pi));
            paginationPagesBox.getChildren().add(b);
        }

        paginationPagesBox.getChildren().add(next);

        // Jump to page label
        Label jumpLabel = new Label("Go to:");
        jumpLabel.setStyle("-fx-text-fill: #A79F99; -fx-font-size: 11px; -fx-padding: 0 0 0 10;");
        paginationPagesBox.getChildren().add(jumpLabel);

        if (goToPageField != null) {
            goToPageField.getStyleClass().add("goto-field");
            if (!paginationPagesBox.getChildren().contains(goToPageField)) {
                paginationPagesBox.getChildren().add(goToPageField);
            }
        }
    }

    @FXML
    private void handleGoToPage() {
        if (goToPageField == null || goToPageField.getText().isEmpty()) return;
        try {
            int target = Integer.parseInt(goToPageField.getText().trim());
            int total = fullInvoiceResults.size();
            int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));

            if (target >= 1 && target <= pages) {
                currentPageIndex = target - 1;
                repaginate();
                goToPageField.clear();
            } else {
                toast("Invalid page number ❌");
            }
        } catch (NumberFormatException e) {
            toast("Please enter a valid number ❌");
        }
    }

    private void toast(String msg) {
        var w = viScreenRoot.getScene() != null ? viScreenRoot.getScene().getWindow() : null;
        if (w instanceof javafx.stage.Stage stage) {
            utils.Toast.show(stage, msg);
        }
    }

    /** Selected table row if it still exists in {@link #fullInvoiceResults}; otherwise null. */
    private InvoiceMaster currentSummaryInvoiceOrNull() {
        InvoiceMaster sel = invoiceTable != null ? invoiceTable.getSelectionModel().getSelectedItem() : null;
        if (sel == null) {
            return null;
        }
        for (InvoiceMaster inv : fullInvoiceResults) {
            if (inv.getId() == sel.getId()) {
                return sel;
            }
        }
        return null;
    }

    private void refreshInvoiceSummaryPanel() {
        InvoiceMaster sel = currentSummaryInvoiceOrNull();
        updateSummaryContextLabel(sel);
        updateSummaryBreakdown(sel);
        updateSidebarTotals(sel);
        if (viStatusOverviewBox != null) {
            boolean showStatusOverview = (sel == null);
            viStatusOverviewBox.setVisible(showStatusOverview);
            viStatusOverviewBox.setManaged(showStatusOverview);
        }
        if (sel == null) {
            updatePieChartData(null);
        }
    }

    /**
     * Inline “Adjustments and payments” block stays hidden; use Total Adjustment in the list
     * (when it is a link) to open the dialog instead.
     */
    private void updateSummaryBreakdown(InvoiceMaster selectedInFilter) {
        if (summaryBreakdownBox == null || summarySingleInvoiceSection == null) {
            return;
        }
        summaryBreakdownBox.getChildren().clear();
        summarySingleInvoiceSection.setManaged(false);
        summarySingleInvoiceSection.setVisible(false);
    }

    private void updateSummaryContextLabel(InvoiceMaster selectedInFilter) {
        if (summaryContextLabel == null) {
            return;
        }
        if (selectedInFilter != null) {
            String client = selectedInFilter.getClientName() != null ? selectedInFilter.getClientName() : "";
            String line2 = client.isEmpty() ? "Selected invoice" : client;
            summaryContextLabel.setText(formatInvoiceId(selectedInFilter.getInvoiceNo()) + "\n" + line2);
        } else {
            int n = fullInvoiceResults.size();
            summaryContextLabel.setText(
                    "Date range: " + formatFilterDateRangeLine() + "\n" + n + " invoice(s) in current filter");
        }
    }

    private String formatFilterDateRangeLine() {
        LocalDate s = startDatePicker != null ? startDatePicker.getValue() : null;
        LocalDate e = endDatePicker != null ? endDatePicker.getValue() : null;
        if (s == null && e == null) {
            return "All dates";
        }
        if (s != null && e != null) {
            return s.format(ISSUE_DUE_FORMAT) + " – " + e.format(ISSUE_DUE_FORMAT);
        }
        if (s != null) {
            return "From " + s.format(ISSUE_DUE_FORMAT);
        }
        return "Until " + e.format(ISSUE_DUE_FORMAT);
    }

    /**
     * @param selectedInFilter null → totals for all rows in current filter (incl. date range);
     *                         non-null → figures for that invoice only.
     */
    private void updateSidebarTotals(InvoiceMaster selectedInFilter) {
        Iterable<InvoiceMaster> rows =
                selectedInFilter != null ? Collections.singletonList(selectedInFilter) : fullInvoiceResults;
        long n = selectedInFilter != null ? 1L : fullInvoiceResults.size();
        double base = 0;
        double adj = 0;
        double net = 0;
        double paid = 0;
        double due = 0;
        for (InvoiceMaster inv : rows) {
            base += inv.getAmount();
            double cn = inv.getCnAmount() != null ? inv.getCnAmount() : 0;
            double dn = inv.getDnAmount() != null ? inv.getDnAmount() : 0;
            adj += dn - cn;
            net += inv.getNetAmount();
            paid += inv.getPaidAmount();
            due += inv.getDueAmount();
        }
        if (sumCountLabel != null) {
            sumCountLabel.setText(String.valueOf(n));
        }
        if (sumBaseLabel != null) {
            sumBaseLabel.setText(fmtRupee(base));
        }
        if (sumAdjustmentLabel != null) {
            sumAdjustmentLabel.setText(fmtRupee(adj));
            sumAdjustmentLabel.getStyleClass().remove("vi-summary-link");
            sumAdjustmentLabel.setOnMouseClicked(null);
            sumAdjustmentLabel.setTooltip(null);

            if (selectedInFilter != null && invoiceHasCnOrDn(selectedInFilter)) {
                final InvoiceMaster openFor = selectedInFilter;
                sumAdjustmentLabel.getStyleClass().add("vi-summary-link");
                sumAdjustmentLabel.setTooltip(new Tooltip("Credit / debit notes and payments"));
                sumAdjustmentLabel.setOnMouseClicked(e -> {
                    e.consume();
                    showAdjustmentsAndPaymentsDialog(openFor);
                });
            }
        }
        if (sumNetLabel != null) {
            sumNetLabel.setText(fmtRupee(net));
        }
        if (sumPaidLabel != null) {
            sumPaidLabel.setText(fmtRupee(paid));
        }
        if (sumDueLabel != null) {
            sumDueLabel.setText(fmtRupee(due));
        }
    }

    private void updatePieChartData(InvoiceMaster selectedInFilter) {
        if (viDonutArcPaid == null || viDonutArcUnpaid == null || viDonutArcOverdue == null) {
            return;
        }
        Iterable<InvoiceMaster> rows =
                selectedInFilter != null ? Collections.singletonList(selectedInFilter) : fullInvoiceResults;
        int paid = 0;
        int unpaid = 0;
        int overdue = 0;
        for (InvoiceMaster inv : rows) {
            String ps = inv.getPaymentStatus() != null ? inv.getPaymentStatus().toUpperCase() : "";
            if ("PAID".equals(ps)) {
                paid++;
            } else if ("OVERDUE".equals(ps)) {
                overdue++;
            } else {
                unpaid++;
            }
        }
        int total = paid + unpaid + overdue;
        if (viDonutCenterCount != null) {
            viDonutCenterCount.setText(String.valueOf(total));
        }
        if (viDonutLegendPaid != null) {
            viDonutLegendPaid.setText(String.valueOf(paid));
        }
        if (viDonutLegendUnpaid != null) {
            viDonutLegendUnpaid.setText(String.valueOf(unpaid));
        }
        if (viDonutLegendOverdue != null) {
            viDonutLegendOverdue.setText(String.valueOf(overdue));
        }
        if (total <= 0) {
            viDonutArcPaid.setLength(0);
            viDonutArcUnpaid.setLength(0);
            viDonutArcOverdue.setLength(0);
            viDonutArcPaid.setStartAngle(90);
            viDonutArcUnpaid.setStartAngle(90);
            viDonutArcOverdue.setStartAngle(90);
            return;
        }
        /* Same sweep convention as MainController payment donut: start at 90°, negative length. */
        double cursor = 90;
        double fPaid = paid / (double) total;
        double fUnpaid = unpaid / (double) total;
        double fOverdue = overdue / (double) total;
        viDonutArcPaid.setStartAngle(cursor);
        viDonutArcPaid.setLength(-(fPaid * 360));
        cursor += viDonutArcPaid.getLength();
        viDonutArcUnpaid.setStartAngle(cursor);
        viDonutArcUnpaid.setLength(-(fUnpaid * 360));
        cursor += viDonutArcUnpaid.getLength();
        viDonutArcOverdue.setStartAngle(cursor);
        viDonutArcOverdue.setLength(-(fOverdue * 360));
    }

    @FXML
    private void handleClear(ActionEvent event) {
        if (clientComboBox != null) {
            clientComboBox.getSelectionModel().selectFirst();
        }
        if (statusComboBox != null) {
            statusComboBox.getSelectionModel().selectFirst();
        }
        if (startDatePicker != null) {
            startDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        }
        if (endDatePicker != null) {
            endDatePicker.setValue(LocalDate.now());
        }
        if (invoiceSearchField != null) {
            invoiceSearchField.clear();
        }
        setQuickFilterActive(quickAllBtn);
        handleSearch(null);
    }

    @FXML private void handleFinalizeAction(ActionEvent e) {
        InvoiceMaster inv = (invoiceTable != null) ? invoiceTable.getSelectionModel().getSelectedItem() : null;
        if (inv != null) {
            try {
                String newNo = invoiceMasterService.finalizeInvoice(inv.getId());
                handleSearch(null); // Refresh table
                Toast.show((Stage) invoiceTable.getScene().getWindow(), "✅ Invoice Finalized as: " + newNo);
            } catch (Exception ex) {
                ex.printStackTrace();
                Toast.show((Stage) invoiceTable.getScene().getWindow(), "❌ Failed to finalize: " + ex.getMessage());
            }
        }
    }
    @FXML private void handleSendAction(ActionEvent e) { updateStatus("SENT TO CLIENT"); }
    @FXML private void handleCancelAction(ActionEvent e) { updateStatus("CANCELLED"); }

    private void updateStatus(String status) {
        InvoiceMaster inv = (invoiceTable != null) ? invoiceTable.getSelectionModel().getSelectedItem() : null;
        if (inv != null) {
            invoiceMasterService.updateInvoiceStatus(inv.getId(), status);
            handleSearch(null);
        }
    }

    @FXML private void handleRevisedAction(ActionEvent event) {
        InvoiceMaster inv = (invoiceTable != null) ? invoiceTable.getSelectionModel().getSelectedItem() : null;
        if (inv == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Create revision?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                try {
                    invoiceMasterService.reviseInvoice(inv);
                    handleSearch(null);
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    @FXML private void handlePaymentAction(ActionEvent event) {
        InvoiceMaster inv = (invoiceTable != null) ? invoiceTable.getSelectionModel().getSelectedItem() : null;
        if (inv != null) {
            RecordPaymentController.pendingPrefillInvoice = inv;
            MainController.getInstance().loadRecordPayment();
        }
    }

    @FXML private void handleEditAction(ActionEvent event) {
        InvoiceMaster inv = (invoiceTable != null) ? invoiceTable.getSelectionModel().getSelectedItem() : null;
        if (inv != null) {
            String stat = inv.getStatus() != null ? inv.getStatus().toUpperCase() : "";
            ViewInvoiceJobsController.pendingPrefillInvoice = inv;
            // 🔥 Requirement: FINAL status opens in viewOnly mode via Edit button
            ViewInvoiceJobsController.viewOnlyMode = "FINAL".equals(stat);
            MainController.getInstance().loadViewInvoiceJobs();
        }
    }

    private void handleViewOnlyAction(InvoiceMaster inv) {
        if (inv != null) {
            ViewInvoiceJobsController.pendingPrefillInvoice = inv;
            ViewInvoiceJobsController.viewOnlyMode = true;
            MainController.getInstance().loadViewInvoiceJobs();
        }
    }

    @FXML private void handleRaiseCnDnAction(ActionEvent event) {
        InvoiceMaster inv = (invoiceTable != null) ? invoiceTable.getSelectionModel().getSelectedItem() : null;
        if (inv != null) {
            CreditDebitNoteController.pendingPrefillInvoice = inv;
            MainController.getInstance().loadCreditDebitNote();
        }
    }

    private void loadClients() {
        if (clientComboBox == null) {
            return;
        }
        List<Client> all = clientService.getAllClients();
        Client sentinel = new Client();
        sentinel.setId(ALL_CLIENTS_ID);
        sentinel.businessNameProperty().set("All Clients");
        sentinel.clientNameProperty().set("");
        ObservableList<Client> items = FXCollections.observableArrayList(sentinel);
        items.addAll(all);
        clientComboBox.setItems(items);
        clientComboBox.getSelectionModel().selectFirst();
        rebuildClientEmailCache();
    }

    private void rebuildClientEmailCache() {
        clientIdToEmail.clear();
        for (Client c : clientService.getAllClients()) {
            String em = c.getEmail();
            clientIdToEmail.put(c.getId(), (em != null && !em.isBlank()) ? em.trim() : "—");
        }
    }
    private void loadStatuses() {
        if (statusComboBox != null) {
            statusComboBox.getItems().setAll("All", "UNPAID", "PARTIAL PAID", "PAID", "OVERDUE", "CANCELLED");
            statusComboBox.getSelectionModel().selectFirst();
        }
    }

    private void setupAutoPopupDatePicker(DatePicker dp) {
        if (dp == null) return;
        dp.setEditable(false);
        dp.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> { if (!dp.isShowing()) dp.show(); });
    }

    /** Credit/debit note lines + payment allocations — only when invoice has CN/DN (opened from Total Adjustment). */
    private void showAdjustmentsAndPaymentsDialog(InvoiceMaster inv) {
        if (inv == null || !invoiceHasCnOrDn(inv)) {
            return;
        }
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Adjustments & payments · " + formatInvoiceId(inv.getInvoiceNo()));
        dialog.getDialogPane().getStylesheets().addAll(
            getClass().getResource("/css/theme.css").toExternalForm(),
            getClass().getResource("/css/view_invoices.css").toExternalForm()
        );
        dialog.getDialogPane().getStyleClass().add("vi-premium-dialog");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        if (invoiceTable != null && invoiceTable.getScene() != null && invoiceTable.getScene().getWindow() != null) {
            dialog.initOwner(invoiceTable.getScene().getWindow());
        }

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("vi-premium-tabs");
        Tab tabAdj = new Tab("Credit / debit notes");
        tabAdj.setClosable(false);
        tabAdj.setContent(wrapTableInGrow(buildAdjustmentsTableView(inv)));
        Tab tabPay = new Tab("Payments");
        tabPay.setClosable(false);
        tabPay.setContent(wrapTableInGrow(buildPaymentsTableView(inv)));
        tabs.getTabs().addAll(tabAdj, tabPay);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox root = new VBox(8, tabs);
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setPrefSize(620, 440);
        dialog.setResizable(true);
        dialog.showAndWait();
    }

    private static VBox wrapTableInGrow(TableView<?> table) {
        table.setMinHeight(220);
        table.setPrefHeight(280);
        VBox box = new VBox(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        box.setMinHeight(230);
        box.setPrefHeight(290);
        return box;
    }

    private TableView<InvoiceAdjustment> buildAdjustmentsTableView(InvoiceMaster inv) {
        TableView<InvoiceAdjustment> table = new TableView<>();
        table.getStyleClass().add("jobs-table-premium");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<InvoiceAdjustment, String> cType = new TableColumn<>("Type");
        cType.setCellValueFactory(p -> {
            InvoiceAdjustment a = p.getValue();
            String t = a != null && a.getType() != null ? a.getType() : "";
            return new SimpleStringProperty(t);
        });
        TableColumn<InvoiceAdjustment, String> cNo = new TableColumn<>("No");
        cNo.setCellValueFactory(p -> {
            InvoiceAdjustment a = p.getValue();
            String n = a != null && a.getNoteNo() != null ? a.getNoteNo() : "";
            return new SimpleStringProperty(n);
        });
        TableColumn<InvoiceAdjustment, String> cDate = new TableColumn<>("Date");
        cDate.setCellValueFactory(cd -> {
            InvoiceAdjustment a = cd.getValue();
            String t = a != null && a.getDate() != null ? a.getDate().format(ISSUE_DUE_FORMAT) : "—";
            return new SimpleStringProperty(t);
        });
        TableColumn<InvoiceAdjustment, Double> cAmt = new TableColumn<>("Amount");
        cAmt.setCellValueFactory(p -> {
            InvoiceAdjustment a = p.getValue();
            return new ReadOnlyObjectWrapper<>(a != null ? Double.valueOf(a.getAmount()) : null);
        });
        cAmt.setCellFactory(col -> new TableCell<InvoiceAdjustment, Double>() {
            private final Label label = new Label();

            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    InvoiceAdjustment adj = getTableRow() != null ? getTableRow().getItem() : null;
                    label.setText(fmtRupee(amount));
                    if (adj != null) {
                        String type = adj.getType() != null ? adj.getType() : "";
                        if ("Credit Note".equalsIgnoreCase(type)) {
                            label.setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                        } else if ("Debit Note".equalsIgnoreCase(type)) {
                            label.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                        } else {
                            label.setStyle("");
                        }
                    } else {
                        label.setStyle("");
                    }
                    setGraphic(label);
                    setText(null);
                }
            }
        });
        TableColumn<InvoiceAdjustment, String> cReason = new TableColumn<>("Reason");
        cReason.setCellValueFactory(p -> {
            InvoiceAdjustment a = p.getValue();
            String r = a != null && a.getReason() != null ? a.getReason() : "";
            return new SimpleStringProperty(r);
        });

        table.getColumns().addAll(cType, cNo, cDate, cAmt, cReason);
        List<InvoiceAdjustment> adjs = new ArrayList<>();
        try (java.sql.Connection con = utils.DBConnection.getConnection();
             java.sql.PreparedStatement ps = con.prepareStatement(
                     "SELECT id, type, note_no, amount, reason, date FROM invoice_adjustments "
                             + "WHERE invoice_id = ? ORDER BY id DESC")) {
            ps.setInt(1, inv.getId());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InvoiceAdjustment a = new InvoiceAdjustment();
                    a.setId(rs.getInt("id"));
                    a.setInvoiceId(inv.getId());
                    a.setType(rs.getString("type"));
                    a.setNoteNo(rs.getString("note_no"));
                    a.setAmount(rs.getDouble("amount"));
                    a.setReason(rs.getString("reason"));
                    /* SQLite stores date as TEXT; getDate() can throw and skip all rows */
                    String ds = rs.getString("date");
                    if (ds != null && !ds.isBlank()) {
                        try {
                            String norm = ds.trim();
                            if (norm.length() >= 10 && norm.charAt(4) == '-') {
                                a.setDate(LocalDate.parse(norm.substring(0, 10)));
                            } else if (norm.matches("\\d+")) {
                                long epoch = Long.parseLong(norm);
                                a.setDate(java.time.Instant.ofEpochMilli(epoch)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDate());
                            }
                        } catch (Exception ignored) {
                            /* leave date null */
                        }
                    }
                    adjs.add(a);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        table.setItems(FXCollections.observableArrayList(adjs));
        return table;
    }

    private TableView<PaymentRecord> buildPaymentsTableView(InvoiceMaster inv) {
        TableView<PaymentRecord> table = new TableView<>();
        table.getStyleClass().add("jobs-table-premium");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        
        TableColumn<PaymentRecord, String> cType = new TableColumn<>("Mode");
        cType.setCellValueFactory(new PropertyValueFactory<>("type"));
        
        TableColumn<PaymentRecord, String> cDate = new TableColumn<>("Date");
        cDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        
        TableColumn<PaymentRecord, Double> cAmt = new TableColumn<>("Allocated");
        cAmt.setCellValueFactory(new PropertyValueFactory<>("amount"));
        cAmt.setCellFactory(col -> new TableCell<PaymentRecord, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(fmtRupee(item));
                    if (item < 0) {
                        setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                    }
                }
            }
        });
        
        table.getColumns().addAll(cType, cDate, cAmt);
        List<PaymentRecord> records = new ArrayList<>();
        
        try (java.sql.Connection con = utils.DBConnection.getConnection();
             java.sql.PreparedStatement ps = con.prepareStatement(
                     "SELECT p.type, p.payment_date, pa.allocated_amount "
                             + "FROM payment_allocations pa "
                             + "JOIN payments p ON pa.payment_id = p.id "
                             + "WHERE pa.invoice_id = ? ORDER BY p.payment_date DESC")) {
            ps.setInt(1, inv.getId());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PaymentRecord r = new PaymentRecord();
                    r.setType(rs.getString(1));
                    r.setDate(rs.getString(2));
                    r.setAmount(rs.getDouble(3));
                    records.add(r);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        table.setItems(FXCollections.observableArrayList(records));
        return table;
    }

    public static class PaymentRecord {
        private String type;
        private String date;
        private double amount;
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
    }
}
