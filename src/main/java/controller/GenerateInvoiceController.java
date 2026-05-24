package controller;

import java.awt.Desktop;
import java.io.File;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.util.StringConverter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

import service.InvoiceMasterService.CreateOrGetResult;
import javafx.stage.Stage;

import model.Client;
import model.Invoice;
import model.MasterDocumentSeries;
import service.ClientService;
import service.InvoiceBuilderService;
import service.InvoiceMasterService;
import service.PdfInvoiceService;
import service.SettingsService;
import utils.DBConnection;
import utils.Toast;

import java.sql.Connection;

public class GenerateInvoiceController {

    @FXML
    private HBox breadcrumbContainer;
    @FXML
    private TableView<JobItem> jobsTable;
    @FXML
    private TableColumn<JobItem, Boolean> selectCol;
    @FXML
    private TableColumn<JobItem, String> jobNameCol;
    @FXML
    private TableColumn<JobItem, String> dateCol;
    @FXML
    private TableColumn<JobItem, String> qtyCol;
    @FXML
    private TableColumn<JobItem, String> rateCol;
    @FXML
    private TableColumn<JobItem, String> totalCol;
    @FXML
    private CheckBox selectAllCheckbox;

    @FXML
    private TextField jobSearchField;
    @FXML
    private ComboBox<Client> clientComboBox;
    @FXML
    private TextField invoiceNoField;
    @FXML
    private ComboBox<String> termsCombo;
    @FXML
    private DatePicker invoiceDatePicker;
    @FXML
    private DatePicker dueDatePicker;
    @FXML
    private TextArea notesArea;

    @FXML
    private Label itemCountLabel;
    @FXML
    private Label totalAmountLabel;
    @FXML
    private VBox jobsCard;

    @FXML
    private VBox tabBtnSelectedJobs;
    @FXML
    private VBox tabBtnDateRange;
    @FXML
    private VBox tabBtnMonthly;
    @FXML
    private VBox selectedJobsContainer;
    @FXML
    private VBox dateRangeContainer;
    @FXML
    private VBox monthlyContainer;
    @FXML
    private ComboBox<Month> monthlyMonthCombo;
    @FXML
    private ComboBox<Integer> monthlyYearCombo;
    @FXML
    private Label leftPanelTitle;
    @FXML
    private Label jobsTablePlaceholderSub;
    @FXML
    private DatePicker dateRangeFromPicker;
    @FXML
    private DatePicker dateRangeToPicker;
    @FXML
    private Label drTotalJobsLabel;
    @FXML
    private Label drTotalQtyLabel;
    @FXML
    private Label drTotalAmountLabel;
    @FXML
    private Text bannerDateFromText;
    @FXML
    private Text bannerDateToText;

    private enum BillingTab {
        SELECTED_JOBS, DATE_RANGE, MONTHLY
    }

    private BillingTab activeTab = BillingTab.SELECTED_JOBS;
    /** Keeps combo display when switching tabs (e.g. Date Range → Monthly) while combo is disabled. */
    private Client lastClientSelectionForMonthly;
    private int dateRangeJobCount;
    private long dateRangeTotalQty;
    private double dateRangeTotalAmount;

    private static final DateTimeFormatter DR_BANNER_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public void setRootPane(StackPane rootPane) {
        this.rootStackPane = rootPane;
    }

