package controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import model.Client;
import model.InvoiceMaster;
import model.InvoiceAdjustment;
import service.ClientService;
import service.InvoiceMasterService;
import utils.Toast;

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
    @FXML private TableColumn<InvoiceMaster, String> colInvoiceNo;
    @FXML private TableColumn<InvoiceMaster, String> colClientName;
    @FXML private TableColumn<InvoiceMaster, String> colInvoiceDate;
    @FXML private TableColumn<InvoiceMaster, Double> colAmount;
    @FXML private TableColumn<InvoiceMaster, String> colAdjustment;
    @FXML private TableColumn<InvoiceMaster, Double> colNetAmount;
    @FXML private TableColumn<InvoiceMaster, Double> colNetPaid;
    @FXML private TableColumn<InvoiceMaster, Double> colDue;
    @FXML private TableColumn<InvoiceMaster, String> colStatus;
    @FXML private TableColumn<InvoiceMaster, Void> colActions;

    @FXML private Button btnEdit, btnFinalize, btnSend, btnPayment, btnCancel, btnRevised, btnRaiseCnDn;
    @FXML private Label paginationInfoLabel;
    @FXML private HBox paginationPagesBox;
    @FXML private ComboBox<Integer> pageSizeCombo;
    @FXML private PieChart statusPieChart;
    @FXML private Label sumCountLabel;
    @FXML private Label sumBaseLabel;
    @FXML private Label sumAdjustmentLabel;
    @FXML private Label sumNetLabel;
    @FXML private Label sumPaidLabel;
    @FXML private Label sumDueLabel;
    @FXML private ToggleButton quickAllBtn;
    @FXML private ToggleButton quickPaidBtn;
    @FXML private ToggleButton quickUnpaidBtn;
    @FXML private ToggleButton quickOverdueBtn;
    @FXML private HBox breadcrumbContainer;

    public static String pendingSearchInvoiceNo;
    private static final int ALL_CLIENTS_ID = 0;

    private final ClientService clientService = new ClientService();
    private final InvoiceMasterService invoiceMasterService = new InvoiceMasterService();
    /** Current page rows shown in the table (same object references as {@link #fullInvoiceResults}). */
    private final ObservableList<InvoiceMaster> tablePageItems = FXCollections.observableArrayList();
    private final List<InvoiceMaster> fullInvoiceResults = new ArrayList<>();
    private int currentPageIndex = 0;
    private int pageSize = 20;

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
            
            utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, "View Invoices", () -> handleBack(null));
            
            setupAutoPopupDatePicker(startDatePicker);
            setupAutoPopupDatePicker(endDatePicker);

            if (startDatePicker != null) {
                startDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
            }
            if (endDatePicker != null) {
                endDatePicker.setValue(LocalDate.now());
            }

            setupPaginationControls();
            setupStatusPieChart();
            setupQuickFilterSync();
            setupLiveFilters();

            invoiceTable.setItems(tablePageItems);

            invoiceTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
                updateButtonStates(newSel);
            });

            invoiceTable.setRowFactory(tv -> {
                TableRow<InvoiceMaster> row = new TableRow<>();
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

            Platform.runLater(() -> {
                try {
                    if (pendingSearchInvoiceNo != null) {
                        if (invoiceSearchField != null) invoiceSearchField.setText(pendingSearchInvoiceNo);
                        pendingSearchInvoiceNo = null;
                    }
                    handleSearch(null);
                } catch (Exception e) { e.printStackTrace(); }
            });
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR initializing ViewInvoicesController: " + e.getMessage());
            e.printStackTrace();
        }
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

    private void setupPaginationControls() {
        if (pageSizeCombo != null) {
            pageSizeCombo.setItems(FXCollections.observableArrayList(20, 50, 100));
            pageSizeCombo.setValue(20);
            pageSizeCombo.valueProperty().addListener((o, a, b) -> {
                if (b != null) {
                    pageSize = b;
                    currentPageIndex = 0;
                    repaginate();
                }
            });
        }
    }

    private void setupStatusPieChart() {
        if (statusPieChart != null) {
            statusPieChart.setTitle("");
            statusPieChart.setLabelsVisible(true);
            statusPieChart.setLegendVisible(true);
            statusPieChart.setClockwise(false);
            statusPieChart.setStartAngle(90);
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
        if (colInvoiceNo != null) colInvoiceNo.setCellValueFactory(new PropertyValueFactory<>("invoiceNo"));
        if (colClientName != null) colClientName.setCellValueFactory(new PropertyValueFactory<>("clientName"));
        if (colInvoiceDate != null) colInvoiceDate.setCellValueFactory(cellData -> {
            LocalDate date = cellData.getValue().getInvoiceDate();
            return new SimpleStringProperty(date != null ? date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) : "");
        });
        if (colAmount != null) {
            colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
            colAmount.setCellFactory(c -> rupeeMoneyCell(false));
        }
        if (colAdjustment != null) {
            colAdjustment.setCellValueFactory(new PropertyValueFactory<>("adjustment"));
            colAdjustment.setCellFactory(col -> new AdjustmentCell());
        }
        if (colNetAmount != null) {
            colNetAmount.setCellValueFactory(new PropertyValueFactory<>("netAmount"));
            colNetAmount.setCellFactory(c -> rupeeMoneyCell(false));
        }
        if (colNetPaid != null) {
            colNetPaid.setCellValueFactory(new PropertyValueFactory<>("paidAmount"));
            colNetPaid.setCellFactory(col -> new NetPaidCell());
        }
        if (colDue != null) {
            colDue.setCellValueFactory(new PropertyValueFactory<>("dueAmount"));
            colDue.setCellFactory(c -> rupeeMoneyCell(true));
        }

        if (colStatus != null) {
            colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
            colStatus.setCellFactory(col -> new StatusCell());
        }

        if (colActions != null) {
            colActions.setCellFactory(col -> new TableCell<>() {
                private final Button btn = new Button("...");
                {
                    btn.getStyleClass().add("vi-btn-actions");
                    btn.setOnAction(e -> {
                        InvoiceMaster inv = getTableRow().getItem();
                        if (inv != null) {
                            // Show a context menu or simple popup
                            ContextMenu menu = new ContextMenu();
                            MenuItem view = new MenuItem("View Details");
                            view.setOnAction(ae -> handleViewOnlyAction(inv));
                            menu.getItems().add(view);
                            menu.show(btn, javafx.geometry.Side.BOTTOM, 0, 0);
                        }
                    });
                }
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) setGraphic(null);
                    else setGraphic(btn);
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

    private class StatusCell extends TableCell<InvoiceMaster, String> {
        private final Label pill = new Label();

        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);
            if (empty || status == null) {
                setGraphic(null);
                setText(null);
            } else {
                pill.setText(status);
                applyStatusPillStyle(pill, status);
                setGraphic(pill);
                setText(null);
            }
        }
    }

    private static String fmtRupee(double v) {
        return String.format("₹ %,.2f", v);
    }

    private TableCell<InvoiceMaster, Double> rupeeMoneyCell(boolean dueColumn) {
        return new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("vi-money", "vi-due-unpaid");
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(fmtRupee(item));
                    getStyleClass().add("vi-money");
                    if (dueColumn && item > 0.009) {
                        getStyleClass().add("vi-due-unpaid");
                    }
                }
            }
        };
    }

    private void applyStatusPillStyle(Label pill, String status) {
        if (pill == null || status == null) return;
        pill.getStyleClass().add("status-pill");
        String u = status.toUpperCase().replace(' ', '_');
        String bg, fg;
        switch (u) {
            case "PAID": case "FINAL":
                bg = "#E8F5E9"; fg = "#2E7D32"; break;
            case "UNPAID": case "VOID": case "CANCELLED":
                bg = "#FFEBEE"; fg = "#C62828"; break;
            case "OVERDUE":
                bg = "#FFF3E0"; fg = "#EF6C00"; break;
            case "PARTIAL_PAID":
                bg = "#E3F2FD"; fg = "#1565C0"; break;
            case "SENT": case "SENT_TO_CLIENT":
                bg = "#E0F2FE"; fg = "#0369A1"; break;
            case "DRAFT": default:
                bg = "#F3E5F5"; fg = "#7B1FA2"; break;
        }
        pill.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + ";");
    }

    private class AdjustmentCell extends TableCell<InvoiceMaster, String> {
        private final Label textLabel = new Label();
        private final SVGPath eyeIcon = new SVGPath();
        private final HBox box = new HBox(6, textLabel, eyeIcon);

        public AdjustmentCell() {
            box.setAlignment(Pos.CENTER);
            textLabel.setAlignment(Pos.CENTER);
            eyeIcon.setContent("M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z");
            eyeIcon.setScaleX(0.85);
            eyeIcon.setScaleY(0.85);
            box.setStyle("-fx-cursor: hand;");
            
            box.setOnMouseClicked(e -> {
                InvoiceMaster inv = (getTableRow() != null) ? getTableRow().getItem() : null;
                if (inv != null && !"-".equals(inv.getAdjustment())) {
                    showAdjustmentDetails(inv);
                }
                e.consume();
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                InvoiceMaster inv = (getTableRow() != null) ? getTableRow().getItem() : null;
                if (inv == null || item.equals("-")) {
                    textLabel.setText(item != null ? item : "-");
                    textLabel.setStyle("-fx-text-fill: #78716C;");
                    eyeIcon.setVisible(false);
                } else {
                    textLabel.setText(item);
                    textLabel.setStyle("-fx-text-fill: #3E312D; -fx-font-weight: 800;");
                    eyeIcon.setVisible(true);
                    eyeIcon.setFill(Color.web("#78716C"));
                }
                setGraphic(box);
            }
        }
    }

    private class NetPaidCell extends TableCell<InvoiceMaster, Double> {
        private final Label textLabel = new Label();
        private final SVGPath eyeIcon = new SVGPath();
        private final HBox box = new HBox(6, textLabel, eyeIcon);

        public NetPaidCell() {
            box.setAlignment(Pos.CENTER);
            textLabel.setAlignment(Pos.CENTER);
            eyeIcon.setContent("M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z");
            eyeIcon.setScaleX(0.85);
            eyeIcon.setScaleY(0.85);
            box.setStyle("-fx-cursor: hand;");
            
            box.setOnMouseClicked(e -> {
                InvoiceMaster inv = (getTableRow() != null) ? getTableRow().getItem() : null;
                if (inv != null && inv.getPaidAmount() != 0) {
                    showPaymentDetails(inv);
                }
                e.consume();
            });
        }

        @Override
        protected void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                InvoiceMaster inv = (getTableRow() != null) ? getTableRow().getItem() : null;
                textLabel.setText(fmtRupee(item));
                if (inv != null && inv.getPaidAmount() != 0) {
                    textLabel.setStyle("-fx-text-fill: #2F7D4A; -fx-font-weight: 800;");
                    eyeIcon.setVisible(true);
                    eyeIcon.setFill(Color.web("#57534E"));
                } else {
                    textLabel.setStyle("-fx-text-fill: #78716C;");
                    eyeIcon.setVisible(false);
                }
                setGraphic(box);
            }
        }
    }

    private void updateButtonStates(InvoiceMaster inv) {
        if (inv == null) { disableAllButtons(); return; }
        String status = inv.getStatus() != null ? inv.getStatus().toUpperCase() : "";
        String pStatus = inv.getPaymentStatus() != null ? inv.getPaymentStatus().toUpperCase() : "";
        boolean isDraft = "DRAFT".equals(status);
        boolean isFinal = "FINAL".equals(status);
        boolean isSent = "SENT TO CLIENT".equals(status) || "SENT".equals(status);
        boolean isPaid = "PAID".equals(pStatus);
        boolean isPartialPaid = "PARTIAL PAID".equals(pStatus);
        
        boolean hasPayments = inv.getPaidAmount() > 0;
        
        if (btnEdit != null) btnEdit.setDisable(!("DRAFT".equals(status) || "FINAL".equals(status)));
        if (btnFinalize != null) btnFinalize.setDisable(!isDraft);
        if (btnSend != null) {
            btnSend.setDisable(!(isFinal || isSent));
            btnSend.setText(isSent ? "Send Again" : "Send");
        }
        if (btnRevised != null) btnRevised.setDisable(!(isFinal || isSent) || hasPayments);
        if (btnPayment != null) btnPayment.setDisable(!isSent || isPaid);
        if (btnRaiseCnDn != null) btnRaiseCnDn.setDisable(!isSent || "UNPAID".equals(pStatus));
        if (btnCancel != null) {
            btnCancel.setDisable(isPaid || isPartialPaid || "REVISED".equals(status) || "CANCELLED".equals(status));
            String invNo = inv.getInvoiceNo();
            boolean isTemp = isDraft || (invNo != null && invNo.startsWith("TEMP-"));
            btnCancel.setText(isTemp ? "Delete" : "Cancel");
        }
    }

    private void disableAllButtons() {
        if (btnEdit != null) btnEdit.setDisable(true); 
        if (btnFinalize != null) btnFinalize.setDisable(true);
        if (btnSend != null) btnSend.setDisable(true); 
        if (btnPayment != null) btnPayment.setDisable(true); 
        if (btnCancel != null) btnCancel.setDisable(true);
        if (btnRevised != null) btnRevised.setDisable(true); 
        if (btnRaiseCnDn != null) btnRaiseCnDn.setDisable(true);
    }

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
                    currentPageIndex = 0;
                    updateSidebarTotals();
                    updatePieChartData();
                    repaginate();

                    if (selectedId != null && invoiceTable != null) {
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
                        }
                    }
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
        rebuildPaginationControls();
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

    private void rebuildPaginationControls() {
        if (paginationPagesBox == null) {
            return;
        }
        paginationPagesBox.getChildren().clear();
        int total = fullInvoiceResults.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));

        Button prev = new Button("‹");
        prev.getStyleClass().add("vi-page-btn");
        prev.setDisable(currentPageIndex <= 0);
        prev.setOnAction(e -> pageChange(currentPageIndex - 1));

        Button next = new Button("›");
        next.getStyleClass().add("vi-page-btn");
        next.setDisable(currentPageIndex >= pages - 1);
        next.setOnAction(e -> pageChange(currentPageIndex + 1));

        paginationPagesBox.getChildren().add(prev);

        final int window = 5;
        int start = Math.max(0, Math.min(currentPageIndex - 2, pages - window));
        int end = Math.min(pages, start + window);
        if (end - start < window) {
            start = Math.max(0, end - window);
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
    }

    private void updateSidebarTotals() {
        long n = fullInvoiceResults.size();
        double base = 0;
        double adj = 0;
        double net = 0;
        double paid = 0;
        double due = 0;
        for (InvoiceMaster inv : fullInvoiceResults) {
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

    private void updatePieChartData() {
        if (statusPieChart == null) {
            return;
        }
        int paid = 0;
        int unpaid = 0;
        int overdue = 0;
        for (InvoiceMaster inv : fullInvoiceResults) {
            String ps = inv.getPaymentStatus() != null ? inv.getPaymentStatus().toUpperCase() : "";
            if ("PAID".equals(ps)) {
                paid++;
            } else if ("OVERDUE".equals(ps)) {
                overdue++;
            } else {
                unpaid++;
            }
        }
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList(
                new PieChart.Data("Paid", paid),
                new PieChart.Data("Unpaid", unpaid),
                new PieChart.Data("Overdue", overdue));
        statusPieChart.setData(data);
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

    private void showAdjustmentDetails(InvoiceMaster inv) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Notes: " + inv.getInvoiceNo());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        TableView<InvoiceAdjustment> table = new TableView<>();
        table.setPrefWidth(500); table.setPrefHeight(300);
        TableColumn<InvoiceAdjustment, String> cType = new TableColumn<>("Type");
        cType.setCellValueFactory(new PropertyValueFactory<>("type"));
        TableColumn<InvoiceAdjustment, String> cNo = new TableColumn<>("No");
        cNo.setCellValueFactory(new PropertyValueFactory<>("noteNo"));
        TableColumn<InvoiceAdjustment, Double> cAmt = new TableColumn<>("Amount");
        cAmt.setCellValueFactory(new PropertyValueFactory<>("amount"));
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
                    label.setText(String.valueOf(amount));
                    if (adj != null) {
                        String type = adj.getType() != null ? adj.getType() : "";
                        String colorStyle = "";
                        if ("Credit Note".equalsIgnoreCase(type)) colorStyle = "-fx-text-fill: #dc3545; -fx-font-weight: bold;";
                        else if ("Debit Note".equalsIgnoreCase(type)) colorStyle = "-fx-text-fill: #28a745; -fx-font-weight: bold;";
                        
                        label.setStyle(colorStyle);
                    } else {
                        label.setStyle("");
                    }
                    setGraphic(label);
                    setText(null);
                }
            }
        });
        TableColumn<InvoiceAdjustment, String> cReason = new TableColumn<>("Reason");
        cReason.setCellValueFactory(new PropertyValueFactory<>("reason"));
        table.getColumns().addAll(cType, cNo, cAmt, cReason);
        List<InvoiceAdjustment> adjs = new ArrayList<>();
        try (java.sql.Connection con = utils.DBConnection.getConnection();
             java.sql.PreparedStatement ps = con.prepareStatement("SELECT type, note_no, amount, reason, date FROM invoice_adjustments WHERE invoice_id = ?")) {
            ps.setInt(1, inv.getId());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    InvoiceAdjustment a = new InvoiceAdjustment();
                    a.setType(rs.getString("type")); a.setNoteNo(rs.getString("note_no"));
                    a.setAmount(rs.getDouble("amount")); a.setReason(rs.getString("reason"));
                    adjs.add(a);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        table.setItems(FXCollections.observableArrayList(adjs));
        dialog.getDialogPane().setContent(table);
        dialog.showAndWait();
    }

    private void showPaymentDetails(InvoiceMaster inv) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Payment History: " + inv.getInvoiceNo());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        TableView<PaymentRecord> table = new TableView<>();
        table.setPrefWidth(500); table.setPrefHeight(300);
        
        TableColumn<PaymentRecord, String> cType = new TableColumn<>("Mode");
        cType.setCellValueFactory(new PropertyValueFactory<>("type"));
        
        TableColumn<PaymentRecord, String> cDate = new TableColumn<>("Date");
        cDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        
        TableColumn<PaymentRecord, Double> cAmt = new TableColumn<>("Amount");
        cAmt.setCellValueFactory(new PropertyValueFactory<>("amount"));
        cAmt.setCellFactory(col -> new TableCell<PaymentRecord, Double>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    setText(String.valueOf(item));
                    if (item < 0) setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                    else setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                }
            }
        });
        
        table.getColumns().addAll(cType, cDate, cAmt);
        List<PaymentRecord> records = new ArrayList<>();
        
        try (java.sql.Connection con = utils.DBConnection.getConnection();
             java.sql.PreparedStatement ps = con.prepareStatement(
                "SELECT p.type, p.payment_date, pa.allocated_amount " +
                " FROM payment_allocations pa " +
                " JOIN payments p ON pa.payment_id = p.id " +
                " WHERE pa.invoice_id = ? ORDER BY p.payment_date DESC")) {
            ps.setInt(1, inv.getId());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while(rs.next()) {
                    PaymentRecord r = new PaymentRecord();
                    r.setType(rs.getString(1));
                    r.setDate(rs.getString(2));
                    r.setAmount(rs.getDouble(3));
                    records.add(r);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        
        table.setItems(FXCollections.observableArrayList(records));
        dialog.getDialogPane().setContent(table);
        dialog.showAndWait();
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