    /**
     * Run after the FXML root is attached to the scene. {@link MainController} loads FXML on a
     * background thread, so this re-applies tab state and live date-range totals on the FX thread.
     */
    public void onShownAfterNavigation() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::onShownAfterNavigation);
            return;
        }
        applyTabContent();
        refreshInvoiceNoPreview();
    }

    @FXML
    public void initialize() {
        filteredMonthlyJobs = new FilteredList<>(monthlyMasterJobs, j -> true);
        setupTable();
        if (jobsCard != null) {
            jobsCard.setMaxHeight(Region.USE_PREF_SIZE);
        }
        setupFields();
        setupClientCombo();
        setupJobSearch();
        setupDateRangeTab();
        setupMonthlyTab();
        refreshTabVisuals();
        applyTabContent();
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null, this::handleBack);
    }

    /** Updates the client box invoice number to the next value for the GST invoice sequence. */
    private void refreshInvoiceNoPreview() {
        if (invoiceNoField == null) {
            return;
        }
        LocalDate refDate = invoiceDatePicker != null ? invoiceDatePicker.getValue() : LocalDate.now();
        if (refDate == null) {
            refDate = LocalDate.now();
        }
        final LocalDate d = refDate;
        final MasterDocumentSeries series = selectedDocumentSeriesForGeneration();
        new Thread(() -> {
            try (Connection con = DBConnection.getConnection()) {
                String preview = settingsService.peekNextMasterNumberDisplay(con, series, d);
                Platform.runLater(() -> invoiceNoField.setText(preview));
            } catch (Exception e) {
                Platform.runLater(() -> invoiceNoField.setText("--"));
            }
        }, "invoice-no-preview").start();
    }

    private MasterDocumentSeries selectedDocumentSeriesForGeneration() {
        return MasterDocumentSeries.PROFORMA_INVOICE;
    }

    private final ClientService clientService = new ClientService();
    private final service.JobService jobService = new service.JobService();
    private final InvoiceBuilderService invoiceBuilder = new InvoiceBuilderService();
    private final InvoiceMasterService invoiceMasterService = new InvoiceMasterService();
    private final PdfInvoiceService pdfInvoiceService = new PdfInvoiceService();
    private final SettingsService settingsService = new SettingsService();

    private StackPane rootStackPane;
    private final ObservableList<Client> masterClients = FXCollections.observableArrayList();
    private final ObservableList<JobItem> masterJobs = FXCollections.observableArrayList();
    private final ObservableList<JobItem> monthlyMasterJobs = FXCollections.observableArrayList();
    private FilteredList<Client> filteredClients;
    private FilteredList<JobItem> filteredJobs;
    private FilteredList<JobItem> filteredMonthlyJobs;

    private void setupClientCombo() {
        if (clientComboBox == null)
            return;

        filteredClients = new FilteredList<>(masterClients, c -> true);
        clientComboBox.setItems(filteredClients);
        clientComboBox.setEditable(false);

        // Selection listener to load jobs
        clientComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                lastClientSelectionForMonthly = newV;
                if (activeTab != BillingTab.MONTHLY) {
                    loadJobsForClient(newV);
                }
            } else {
                if (activeTab != BillingTab.MONTHLY) {
                    // Combo can report null briefly when disabled; monthly table uses monthlyMasterJobs.
                } else {
                    masterJobs.clear();
                    updateSummary();
                }
            }
            if (activeTab == BillingTab.DATE_RANGE) {
                refreshDateRangeLive();
            }
            if (activeTab == BillingTab.MONTHLY) {
                refreshMonthlyJobs();
            }
        });

        // Premium Cell Factory
        clientComboBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Client c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null)
                    setText(null);
                else
                    setText(c.getBusinessName() + " (" + c.getClientName() + ")");
            }
        });

        // Button Cell (Visible when closed)
        clientComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Client c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null)
                    setText(null);
                else
                    setText(c.getBusinessName());
            }
        });

        final String promptHasClients = "Select client name.";
        final String promptNoClients = "No client exists to be invoiced";

        // Load only clients who have uninvoiced completed jobs (matches jobs table)
        try {
            List<Client> eligible = clientService.getClientsWithUninvoicedCompletedJobs();
            masterClients.setAll(eligible);
            if (eligible.isEmpty()) {
                clientComboBox.setPromptText(promptNoClients);
                clientComboBox.getSelectionModel().clearSelection();
                masterJobs.clear();
            } else {
                clientComboBox.setPromptText(promptHasClients);
            }
        } catch (Exception e) {
            masterClients.clear();
            clientComboBox.setPromptText(promptNoClients);
        }
    }

    private void setupTable() {
        jobsTable.setTableMenuButtonVisible(false);

        jobsTable.setRowFactory(tv -> {
            TableRow<JobItem> row = new TableRow<>();
            ChangeListener<Boolean> onSelected = (obs, o, n) -> styleInvoiceJobRow(row);
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (oldItem != null) {
                    oldItem.selectedProperty().removeListener(onSelected);
                }
                if (newItem != null) {
                    newItem.selectedProperty().addListener(onSelected);
                }
                styleInvoiceJobRow(row);
                applyInvoiceLastRowStyle(row);
            });
            row.indexProperty().addListener((o, a, b) -> applyInvoiceLastRowStyle(row));
            applyInvoiceLastRowStyle(row);
            return row;
        });

        masterJobs.addListener((ListChangeListener<JobItem>) c -> {
            if (jobsTable != null && jobsTable.getItems() == filteredJobs) {
                Platform.runLater(() -> {
                    refreshInvoiceLastRowStyles();
                    if (jobsTable.getItems().size() <= 1 && selectAllCheckbox != null) {
                        selectAllCheckbox.setSelected(false);
                    }
                });
            }
        });
        monthlyMasterJobs.addListener((ListChangeListener<JobItem>) c -> {
            if (jobsTable != null && jobsTable.getItems() == filteredMonthlyJobs) {
                Platform.runLater(() -> {
                    refreshInvoiceLastRowStyles();
                    if (jobsTable.getItems().size() <= 1 && selectAllCheckbox != null) {
                        selectAllCheckbox.setSelected(false);
                    }
                });
            }
        });

        // Select column with checkboxes
        selectCol.setCellValueFactory(data -> data.getValue().selectedProperty());
        selectCol.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setOnAction(e -> {
                    TableRow<JobItem> r = getTableRow();
                    if (r == null) {
                        return;
                    }
                    JobItem rowItem = r.getItem();
                    if (rowItem == null) {
                        return;
                    }
                    rowItem.setSelected(checkBox.isSelected());
                    jobsTable.refresh();
                    updateSummary();
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    checkBox.setSelected(item);
                    setGraphic(checkBox);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        jobNameCol.setCellValueFactory(data -> data.getValue().nameProperty());

        filteredJobs = new FilteredList<>(masterJobs, j -> true);
        jobsTable.setItems(filteredJobs);
        bindSelectAllCheckboxVisibility();
        jobNameCol.setCellFactory(column -> new TableCell<>() {
            private final Label title = new Label();
            private final Label subtitle = new Label();
            private final VBox container = new VBox(title, subtitle);
            {
                title.getStyleClass().add("job-name-main");
                subtitle.getStyleClass().add("job-name-sub");
                container.setSpacing(4);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                TableRow<JobItem> row = getTableRow();
                JobItem job = row != null ? row.getItem() : null;
                if (job == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                title.setText(item);
                subtitle.setText(job.getSubtitle());
                applyJobNameLabelColors(title, subtitle, job.isSelected());
                setGraphic(container);
                setAlignment(Pos.CENTER_LEFT);
            }
        });

        dateCol.setCellValueFactory(data -> data.getValue().dateProperty());
        dateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("invoice-cell-muted");
                setGraphic(null);
                if (empty || item == null) {
                    setText(null);
                    setStyle(null);
                } else {
                    TableRow<JobItem> tr = getTableRow();
                    JobItem job = tr != null ? tr.getItem() : null;
                    boolean sel = job != null && job.isSelected();
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                    applyDataCellTextStyle(this, sel);
                }
            }
        });

        qtyCol.setCellValueFactory(data -> data.getValue().qtyProperty());
        qtyCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("invoice-cell-muted");
                setGraphic(null);
                if (empty || item == null) {
                    setText(null);
                    setStyle(null);
                } else {
                    TableRow<JobItem> tr = getTableRow();
                    JobItem job = tr != null ? tr.getItem() : null;
                    boolean sel = job != null && job.isSelected();
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                    applyDataCellTextStyle(this, sel);
                }
            }
        });

        rateCol.setCellValueFactory(data -> data.getValue().rateProperty());
        rateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("invoice-cell-muted");
                setGraphic(null);
                if (empty || item == null) {
                    setText(null);
                    setStyle(null);
                } else {
                    TableRow<JobItem> tr = getTableRow();
                    JobItem job = tr != null ? tr.getItem() : null;
                    boolean sel = job != null && job.isSelected();
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                    applyDataCellTextStyle(this, sel);
                }
            }
        });

        totalCol.setCellValueFactory(data -> data.getValue().totalProperty());
        totalCol.setCellFactory(column -> new TableCell<>() {
            private final Text rupee = new Text("₹ ");
            private final Text amount = new Text();
            private final TextFlow flow = new TextFlow(rupee, amount);
            {
                flow.setTextAlignment(TextAlignment.RIGHT);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    amount.setText("");
                    rupee.setStyle(null);
                    amount.setStyle(null);
                    setGraphic(null);
                    return;
                }
                TableRow<JobItem> tr = getTableRow();
                JobItem job = tr != null ? tr.getItem() : null;
                boolean sel = job != null && job.isSelected();
                String num = item.replace("₹", "").trim();
                amount.setText(num);
                applyTotalTextStyles(rupee, amount, sel);
                setGraphic(flow);
                setAlignment(Pos.CENTER_RIGHT);
            }
        });

        selectAllCheckbox.setOnAction(e -> {
            boolean selected = selectAllCheckbox.isSelected();
            jobsTable.getItems().forEach(item -> item.setSelected(selected));
            jobsTable.refresh();
            updateSummary();
        });

        jobsTable.setFixedCellSize(72);
        bindJobsTableDynamicHeight();
        Platform.runLater(this::refreshInvoiceLastRowStyles);
    }

    private void bindJobsTableDynamicHeight() {
        if (jobsTable == null) {
            return;
        }
        jobsTable.prefHeightProperty().unbind();
        jobsTable.maxHeightProperty().unbind();
        final double headerHeight = 46;
        final double emptyBodyMin = 268;
        var tableHeight = Bindings.createDoubleBinding(
                () -> {
                    int n = jobsTable.getItems().size();
                    double body = n * jobsTable.getFixedCellSize();
                    if (n == 0) {
                        body = Math.max(body, emptyBodyMin);
                    }
                    return body + headerHeight + 1;
                },
                Bindings.size(jobsTable.getItems()));
        jobsTable.prefHeightProperty().bind(tableHeight);
        jobsTable.maxHeightProperty().bind(tableHeight);
        jobsTable.setMinHeight(headerHeight + 1);
    }

    private static void styleInvoiceJobRow(TableRow<JobItem> row) {
        if (row == null) {
            return;
        }
        row.getStyleClass().removeAll("invoice-row-muted", "invoice-row-selected");
        JobItem item = row.getItem();
        if (item == null || row.isEmpty()) {
            return;
        }
        row.getStyleClass().add(item.isSelected() ? "invoice-row-selected" : "invoice-row-muted");
    }

    /**
     * Last visible data row: no bottom border (row rule + skin/table edge was a
     * double line).
     */
    private void applyInvoiceLastRowStyle(TableRow<JobItem> row) {
        if (row == null || jobsTable == null) {
            return;
        }
        row.getStyleClass().remove("invoice-last-row");
        if (row.isEmpty()) {
            return;
        }
        int idx = row.getIndex();
        int n = jobsTable.getItems().size();
        if (idx >= 0 && n > 0 && idx == n - 1) {
            row.getStyleClass().add("invoice-last-row");
        }
    }

    private void refreshInvoiceLastRowStyles() {
        if (jobsTable == null) {
            return;
        }
        for (Node n : jobsTable.lookupAll(".table-row-cell")) {
            if (n instanceof TableRow) {
                @SuppressWarnings("unchecked")
                TableRow<JobItem> tr = (TableRow<JobItem>) n;
                applyInvoiceLastRowStyle(tr);
            }
        }
    }

    /**
     * Inline styles override Modena’s pressed/selected “ladder” (fixes text
     * vanishing on mouse-down).
     */
    private static void applyDataCellTextStyle(TableCell<?, String> cell, boolean rowChecked) {
        String fill = rowChecked ? "#3E312D" : "#57534E";
        cell.setStyle("-fx-text-fill: " + fill + "; -fx-opacity: 1;");
    }

    private static void applyJobNameLabelColors(Label title, Label subtitle, boolean rowChecked) {
        if (rowChecked) {
            title.setStyle("-fx-text-fill: #1C1917; -fx-opacity: 1;");
            subtitle.setStyle("-fx-text-fill: #57534E; -fx-opacity: 1;");
        } else {
            title.setStyle("-fx-text-fill: #44403C; -fx-opacity: 1;");
            subtitle.setStyle("-fx-text-fill: #78716C; -fx-opacity: 1;");
        }
    }

    private static void applyTotalTextStyles(Text rupee, Text amount, boolean rowChecked) {
        if (rowChecked) {
            rupee.setStyle("-fx-fill: #57534E; -fx-font-size: 13px; -fx-font-weight: 600; -fx-opacity: 1;");
            amount.setStyle("-fx-fill: #1C1917; -fx-font-size: 17px; -fx-font-weight: 800; -fx-opacity: 1;");
        } else {
            rupee.setStyle("-fx-fill: #78716C; -fx-font-size: 14px; -fx-font-weight: 600; -fx-opacity: 1;");
            amount.setStyle("-fx-fill: #44403C; -fx-font-size: 15px; -fx-font-weight: 800; -fx-opacity: 1;");
        }
    }

    /**
     * Header “select all” only when there is more than one row (single job → no
     * bulk select).
     */
    private void bindSelectAllCheckboxVisibility() {
        rebindSelectAllCheckboxToCurrentItems();
    }

    private void rebindSelectAllCheckboxToCurrentItems() {
        if (selectAllCheckbox == null || jobsTable == null) {
            return;
        }
        selectAllCheckbox.visibleProperty().unbind();
        selectAllCheckbox.managedProperty().unbind();
        var showSelectAll = Bindings.greaterThan(Bindings.size(jobsTable.getItems()), 1);
        selectAllCheckbox.visibleProperty().bind(showSelectAll);
        selectAllCheckbox.managedProperty().bind(showSelectAll);
    }

    private void setupFields() {
        if (invoiceNoField != null) {
            invoiceNoField.setEditable(false);
        }
        termsCombo.setItems(FXCollections.observableArrayList("Net 30", "Net 15", "Due on Receipt", "Custom"));
        termsCombo.setValue("Net 30");
        invoiceDatePicker.setEditable(false);
        dueDatePicker.setEditable(false);
        invoiceDatePicker.setValue(LocalDate.now());

        termsCombo.valueProperty().addListener((o, oldVal, newVal) -> applyTermsToDueDate());
        invoiceDatePicker.valueProperty().addListener((o, oldVal, newVal) -> {
            if (isAutoDueDateTerms(termsCombo.getValue())) {
                applyTermsToDueDate();
            }
            refreshInvoiceNoPreview();
        });

        applyTermsToDueDate();
        refreshInvoiceNoPreview();
    }

    /**
     * @return days after invoice date, or null when terms are Custom (user sets due
     *         date manually).
     */
    private static Integer daysFromTerms(String terms) {
        if (terms == null) {
            return 30;
        }
        switch (terms) {
            case "Net 30":
                return 30;
            case "Net 15":
                return 15;
            case "Due on Receipt":
                return 0;
            case "Custom":
                return null;
            default:
                return null;
        }
    }

    private static boolean isAutoDueDateTerms(String terms) {
        return daysFromTerms(terms) != null;
    }

    /**
     * Net 15 / Net 30 / Due on Receipt: due = invoice + days, picker disabled.
     * Custom: no auto update, picker enabled.
     */
    private void applyTermsToDueDate() {
        if (dueDatePicker == null || invoiceDatePicker == null || termsCombo == null) {
            return;
        }
        LocalDate inv = invoiceDatePicker.getValue();
        Integer add = daysFromTerms(termsCombo.getValue());
        if (add != null) {
            if (inv != null) {
                dueDatePicker.setValue(inv.plusDays(add));
            }
            dueDatePicker.setDisable(true);
        } else {
            dueDatePicker.setDisable(false);
        }
    }

    private void setupJobSearch() {
        jobSearchField.textProperty().addListener((obs, oldV, newV) -> {
            String q = newV == null ? "" : newV.toLowerCase().trim();
            filteredJobs.setPredicate(j -> {
                if (q.isEmpty())
                    return true;
                return j.nameProperty().get().toLowerCase().contains(q)
                        || j.getSubtitle().toLowerCase().contains(q);
            });
        });
    }

    private void setupDateRangeTab() {
        if (dateRangeFromPicker != null) {
            dateRangeFromPicker.setValue(LocalDate.now().withDayOfMonth(1));
            setupAutoPopupDatePicker(dateRangeFromPicker);
            dateRangeFromPicker.valueProperty().addListener((o, ov, nv) -> refreshDateRangeLive());
        }
        if (dateRangeToPicker != null) {
            dateRangeToPicker.setValue(LocalDate.now());
            setupAutoPopupDatePicker(dateRangeToPicker);
            dateRangeToPicker.valueProperty().addListener((o, ov, nv) -> refreshDateRangeLive());
        }
    }

    private void setupMonthlyTab() {
        if (monthlyMonthCombo != null) {
            monthlyMonthCombo.setItems(FXCollections.observableArrayList(Month.values()));
            monthlyMonthCombo.setConverter(new StringConverter<>() {
                @Override
                public String toString(Month m) {
                    return m == null ? "" : m.getDisplayName(TextStyle.FULL, Locale.getDefault());
                }

                @Override
                public Month fromString(String s) {
                    return null;
                }
            });
            monthlyMonthCombo.setValue(LocalDate.now().getMonth());
            monthlyMonthCombo.valueProperty().addListener((o, ov, nv) -> refreshMonthlyJobs());
        }
        if (monthlyYearCombo != null) {
            int y = LocalDate.now().getYear();
            List<Integer> years = new ArrayList<>();
            for (int i = y - 5; i <= y + 2; i++) {
                years.add(i);
            }
            monthlyYearCombo.setItems(FXCollections.observableArrayList(years));
            monthlyYearCombo.setValue(y);
            monthlyYearCombo.valueProperty().addListener((o, ov, nv) -> refreshMonthlyJobs());
        }
    }

    private void updateJobsTablePlaceholderText() {
        if (jobsTablePlaceholderSub == null) {
            return;
        }
        if (activeTab == BillingTab.MONTHLY) {
            jobsTablePlaceholderSub.setText(
                    "Choose month and year. The table lists completed jobs for all clients. Invoice uses the client shown above (only their selected rows).");
        } else {
            jobsTablePlaceholderSub.setText("Pick a client above to load jobs, or try a different search.");
        }
    }

    /** Load completed jobs for the chosen client in the selected calendar month (Monthly tab). */
    private void refreshMonthlyJobs() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refreshMonthlyJobs);
            return;
        }
        if (activeTab != BillingTab.MONTHLY) {
            return;
        }
        monthlyMasterJobs.clear();
        Month month = monthlyMonthCombo != null ? monthlyMonthCombo.getValue() : null;
        Integer year = monthlyYearCombo != null ? monthlyYearCombo.getValue() : null;
        if (month == null || year == null) {
            if (jobsTable != null) {
                jobsTable.refresh();
            }
            updateSummary();
            return;
        }
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        try {
            List<model.Job> jobs = jobService.getCompletedJobsAllClientsInDateRange(from, to);
            for (model.Job j : jobs) {
                String dateStr = j.getJobDate() != null ? j.getJobDate().toString() : "-";
                String totalStr = j.getJobTotal() != null ? String.format("₹ %,.2f", j.getJobTotal()) : "₹ 0.00";
                String biz = j.getClientBusinessName();
                String subtitle = (biz != null && !biz.isBlank() ? biz + " · " : "") + j.getJobNo();
                String cid = j.getClientId();
                monthlyMasterJobs.add(new JobItem(
                        j.getUuid(),
                        cid,
                        j.getJobTitle(),
                        subtitle,
                        dateStr,
                        "1",
                        totalStr,
                        totalStr));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (jobsTable != null) {
            jobsTable.refresh();
        }
        updateSummary();
    }

    /** Live totals for Date Range tab from client + From/To pickers (banner + metrics + right summary). */
    private void refreshDateRangeLive() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refreshDateRangeLive);
            return;
        }
        LocalDate from = dateRangeFromPicker != null ? dateRangeFromPicker.getValue() : null;
        LocalDate to = dateRangeToPicker != null ? dateRangeToPicker.getValue() : null;
        if (bannerDateFromText != null) {
            bannerDateFromText.setText(from != null ? from.format(DR_BANNER_FMT) : "—");
        }
        if (bannerDateToText != null) {
            bannerDateToText.setText(to != null ? to.format(DR_BANNER_FMT) : "—");
        }

        if (activeTab != BillingTab.DATE_RANGE) {
            return;
        }

        Client client = clientComboBox != null ? clientComboBox.getValue() : null;
        if (client == null || from == null || to == null || to.isBefore(from)) {
            dateRangeJobCount = 0;
            dateRangeTotalQty = 0;
            dateRangeTotalAmount = 0.0;
            if (drTotalJobsLabel != null) {
                drTotalJobsLabel.setText("0");
            }
            if (drTotalQtyLabel != null) {
                drTotalQtyLabel.setText("0");
            }
            if (drTotalAmountLabel != null) {
                drTotalAmountLabel.setText("₹ 0.00");
            }
            updateSummary();
            return;
        }

        try {
            List<String> uuids = jobService.getCompletedJobUuidsByClientInDateRange(client.getClientUuid(), from, to);
            dateRangeJobCount = uuids.size();
            dateRangeTotalQty = jobService.getTotalPrintingQtyForJobUuids(uuids);
            dateRangeTotalAmount = jobService.getSumJobItemsAmountForJobUuids(uuids);
        } catch (Exception e) {
            dateRangeJobCount = 0;
            dateRangeTotalQty = 0;
            dateRangeTotalAmount = 0.0;
            e.printStackTrace();
        }

        if (drTotalJobsLabel != null) {
            drTotalJobsLabel.setText(String.valueOf(dateRangeJobCount));
        }
        if (drTotalQtyLabel != null) {
            drTotalQtyLabel.setText(String.format("%,d", dateRangeTotalQty));
        }
        if (drTotalAmountLabel != null) {
            drTotalAmountLabel.setText(String.format("₹ %,.2f", dateRangeTotalAmount));
        }
        updateSummary();
    }

    private void setupAutoPopupDatePicker(DatePicker dp) {
        if (dp == null) {
            return;
        }
        dp.setEditable(false);
        dp.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null)
                    return;
                if (date.isAfter(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-opacity: 0.35;");
                }
            }
        });
        dp.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (!dp.isShowing())
                dp.show();
        });
        dp.focusedProperty().addListener((obs, oldV, isFocused) -> {
            if (Boolean.TRUE.equals(isFocused) && !dp.isShowing())
                dp.show();
        });
    }

    private void refreshTabVisuals() {
        if (tabBtnSelectedJobs != null) {
            tabBtnSelectedJobs.getStyleClass().remove("tab-active");
        }
        if (tabBtnDateRange != null) {
            tabBtnDateRange.getStyleClass().remove("tab-active");
        }
        if (tabBtnMonthly != null) {
            tabBtnMonthly.getStyleClass().remove("tab-active");
        }
        switch (activeTab) {
            case SELECTED_JOBS -> {
                if (tabBtnSelectedJobs != null)
                    tabBtnSelectedJobs.getStyleClass().add("tab-active");
            }
            case DATE_RANGE -> {
                if (tabBtnDateRange != null)
                    tabBtnDateRange.getStyleClass().add("tab-active");
            }
            case MONTHLY -> {
                if (tabBtnMonthly != null)
                    tabBtnMonthly.getStyleClass().add("tab-active");
            }
        }
    }

    private void applyTabContent() {
        boolean showSelected = activeTab == BillingTab.SELECTED_JOBS;
        boolean showDateRange = activeTab == BillingTab.DATE_RANGE;
        boolean showMonthly = activeTab == BillingTab.MONTHLY;
        boolean showJobsTable = showSelected || showMonthly;

        if (selectedJobsContainer != null) {
            selectedJobsContainer.setVisible(showSelected);
            selectedJobsContainer.setManaged(showSelected);
        }
        if (dateRangeContainer != null) {
            dateRangeContainer.setVisible(showDateRange);
            dateRangeContainer.setManaged(showDateRange);
        }
        if (monthlyContainer != null) {
            monthlyContainer.setVisible(showMonthly);
            monthlyContainer.setManaged(showMonthly);
        }
        if (jobsTable != null) {
            jobsTable.setVisible(showJobsTable);
            jobsTable.setManaged(showJobsTable);
        }
        if (leftPanelTitle != null) {
            leftPanelTitle.setText("Select Jobs");
        }

        boolean monthlyTab = showMonthly;
        if (clientComboBox != null && showMonthly) {
            Client cur = clientComboBox.getValue();
            if (cur != null) {
                lastClientSelectionForMonthly = cur;
            }
        }
        if (clientComboBox != null) {
            clientComboBox.setDisable(monthlyTab || masterClients.isEmpty());
        }
        if (invoiceNoField != null) {
            invoiceNoField.setDisable(activeTab == BillingTab.DATE_RANGE || monthlyTab);
        }

        refreshInvoiceNoPreview();

        updateJobsTablePlaceholderText();

        if (jobsTable != null) {
            if (showMonthly) {
                jobsTable.setItems(filteredMonthlyJobs);
            } else {
                jobsTable.setItems(filteredJobs);
            }
            bindJobsTableDynamicHeight();
            rebindSelectAllCheckboxToCurrentItems();
        }

        if (activeTab == BillingTab.DATE_RANGE) {
            refreshDateRangeLive();
        } else if (activeTab == BillingTab.MONTHLY) {
            refreshMonthlyJobs();
        } else {
            Client c = clientComboBox != null ? clientComboBox.getValue() : null;
            if (c != null) {
                loadJobsForClient(c);
            } else {
                masterJobs.clear();
                updateSummary();
            }
        }

        Platform.runLater(this::restoreClientComboSelection);
    }

    /** Re-select client in combo after tab/disable changes (value can appear lost on Monthly). */
    private void restoreClientComboSelection() {
        if (clientComboBox == null || masterClients.isEmpty()) {
            return;
        }
        Client ref = lastClientSelectionForMonthly;
        if (ref == null) {
            return;
        }
        masterClients.stream()
                .filter(c -> c.getClientUuid().equals(ref.getClientUuid()))
                .findFirst()
                .ifPresent(c -> {
                    if (activeTab == BillingTab.MONTHLY) {
                        clientComboBox.getSelectionModel().select(c);
                    } else if (clientComboBox.getValue() == null) {
                        clientComboBox.getSelectionModel().select(c);
                    }
                });
    }

    @FXML
    private void onTabSelectedJobs(MouseEvent e) {
        activeTab = BillingTab.SELECTED_JOBS;
        refreshTabVisuals();
        applyTabContent();
    }

    @FXML
    private void onTabDateRange(MouseEvent e) {
        activeTab = BillingTab.DATE_RANGE;
        refreshTabVisuals();
        applyTabContent();
    }

    @FXML
    private void onTabMonthly(MouseEvent e) {
        activeTab = BillingTab.MONTHLY;
        refreshTabVisuals();
        applyTabContent();
    }

    private void loadJobsForClient(Client client) {
        if (client == null)
            return;

        masterJobs.clear();
        try {
            System.out.println("DEBUG: Loading jobs for client ID: " + client.getClientUuid());
            List<model.Job> jobs = jobService.getCompletedJobsByClient(client.getClientUuid());
            System.out.println("DEBUG: Found " + jobs.size() + " jobs.");

            if (jobs.isEmpty()) {
                // Diagnostic: check if ANY jobs exist for this client
                List<model.Job> allJobs = jobService.getFullJobsByClientId(client.getClientUuid());
                System.out.println("DEBUG: Diagnostic - Total jobs (any status) for this client: " + allJobs.size());
                for (model.Job aj : allJobs) {
                    System.out.println("DEBUG: Job " + aj.getJobNo() + " has status: " + aj.getStatus());
                }
            }

            for (model.Job j : jobs) {
                String dateStr = j.getJobDate() != null ? j.getJobDate().toString() : "-";
                String totalStr = j.getJobTotal() != null ? String.format("₹ %,.2f", j.getJobTotal()) : "₹ 0.00";

                String cid = j.getClientId();
                masterJobs.add(new JobItem(
                        j.getUuid(),
                        cid,
                        j.getJobTitle(),
                        j.getJobNo(),
                        dateStr,
                        "1", // Default Qty to 1 as placeholder
                        totalStr, // Use total as rate for now
                        totalStr));
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Failed to load jobs: " + e.getMessage());
            e.printStackTrace();
        }
        updateSummary();
    }

    private void updateSummary() {
        if (activeTab == BillingTab.DATE_RANGE) {
            if (itemCountLabel != null) {
                long c = dateRangeJobCount;
                itemCountLabel.setText(c + (c == 1 ? " job" : " jobs"));
            }
            if (totalAmountLabel != null) {
                totalAmountLabel.setText(String.format("₹ %,.2f", dateRangeTotalAmount));
            }
            return;
        }

        long count = jobsTable.getItems().stream().filter(JobItem::isSelected).count();
        double total = jobsTable.getItems().stream()
                .filter(JobItem::isSelected)
                .mapToDouble(item -> {
                    String cleanTotal = item.getTotal().replace("₹", "").replace(",", "").trim();
                    try {
                        return Double.parseDouble(cleanTotal);
                    } catch (Exception e) {
                        return 0.0;
                    }
                })
                .sum();

        if (itemCountLabel != null) {
            itemCountLabel.setText(count + (count == 1 ? " job" : " jobs"));
        }
        if (totalAmountLabel != null) {
            totalAmountLabel.setText(String.format("₹ %,.2f", total));
        }
    }

    @FXML
    private void handleBack() {
        MainController.getInstance().handleBack(null);
    }

    @FXML
    private void handlePreview() {
        Client client = clientComboBox.getValue();
        if (client == null) {
            toast("Please select a client first.");
            return;
        }
        if (rootStackPane == null) {
            toast("Unable to show progress.");
            return;
        }

        final LocalDate invoiceDate = invoiceDatePicker.getValue();
        final boolean dateRangeMode = activeTab == BillingTab.DATE_RANGE;
        final LocalDate drFrom;
        final LocalDate drTo;
        final List<String> previewJobUuids;
        if (dateRangeMode) {
            LocalDate f = dateRangeFromPicker != null ? dateRangeFromPicker.getValue() : null;
            LocalDate t = dateRangeToPicker != null ? dateRangeToPicker.getValue() : null;
            if (f == null || t == null) {
                toast("Choose both From and To dates.");
                return;
            }
            if (t.isBefore(f)) {
                toast("To date must be on or after From date.");
                return;
            }
            if (dateRangeJobCount == 0) {
                toast("No completed jobs in the selected date range.");
                return;
            }
            drFrom = f;
            drTo = t;
            previewJobUuids = List.of();
        } else {
            drFrom = null;
            drTo = null;
            previewJobUuids = collectSelectedJobUuids();
            if (previewJobUuids.isEmpty()) {
                toast("Please select at least 1 job.");
                return;
            }
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/progress_dialog.fxml"));
            StackPane progressRoot = loader.load();
            ProgressDialogController progress = loader.getController();

            rootStackPane.getChildren().add(progressRoot);
            progress.show("Preview Invoice");

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    updateMessage("Building preview...");
                    Invoice invoice;
                    if (dateRangeMode) {
                        invoice = invoiceBuilder.buildInvoiceForClient(client.getClientUuid(),
                                client.getClientName(), client.getBusinessName(),
                                drFrom, drTo, invoiceDate, false);
                    } else {
                        invoice = invoiceBuilder.buildInvoiceForClientByJobs(client.getClientUuid(),
                                client.getClientName(), client.getBusinessName(),
                                previewJobUuids, invoiceDate, false);
                    }
                    invoice.setMasterDocumentSeries(selectedDocumentSeriesForGeneration());

                    if (isCancelled()) {
                        throw new CancellationException();
                    }
                    if (invoice.getJobs().isEmpty()) {
                        throw new RuntimeException("No invoice lines found.");
                    }

                    updateMessage("Generating PDF...");
                    File pdf = pdfInvoiceService.generateSingleInvoicePDF(invoice);
                    if (pdf == null || !pdf.exists()) {
                        throw new RuntimeException("PDF was not created.");
                    }

                    Platform.runLater(() -> {
                        try {
                            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                                Desktop.getDesktop().open(pdf);
                            } else {
                                toastSmall("PDF saved");
                            }
                        } catch (Exception e) {
                            toast("Could not open PDF: " + e.getMessage());
                        }
                    });

                    updateMessage("Done");
                    return null;
                }
            };

            progress.setOnCancel(task::cancel);

            task.setOnSucceeded(ev -> {
                progress.hide();
                rootStackPane.getChildren().remove(progressRoot);
            });

            task.setOnCancelled(ev -> {
                progress.hide();
                rootStackPane.getChildren().remove(progressRoot);
                toast("Preview cancelled.");
            });

            task.setOnFailed(ev -> {
                progress.hide();
                rootStackPane.getChildren().remove(progressRoot);
                Throwable ex = task.getException();
                toast("Preview failed: " + (ex != null ? ex.getMessage() : "Unknown"));
            });

            new Thread(task).start();
        } catch (Exception ex) {
            ex.printStackTrace();
            toast("Preview failed: " + ex.getMessage());
        }
    }

    @FXML
    private void handleGenerate() {
        Client client = clientComboBox.getValue();
        if (client == null) {
            toast("Please select a client first.");
            return;
        }
        if (rootStackPane == null) {
            toast("Unable to show progress.");
            return;
        }

        final LocalDate invoiceDate = invoiceDatePicker.getValue();
        final boolean dateRangeMode = activeTab == BillingTab.DATE_RANGE;
        final boolean monthlyMode = activeTab == BillingTab.MONTHLY;
        final LocalDate drFromGen;
        final LocalDate drToGen;
        final List<String> generateJobUuids;
        if (dateRangeMode) {
            LocalDate f = dateRangeFromPicker != null ? dateRangeFromPicker.getValue() : null;
            LocalDate t = dateRangeToPicker != null ? dateRangeToPicker.getValue() : null;
            if (f == null || t == null) {
                toast("Choose both From and To dates.");
                return;
            }
            if (t.isBefore(f)) {
                toast("To date must be on or after From date.");
                return;
            }
            if (dateRangeJobCount == 0) {
                toast("No completed jobs in the selected date range.");
                return;
            }
            drFromGen = f;
            drToGen = t;
            generateJobUuids = List.of();
        } else {
            drFromGen = null;
            drToGen = null;
            generateJobUuids = collectSelectedJobUuids();
            if (generateJobUuids.isEmpty()) {
                toast("Please select at least 1 job.");
                return;
            }
        }

        final List<String> newlyCreatedIds = new ArrayList<>();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/progress_dialog.fxml"));
            StackPane progressRoot = loader.load();
            ProgressDialogController progress = loader.getController();

            rootStackPane.getChildren().add(progressRoot);
            progress.show("Creating Draft Invoice");

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    updateProgress(0.1, 1);
                    updateMessage("Building invoice...");

                    Invoice invoice;
                    if (dateRangeMode) {
                        invoice = invoiceBuilder.buildInvoiceForClient(client.getClientUuid(),
                                client.getClientName(), client.getBusinessName(),
                                drFromGen, drToGen, invoiceDate);
                    } else {
                        invoice = invoiceBuilder.buildInvoiceForClientByJobs(client.getClientUuid(),
                                client.getClientName(), client.getBusinessName(), generateJobUuids, invoiceDate);
                    }
                    invoice.setMasterDocumentSeries(selectedDocumentSeriesForGeneration());

                    if (isCancelled()) {
                        throw new CancellationException();
                    }
                    if (invoice.getJobs().isEmpty()) {
                        throw new RuntimeException("No invoice lines found.");
                    }

                    if (dateRangeMode) {
                        updateMessage("Saving draft...");
                        CreateOrGetResult reserved = invoiceMasterService.createNewDraftInvoice(invoice, "DATE_RANGE",
                                null);
                        if (reserved != null) {
                            invoice.setInvoiceNo(reserved.master().getInvoiceNo());
                            if (reserved.wasNewlyCreated()) {
                                newlyCreatedIds.add(reserved.master().getUuid());
                            }
                        }
                        if (isCancelled()) {
                            throw new CancellationException();
                        }
                        invoiceMasterService.registerDateRangeInvoice(invoice, drFromGen,
                                drToGen, "DATE_RANGE", null);
                    } else {
                        updateMessage("Saving draft...");
                        invoiceMasterService.saveGeneratedInvoice(invoice, "JOB_SPECIFIC", "DRAFT", null);
                    }

                    updateProgress(1, 1);
                    updateMessage("Completed");
                    return null;
                }
            };

            progress.setOnCancel(task::cancel);

            task.setOnSucceeded(ev -> {
                progress.hide();
                rootStackPane.getChildren().remove(progressRoot);
                if (dateRangeMode) {
                    refreshDateRangeLive();
                } else if (monthlyMode) {
                    refreshMonthlyJobs();
                } else {
                    loadJobsForClient(client);
                }
                toast("Draft invoice created successfully.");
            });

            task.setOnCancelled(ev -> {
                if (dateRangeMode) {
                    new Thread(() -> invoiceMasterService.deleteInvoicesIfCancelled(newlyCreatedIds)).start();
                }
                progress.hide();
                rootStackPane.getChildren().remove(progressRoot);
                toast("Cancelled.");
            });

            task.setOnFailed(ev -> {
                progress.hide();
                rootStackPane.getChildren().remove(progressRoot);
                Throwable ex = task.getException();
                toast("Failed: " + (ex != null ? ex.getMessage() : "Unknown"));
            });

            new Thread(task).start();
        } catch (Exception ex) {
            ex.printStackTrace();
            toast("Failed: " + ex.getMessage());
        }
    }

    private List<String> collectSelectedJobUuids() {
        Client invoiceClient = clientComboBox != null ? clientComboBox.getValue() : null;
        return jobsTable.getItems().stream()
                .filter(JobItem::isSelected)
                .filter(item -> {
                    if (activeTab != BillingTab.MONTHLY || invoiceClient == null) {
                        return true;
                    }
                    String cid = item.getClientId();
                    return cid == null || cid.isBlank() || cid.equals(invoiceClient.getClientUuid());
                })
                .map(JobItem::getJobUuid)
                .filter(u -> u != null && !u.isBlank())
                .distinct()
                .toList();
    }

    private void toast(String message) {
        Node anchor = clientComboBox != null ? clientComboBox : jobsTable;
        if (anchor == null || anchor.getScene() == null) {
            return;
        }
        Stage stage = (Stage) anchor.getScene().getWindow();
        Toast.show(stage, message);
    }

    private void toastSmall(String message) {
        Node anchor = clientComboBox != null ? clientComboBox : jobsTable;
        if (anchor == null || anchor.getScene() == null) {
            return;
        }
        Stage stage = (Stage) anchor.getScene().getWindow();
        Toast.showSmall(stage, message);
    }

    // Helper class for TableView
    public static class JobItem {
        private final String jobUuid;
        /** null = unknown / single-client screen; otherwise jobs.client_uuid for monthly filtering. */
        private final String clientId;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        private final SimpleStringProperty name;
        private final SimpleStringProperty subtitle;
        private final SimpleStringProperty date;
        private final SimpleStringProperty qty;
        private final SimpleStringProperty rate;
        private final SimpleStringProperty total;

        public JobItem(String jobUuid, String name, String subtitle, String date, String qty, String rate, String total) {
            this(jobUuid, null, name, subtitle, date, qty, rate, total);
        }

        public JobItem(String jobUuid, String clientId, String name, String subtitle, String date, String qty, String rate,
                String total) {
            this.jobUuid = jobUuid;
            this.clientId = clientId;
            this.name = new SimpleStringProperty(name);
            this.subtitle = new SimpleStringProperty(subtitle);
            this.date = new SimpleStringProperty(date);
            this.qty = new SimpleStringProperty(qty);
            this.rate = new SimpleStringProperty(rate);
            this.total = new SimpleStringProperty(total);
        }

        public SimpleBooleanProperty selectedProperty() {
            return selected;
        }

        public boolean isSelected() {
            return selected.get();
        }

        public void setSelected(boolean val) {
            selected.set(val);
        }

        public String getJobUuid() {
            return jobUuid;
        }

        /** @deprecated use {@link #getJobUuid()} */
        public String getJobId() {
            return jobUuid;
        }

        public String getClientId() {
            return clientId;
        }

        public SimpleStringProperty nameProperty() {
            return name;
        }

        public String getSubtitle() {
            return subtitle.get();
        }

        public SimpleStringProperty dateProperty() {
            return date;
        }

        public SimpleStringProperty qtyProperty() {
            return qty;
        }

        public SimpleStringProperty rateProperty() {
            return rate;
        }

        public SimpleStringProperty totalProperty() {
            return total;
        }

        public String getTotal() {
            return total.get();
        }
    }
}
