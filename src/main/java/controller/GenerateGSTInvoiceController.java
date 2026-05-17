package controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.sql.Connection;
import java.util.Set;

public class GenerateGSTInvoiceController implements Initializable {

    @FXML private Button btnSaveDraft;
    @FXML private Button btnGenerateInvoice;

    @FXML private HBox breadcrumbContainer;
    @FXML private Button btnEditTerms;
    
    @FXML private TextField txtInvoiceNo;
    @FXML private DatePicker dpInvoiceDate;
    @FXML private ComboBox<String> comboPlaceOfSupply;
    @FXML private ComboBox<String> comboReverseCharge;
    @FXML private ComboBox<String> comboPaymentTerms;
    @FXML private DatePicker dpDueDate;
    @FXML private TextField txtVehicleDispatch;
    @FXML private TextField txtPoNo;

    @FXML private ComboBox<model.Client> comboCompanyFrom;
    @FXML private ComboBox<model.Client> comboShipTo;
    @FXML private ComboBox<model.Client> comboBillTo;

    @FXML private FlowPane flowJobsContainer;

    @FXML private TableView<ItemRow> tableItems;
    @FXML private Button btnAddItem;
    @FXML private Button btnImportJob;
    @FXML private Button btnClearAll;

    @FXML private TableView<HsnSummaryRow> tableHsnSummary;
    @FXML private ComboBox<String> comboBankDetails;

    @FXML private Label lblGrandTotal;
    @FXML private Label lblTotalInWords;
    @FXML private Label lblTermsFooter;

    private final service.InvoiceMasterService invoiceService = new service.InvoiceMasterService();
    private final service.GstPdfInvoiceService pdfService = new service.GstPdfInvoiceService();
    private final service.ClientService clientService = new service.ClientService();
    private final service.JobService jobService = new service.JobService();
    private final service.JobItemService jobItemService = new service.JobItemService();
    private final service.HsnSacService hsnSacService = new service.HsnSacService();
    private final service.SettingsService settingsService = new service.SettingsService();

    private final List<model.JobSummary> loadedJobSummaries = new ArrayList<>();
    private final Set<String> selectedJobUuids = new LinkedHashSet<>();
    private final ObservableList<ItemRow> itemRows = FXCollections.observableArrayList();
    private final ObservableList<HsnSummaryRow> hsnRows = FXCollections.observableArrayList();
    private final ObservableList<String> printingHsnOptions = FXCollections.observableArrayList();
    private final java.util.Map<String, model.HsnSacInfo> printingHsnInfoByCode = new java.util.HashMap<>();

    private static final DateTimeFormatter JOB_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final double DEFAULT_GST_RATE = 0.18;
    private static final String DEFAULT_COMPANY_STATE_CODE = "07"; // Delhi (07)

    private final StringProperty termsText = new SimpleStringProperty("");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize logic here
        setupInitialData();
        setupClientCombos();
        setupJobsBox();
        setupItemsTable();
        setupHsnSummaryTable();
        setupBreadcrumbs();
        setupTerms();
        loadPrintingHsnOptions();
        refreshInvoiceNoPreview();
    }

    private void loadPrintingHsnOptions() {
        printingHsnOptions.clear();
        printingHsnInfoByCode.clear();
        try {
            List<model.HsnSacInfo> list = hsnSacService.listActiveByType("PRINTING");
            for (model.HsnSacInfo i : list) {
                if (i == null || i.getHsnSac() == null || i.getHsnSac().isBlank()) {
                    continue;
                }
                String code = i.getHsnSac().trim();
                printingHsnInfoByCode.put(code, i);
                if (!printingHsnOptions.contains(code)) {
                    printingHsnOptions.add(code);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        if (!printingHsnOptions.contains("—")) {
            printingHsnOptions.add(0, "—");
        }
    }

    private void setupBreadcrumbs() {
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null,
                () -> controller.MainController.getInstance().handleBack(null));
    }

    private void setupTerms() {
        String defaults = String.join("\n",
                "• GOODS ONCE SOLD WILL NOT BE TAKEN BACK.",
                "• INTEREST @24% P.A WILL BE CHARGED IF THE AMOUNT IS NOT PAID ON DEMAND.",
                "• ALL DISPUTES SUBJECT TO DELHI JURISDICTION.",
                "• ONLY AFTER PRINTING THE PLATES WILL NOT BE RETURNED."
        );
        if (termsText.get() == null || termsText.get().isBlank()) {
            termsText.set(defaults);
        }
        if (lblTermsFooter != null) {
            lblTermsFooter.textProperty().bind(termsText);
        }
    }

    @SuppressWarnings("unchecked")
    private void setupItemsTable() {
        if (tableItems == null) {
            return;
        }

        tableItems.setItems(itemRows);

        List<TableColumn<ItemRow, ?>> cols = (List<TableColumn<ItemRow, ?>>) (List<?>) tableItems.getColumns();
        if (cols == null || cols.isEmpty()) {
            return;
        }

        // 0 #, 1 Description, 2 HSN/SAC, 3 Qty, 4 Unit, 5 Rate, 6 Taxable, 7 GST%, 8 CGST, 9 SGST, 10 IGST, 11 Total, 12 Action
        if (cols.size() >= 1) ((TableColumn<ItemRow, Number>) cols.get(0)).setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getSlNo()));
        if (cols.size() >= 2) ((TableColumn<ItemRow, String>) cols.get(1)).setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getDescription()));
        if (cols.size() >= 3) ((TableColumn<ItemRow, String>) cols.get(2)).setCellValueFactory(c -> c.getValue().hsnSacProperty());
        if (cols.size() >= 4) ((TableColumn<ItemRow, String>) cols.get(3)).setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getQty()));
        if (cols.size() >= 5) ((TableColumn<ItemRow, String>) cols.get(4)).setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getUnit()));
        if (cols.size() >= 6) ((TableColumn<ItemRow, String>) cols.get(5)).setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getRate()));
        if (cols.size() >= 7) ((TableColumn<ItemRow, String>) cols.get(6)).setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getTaxable()));
        if (cols.size() >= 8) ((TableColumn<ItemRow, String>) cols.get(7)).setCellValueFactory(c -> c.getValue().gstPercentProperty());
        if (cols.size() >= 9) ((TableColumn<ItemRow, String>) cols.get(8)).setCellValueFactory(c -> c.getValue().cgstProperty());
        if (cols.size() >= 10) ((TableColumn<ItemRow, String>) cols.get(9)).setCellValueFactory(c -> c.getValue().sgstProperty());
        if (cols.size() >= 11) ((TableColumn<ItemRow, String>) cols.get(10)).setCellValueFactory(c -> c.getValue().igstProperty());
        if (cols.size() >= 12) ((TableColumn<ItemRow, String>) cols.get(11)).setCellValueFactory(c -> c.getValue().totalProperty());

        // Enable editable HSN/SAC dropdown (column index 2)
        if (cols.size() >= 3) {
            tableItems.setEditable(true);
            TableColumn<ItemRow, String> hsnCol = (TableColumn<ItemRow, String>) cols.get(2);
            hsnCol.setEditable(true);
            hsnCol.setCellFactory(ComboBoxTableCell.forTableColumn(printingHsnOptions));
            hsnCol.setOnEditCommit(ev -> {
                ItemRow row = ev.getRowValue();
                if (row == null) return;

                String code = ev.getNewValue() != null ? ev.getNewValue().trim() : "—";
                row.setHsnSac(code);

                model.HsnSacInfo info = printingHsnInfoByCode.get(code);
                double gstRate = info != null && info.getGstRate() > 0 ? info.getGstRate() : DEFAULT_GST_RATE;
                row.recalcTaxes(gstRate, isIntraStateSupply());
                refreshHsnSummaryFromItemRows();
            });
        }
    }

    private void setupInitialData() {
        if (txtInvoiceNo != null) {
            txtInvoiceNo.setEditable(false);
        }
        if (dpInvoiceDate != null) {
            dpInvoiceDate.setValue(java.time.LocalDate.now());
            dpInvoiceDate.valueProperty().addListener((obs, oldV, newV) -> {
                refreshInvoiceNoPreview();
                recalcDueDate();
            });
        }

        if (comboPaymentTerms != null) {
            // If nothing is selected, default to prompt (often "30 Days")
            comboPaymentTerms.valueProperty().addListener((obs, oldV, newV) -> recalcDueDate());
        }

        if (dpDueDate != null) {
            dpDueDate.setEditable(false);
        }

        recalcDueDate();
    }

    private void recalcDueDate() {
        if (dpDueDate == null) {
            return;
        }
        java.time.LocalDate inv = dpInvoiceDate != null ? dpInvoiceDate.getValue() : null;
        if (inv == null) {
            inv = java.time.LocalDate.now();
        }

        String term = comboPaymentTerms != null ? comboPaymentTerms.getValue() : null;
        if (term == null || term.isBlank()) {
            term = comboPaymentTerms != null ? comboPaymentTerms.getPromptText() : null;
        }

        int days = parsePaymentTermDays(term);
        if (days <= 0) {
            // fallback: keep due date = invoice date
            dpDueDate.setValue(inv);
            return;
        }
        dpDueDate.setValue(inv.plusDays(days));
    }

    private static int parsePaymentTermDays(String term) {
        if (term == null) return 0;
        String t = term.trim().toLowerCase();
        // Accept: "30", "30 days", "Net 30", "NET30"
        String digits = t.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void refreshInvoiceNoPreview() {
        if (txtInvoiceNo == null) {
            return;
        }
        java.time.LocalDate d = dpInvoiceDate != null && dpInvoiceDate.getValue() != null
                ? dpInvoiceDate.getValue()
                : java.time.LocalDate.now();
        new Thread(() -> {
            try (Connection con = utils.DBConnection.getConnection()) {
                String preview = settingsService.peekNextMasterNumberDisplay(con, model.MasterDocumentSeries.GST_INVOICE, d);
                javafx.application.Platform.runLater(() -> txtInvoiceNo.setText(preview));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> txtInvoiceNo.setText("--"));
            }
        }, "gst-invoice-no-preview").start();
    }

    private void setupClientCombos() {
        List<model.Client> clients = clientService.getAllClients();
        if (comboBillTo != null) {
            comboBillTo.getItems().setAll(clients);
            comboBillTo.setCellFactory(cb -> new ListCell<>() {
                @Override
                protected void updateItem(model.Client item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        String name = item.getBusinessName() != null && !item.getBusinessName().isBlank()
                                ? item.getBusinessName()
                                : item.getClientName();
                        setText(name);
                    }
                }
            });
            comboBillTo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(model.Client item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText("Select buyer");
                    } else {
                        String name = item.getBusinessName() != null && !item.getBusinessName().isBlank()
                                ? item.getBusinessName()
                                : item.getClientName();
                        setText(name);
                    }
                }
            });
            comboBillTo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) {
                    loadJobsForClient(newV.getClientUuid());
                } else {
                    clearJobsBox();
                }
            });
        }

        // For now, keep Company/ShipTo lists same as BillTo.
        if (comboCompanyFrom != null) {
            comboCompanyFrom.getItems().setAll(clients);
        }
        if (comboShipTo != null) {
            comboShipTo.getItems().setAll(clients);
        }
    }

    private void setupJobsBox() {
        if (flowJobsContainer == null) {
            return;
        }
        flowJobsContainer.setHgap(14);
        flowJobsContainer.setVgap(10);
        flowJobsContainer.setPrefWrapLength(900);
    }

    private void clearJobsBox() {
        loadedJobSummaries.clear();
        selectedJobUuids.clear();
        itemRows.clear();
        hsnRows.clear();
        if (flowJobsContainer != null) {
            flowJobsContainer.getChildren().clear();
        }
    }

    private void loadJobsForClient(String clientUuid) {
        clearJobsBox();
        if (flowJobsContainer == null) {
            return;
        }
        List<model.JobSummary> jobs = jobService.getJobsByClientId(clientUuid); // completed + uninvoiced
        loadedJobSummaries.addAll(jobs);

        if (jobs.isEmpty()) {
            Label empty = new Label("No completed jobs available for invoicing.");
            empty.getStyleClass().add("gst-muted");
            flowJobsContainer.getChildren().add(empty);
            return;
        }

        for (model.JobSummary js : jobs) {
            VBox card = new VBox(4);
            card.getStyleClass().add("gst-job-card");
            card.setAlignment(Pos.TOP_LEFT);

            CheckBox cb = new CheckBox();
            cb.getStyleClass().add("gst-job-check");
            cb.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (isSelected) {
                    selectedJobUuids.add(js.getUuid());
                    card.getStyleClass().add("gst-job-card-selected");
                } else {
                    selectedJobUuids.remove(js.getUuid());
                    card.getStyleClass().remove("gst-job-card-selected");
                }
                refreshItemsTableFromSelectedJobs();
            });

            Label no = new Label(js.getJobNo());
            no.getStyleClass().add("gst-chip-title");
            Label title = new Label(js.getJobTitle());
            title.getStyleClass().add("gst-chip-sub");
            Label date = new Label("Completed: " + (js.getJobDate() != null ? js.getJobDate().format(JOB_DATE_FMT) : "—"));
            date.getStyleClass().add("gst-chip-meta");

            card.getChildren().addAll(cb, no, title, date);
            flowJobsContainer.getChildren().add(card);
        }
    }

    private void refreshItemsTableFromSelectedJobs() {
        itemRows.clear();
        if (selectedJobUuids.isEmpty()) {
            hsnRows.clear();
            return;
        }

        boolean intraState = isIntraStateSupply();
        int sl = 1;
        for (model.JobSummary js : loadedJobSummaries) {
            if (!selectedJobUuids.contains(js.getUuid())) {
                continue;
            }

            List<model.JobItem> items = jobItemService.getJobItems(js.getUuid());
            if (items == null || items.isEmpty()) {
                // fallback: single job row (previous behavior)
                double taxable = jobService.getSumJobItemsAmountForJobUuids(List.of(js.getUuid()));
                long qty = jobService.getTotalPrintingQtyForJobUuids(List.of(js.getUuid()));
                double rate = qty > 0 ? (taxable / qty) : 0.0;
                Taxes taxes = Taxes.compute(taxable, DEFAULT_GST_RATE, intraState);
                itemRows.add(ItemRow.ofJob(sl++, js.getUuid(), js.getJobTitle(), "—", qty, "PCS", rate, taxable, taxes));
                continue;
            }

            for (model.JobItem ji : items) {
                model.HsnSacInfo info = hsnSacService.lookup(ji);
                String hsn = info != null && info.getHsnSac() != null && !info.getHsnSac().isBlank() ? info.getHsnSac() : "—";
                double gstRate = info != null && info.getGstRate() > 0 ? info.getGstRate() : DEFAULT_GST_RATE;

                QtyUnit qu = QtyUnit.resolveForJobItem(ji);
                long qty = qu.qty;
                String unit = qu.unit != null && !qu.unit.isBlank()
                        ? qu.unit
                        : (info != null ? info.getUnitDefault() : "PCS");
                if (unit == null || unit.isBlank()) {
                    unit = "PCS";
                }

                double taxable = ji.getAmount();
                double rate = qty > 0 ? (taxable / qty) : 0.0;
                Taxes taxes = Taxes.compute(taxable, gstRate, intraState);

                itemRows.add(ItemRow.ofJob(sl++, js.getUuid(), ji.getDescription(), hsn, qty, unit, rate, taxable, taxes, gstRate));
            }
        }

        refreshHsnSummaryFromItemRows();
    }

    @SuppressWarnings("unchecked")
    private void setupHsnSummaryTable() {
        if (tableHsnSummary == null) {
            return;
        }

        tableHsnSummary.setItems(hsnRows);
        List<TableColumn<HsnSummaryRow, ?>> cols = (List<TableColumn<HsnSummaryRow, ?>>) (List<?>) tableHsnSummary.getColumns();
        if (cols == null || cols.isEmpty()) {
            return;
        }

        // FXML columns: 0 HSN/SAC, 1 Taxable Value, 2 GST%, 3 CGST, 4 SGST, 5 IGST, 6 Total Tax
        if (cols.size() >= 1) ((TableColumn<HsnSummaryRow, String>) cols.get(0)).setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getHsnSac()));
        if (cols.size() >= 2) ((TableColumn<HsnSummaryRow, String>) cols.get(1)).setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getTaxable()));
        if (cols.size() >= 3) ((TableColumn<HsnSummaryRow, String>) cols.get(2)).setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getGstPercent()));
        if (cols.size() >= 4) ((TableColumn<HsnSummaryRow, String>) cols.get(3)).setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getCgst()));
        if (cols.size() >= 5) ((TableColumn<HsnSummaryRow, String>) cols.get(4)).setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getSgst()));
        if (cols.size() >= 6) ((TableColumn<HsnSummaryRow, String>) cols.get(5)).setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getIgst()));
        if (cols.size() >= 7) ((TableColumn<HsnSummaryRow, String>) cols.get(6)).setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getTotalTax()));
    }

    private void refreshHsnSummaryFromItemRows() {
        hsnRows.clear();
        if (itemRows.isEmpty()) {
            return;
        }

        class Agg {
            double taxable = 0;
            double cgst = 0;
            double sgst = 0;
            double igst = 0;
            double gstRate = DEFAULT_GST_RATE;
        }

        java.util.Map<String, Agg> byHsn = new java.util.LinkedHashMap<>();
        for (ItemRow r : itemRows) {
            String hsn = r.getHsnSac() != null && !r.getHsnSac().isBlank() ? r.getHsnSac().trim() : "—";
            Agg a = byHsn.computeIfAbsent(hsn, k -> new Agg());
            a.taxable += r.taxableRaw.get();
            a.cgst += r.cgstRaw.get();
            a.sgst += r.sgstRaw.get();
            a.igst += r.igstRaw.get();
            a.gstRate = r.gstRateRaw.get() > 0 ? r.gstRateRaw.get() : a.gstRate;
        }

        for (var e : byHsn.entrySet()) {
            Agg a = e.getValue();
            hsnRows.add(HsnSummaryRow.of(
                    e.getKey(),
                    round2(a.taxable),
                    a.gstRate,
                    round2(a.cgst),
                    round2(a.sgst),
                    round2(a.igst)
            ));
        }
    }

    private boolean isIntraStateSupply() {
        String companyGst = utils.CompanyProfile.getGst();
        String companyCode = extractStateCode(companyGst);
        if (companyCode == null || companyCode.isBlank()) {
            companyCode = DEFAULT_COMPANY_STATE_CODE;
        }

        String pos = comboPlaceOfSupply != null ? comboPlaceOfSupply.getValue() : null;
        String posCode = extractStateCode(pos);
        if (posCode == null || posCode.isBlank()) {
            // if unknown, treat as inter-state to avoid splitting
            return false;
        }
        return companyCode.equals(posCode);
    }

    private static String extractStateCode(String s) {
        if (s == null) return null;
        // GSTIN starts with 2 digits; combo values contain "(07)" etc.
        String trimmed = s.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d{2})\\)").matcher(trimmed);
        if (m.find()) return m.group(1);
        m = java.util.regex.Pattern.compile("^(\\d{2})").matcher(trimmed);
        if (m.find()) return m.group(1);
        return null;
    }

    private record QtyUnit(long qty, String unit) {
        static QtyUnit resolveForJobItem(model.JobItem ji) {
            if (ji == null) return new QtyUnit(0, "PCS");

            try {
                switch (ji.getType() != null ? ji.getType().trim().toUpperCase() : "") {
                    case "PRINTING" -> {
                        model.Printing p = new repository.PrintingItemRepository().findByJobItemUuid(ji.getUuid());
                        if (p != null) return new QtyUnit(p.getQty(), p.getUnits());
                    }
                    case "PAPER" -> {
                        model.Paper p = new repository.PaperItemRepository().findByJobItemUuid(ji.getUuid());
                        if (p != null) return new QtyUnit(p.getQty(), p.getUnits());
                    }
                    case "BINDING" -> {
                        model.Binding b = new repository.BindingItemRepository().findByJobItemUuid(ji.getUuid());
                        if (b != null) return new QtyUnit(b.getQty(), "PCS");
                    }
                    case "LAMINATION" -> {
                        model.Lamination l = new repository.LaminationItemRepository().findByJobItemUuid(ji.getUuid());
                        if (l != null) return new QtyUnit(l.getQty(), l.getUnit());
                    }
                    case "CTP" -> {
                        model.CtpPlate c = new repository.CtpItemRepository().findByJobItemUuid(ji.getUuid());
                        if (c != null) return new QtyUnit(c.getQty(), "PCS");
                    }
                    default -> { }
                }
            } catch (Exception e) {
                // ignore and fallback below
            }
            return new QtyUnit(0, "PCS");
        }
    }

    private record Taxes(double cgst, double sgst, double igst, double totalTax, double total) {
        static Taxes compute(double taxable, double gstRate, boolean intraState) {
            if (gstRate < 0) gstRate = 0;
            double cgst = 0;
            double sgst = 0;
            double igst = 0;
            if (intraState) {
                cgst = round2(taxable * (gstRate / 2.0));
                sgst = round2(taxable * (gstRate / 2.0));
            } else {
                igst = round2(taxable * gstRate);
            }
            double totalTax = round2(cgst + sgst + igst);
            double total = round2(taxable + totalTax);
            return new Taxes(cgst, sgst, igst, totalTax, total);
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @FXML
    private void handleSaveDraft() {
        processInvoiceGeneration("DRAFT");
    }

    @FXML
    private void handleGenerateInvoice() {
        processInvoiceGeneration("SENT");
    }

    private void processInvoiceGeneration(String status) {
        try {
            model.Invoice invoice = new model.Invoice();
            invoice.setInvoiceNo(txtInvoiceNo.getText());
            invoice.setInvoiceDate(dpInvoiceDate.getValue());
            model.Client buyer = comboBillTo != null ? comboBillTo.getValue() : null;
            if (buyer != null) {
                String name = buyer.getBusinessName() != null && !buyer.getBusinessName().isBlank()
                        ? buyer.getBusinessName()
                        : buyer.getClientName();
                invoice.setClientName(name);
                invoice.setClientId(buyer.getClientUuid());
                invoice.setBuyerAddress(buyer.getBillingAddress());
                invoice.setBuyerGstin(buyer.getGst());
                invoice.setBuyerStateName(comboPlaceOfSupply != null ? comboPlaceOfSupply.getValue() : null);
            }
            invoice.setStatus(status);
            invoice.setInvoiceType("GST_INVOICE");
            invoice.setMasterDocumentSeries(model.MasterDocumentSeries.GST_INVOICE);

            if (selectedJobUuids.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Missing Items");
                alert.setHeaderText("Nothing to generate");
                alert.setContentText("Please select one or more jobs in the Job box before generating the invoice.");
                alert.showAndWait();
                return;
            }

            List<String> selectedIds = new ArrayList<>(selectedJobUuids);

            // Build invoice jobs for each selected job.
            for (model.JobSummary js : loadedJobSummaries) {
                if (!selectedJobUuids.contains(js.getUuid())) {
                    continue;
                }

                model.InvoiceJob invJob = new model.InvoiceJob();
                invJob.setJobId(js.getUuid());
                invJob.setJobNo(js.getJobNo());
                invJob.setJobName(js.getJobTitle());
                invJob.setJobDate(js.getJobDate());

                // Pull totals/qty from DB so PDF matches real amounts.
                double jobTaxable = jobService.getSumJobItemsAmountForJobUuids(List.of(js.getUuid()));
                long jobQty = jobService.getTotalPrintingQtyForJobUuids(List.of(js.getUuid()));
                invJob.setQuantity(jobQty);
                invJob.setUnit("PCS");
                if (jobQty > 0) {
                    invJob.setRatePerUnit(jobTaxable / jobQty);
                }
                invJob.addLine(new model.InvoiceLine(js.getJobTitle(), jobTaxable));
                invoice.addJob(invJob);
            }

            // Consignee defaults to Ship To selection; if missing, reuse buyer.
            model.Client ship = comboShipTo != null ? comboShipTo.getValue() : null;
            if (ship == null) {
                ship = buyer;
            }
            if (ship != null) {
                String n = ship.getBusinessName() != null && !ship.getBusinessName().isBlank()
                        ? ship.getBusinessName()
                        : ship.getClientName();
                invoice.setConsigneeName(n);
                invoice.setConsigneeAddress(ship.getShippingAddress());
                invoice.setConsigneeGstin(ship.getGst());
                invoice.setConsigneeStateName(comboPlaceOfSupply != null ? comboPlaceOfSupply.getValue() : null);
            }

            // Ensure grand total includes IGST like your original sample.
            double taxable = jobService.getSumJobItemsAmountForJobUuids(selectedIds);
            double igst = Math.round(taxable * 0.18 * 100.0) / 100.0;
            invoice.setGrandTotal(Math.round((taxable + igst) * 100.0) / 100.0);
            
            // Note: Jobs should be collected from flowJobsContainer or a selected list
            // For now, we simulate with a dummy check if anything is present
            
            invoiceService.saveGeneratedInvoice(invoice, "GST_INVOICE", status, null);

            // 🆕 Generate the premium GST PDF
            java.io.File pdfFile = pdfService.generateGstInvoice(invoice);

            // 🆕 Auto-open the PDF
            if (pdfFile != null && pdfFile.exists()) {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(pdfFile);
                }
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText(null);
            alert.setContentText("Invoice " + invoice.getInvoiceNo() + " has been generated and downloaded.");
            alert.showAndWait();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to generate invoice");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
            e.printStackTrace();
        }
    }

    @FXML
    private void handleAddItem() {
        System.out.println("Adding Item...");
    }

    @FXML
    private void handleImportJob() {
        System.out.println("Importing Job...");
    }

    @FXML
    private void handleClearAll() {
        System.out.println("Clearing All...");
    }

    @FXML
    private void handleEditTerms() {
        TextArea area = new TextArea();
        area.setWrapText(true);
        area.setText(termsText.get() != null ? termsText.get() : "");
        area.textProperty().addListener((obs, oldV, newV) -> termsText.set(newV));
        area.getStyleClass().addAll("text-area");
        area.setPrefRowCount(10);

        BorderPane root = new BorderPane(area);
        root.setStyle("-fx-padding: 12; -fx-background-color: #FAF6F0;");

        Button btnClose = new Button("Done");
        btnClose.getStyleClass().addAll("action-btn-custom", "btn-terracotta");
        HBox footer = new HBox(btnClose);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-padding: 10 0 0 0;");
        root.setBottom(footer);

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Terms & Conditions");
        Scene scene = new Scene(root, 720, 360);
        scene.getStylesheets().addAll(
                getClass().getResource("/css/theme.css").toExternalForm(),
                getClass().getResource("/css/view_job.css").toExternalForm(),
                getClass().getResource("/css/genrate_gst_invoice.css").toExternalForm()
        );
        dlg.setScene(scene);

        btnClose.setOnAction(e -> dlg.close());
        dlg.showAndWait();
    }

    public static final class ItemRow {
        private final String jobUuid;
        private final int slNo;
        private final String description;
        private final String qty;
        private final String unit;
        private final String rate;
        private final String taxable;
        private final StringProperty hsnSac = new SimpleStringProperty("—");
        private final StringProperty gstPercent = new SimpleStringProperty("—");
        private final StringProperty cgst = new SimpleStringProperty("—");
        private final StringProperty sgst = new SimpleStringProperty("—");
        private final StringProperty igst = new SimpleStringProperty("—");
        private final StringProperty total = new SimpleStringProperty("—");
        private final DoubleProperty taxableRaw = new SimpleDoubleProperty(0);
        private final DoubleProperty cgstRaw = new SimpleDoubleProperty(0);
        private final DoubleProperty sgstRaw = new SimpleDoubleProperty(0);
        private final DoubleProperty igstRaw = new SimpleDoubleProperty(0);
        private final DoubleProperty gstRateRaw = new SimpleDoubleProperty(DEFAULT_GST_RATE);

        private ItemRow(
                String jobUuid,
                int slNo,
                String description,
                String hsnSac,
                String qty,
                String unit,
                String rate,
                String taxable,
                String gstPercent,
                String cgst,
                String sgst,
                String igst,
                String total,
                double taxableRaw,
                double cgstRaw,
                double sgstRaw,
                double igstRaw,
                double gstRateRaw
        ) {
            this.jobUuid = jobUuid;
            this.slNo = slNo;
            this.description = description;
            this.qty = qty;
            this.unit = unit;
            this.rate = rate;
            this.taxable = taxable;
            this.hsnSac.set(hsnSac);
            this.gstPercent.set(gstPercent);
            this.cgst.set(cgst);
            this.sgst.set(sgst);
            this.igst.set(igst);
            this.total.set(total);
            this.taxableRaw.set(taxableRaw);
            this.cgstRaw.set(cgstRaw);
            this.sgstRaw.set(sgstRaw);
            this.igstRaw.set(igstRaw);
            this.gstRateRaw.set(gstRateRaw);
        }

        static ItemRow ofJob(int slNo, String jobUuid, String desc, String hsnSac, long qty, String unit, double rate, double taxable, Taxes taxes) {
            return ofJob(slNo, jobUuid, desc, hsnSac, qty, unit, rate, taxable, taxes, DEFAULT_GST_RATE);
        }

        static ItemRow ofJob(int slNo, String jobUuid, String desc, String hsnSac, long qty, String unit, double rate, double taxable, Taxes taxes, double gstRate) {
            String gstPct = String.format("%.0f%%", gstRate * 100.0);
            return new ItemRow(
                    jobUuid,
                    slNo,
                    desc != null ? desc : "",
                    hsnSac != null ? hsnSac : "—",
                    String.valueOf(qty),
                    unit != null ? unit : "PCS",
                    fmtMoney(rate),
                    fmtMoney(taxable),
                    gstPct,
                    taxes.cgst() > 0 ? fmtMoney(taxes.cgst()) : "—",
                    taxes.sgst() > 0 ? fmtMoney(taxes.sgst()) : "—",
                    taxes.igst() > 0 ? fmtMoney(taxes.igst()) : "—",
                    fmtMoney(taxes.total()),
                    taxable,
                    taxes.cgst(),
                    taxes.sgst(),
                    taxes.igst(),
                    gstRate
            );
        }

        private static String fmtMoney(double v) {
            return String.format("₹ %.2f", v);
        }

        public String getJobUuid() { return jobUuid; }
        public int getSlNo() { return slNo; }
        public String getDescription() { return description; }
        public String getHsnSac() { return hsnSac.get(); }
        public String getQty() { return qty; }
        public String getUnit() { return unit; }
        public String getRate() { return rate; }
        public String getTaxable() { return taxable; }
        public String getGstPercent() { return gstPercent.get(); }
        public String getCgst() { return cgst.get(); }
        public String getSgst() { return sgst.get(); }
        public String getIgst() { return igst.get(); }
        public String getTotal() { return total.get(); }

        public StringProperty hsnSacProperty() { return hsnSac; }
        public StringProperty gstPercentProperty() { return gstPercent; }
        public StringProperty cgstProperty() { return cgst; }
        public StringProperty sgstProperty() { return sgst; }
        public StringProperty igstProperty() { return igst; }
        public StringProperty totalProperty() { return total; }

        public void setHsnSac(String code) {
            hsnSac.set(code != null && !code.isBlank() ? code.trim() : "—");
        }

        public void recalcTaxes(double gstRate, boolean intraState) {
            gstRateRaw.set(gstRate);
            Taxes taxes = Taxes.compute(taxableRaw.get(), gstRate, intraState);
            String pct = String.format("%.0f%%", gstRate * 100.0);
            gstPercent.set(pct);
            cgstRaw.set(taxes.cgst());
            sgstRaw.set(taxes.sgst());
            igstRaw.set(taxes.igst());
            cgst.set(taxes.cgst() > 0 ? fmtMoney(taxes.cgst()) : "—");
            sgst.set(taxes.sgst() > 0 ? fmtMoney(taxes.sgst()) : "—");
            igst.set(taxes.igst() > 0 ? fmtMoney(taxes.igst()) : "—");
            total.set(fmtMoney(taxes.total()));
        }
    }

    public static final class HsnSummaryRow {
        private final String hsnSac;
        private final String taxable;
        private final String gstPercent;
        private final String cgst;
        private final String sgst;
        private final String igst;
        private final String totalTax;

        private HsnSummaryRow(String hsnSac, String taxable, String gstPercent, String cgst, String sgst, String igst, String totalTax) {
            this.hsnSac = hsnSac;
            this.taxable = taxable;
            this.gstPercent = gstPercent;
            this.cgst = cgst;
            this.sgst = sgst;
            this.igst = igst;
            this.totalTax = totalTax;
        }

        static HsnSummaryRow of(String hsnSac, double taxable, double gstRate, double cgst, double sgst, double igst) {
            double totalTax = round2(cgst + sgst + igst);
            String gstPct = String.format("%.0f%%", gstRate * 100.0);
            return new HsnSummaryRow(
                    hsnSac != null ? hsnSac : "—",
                    ItemRow.fmtMoney(taxable),
                    gstPct,
                    cgst > 0 ? ItemRow.fmtMoney(cgst) : "—",
                    sgst > 0 ? ItemRow.fmtMoney(sgst) : "—",
                    igst > 0 ? ItemRow.fmtMoney(igst) : "—",
                    ItemRow.fmtMoney(totalTax)
            );
        }

        public String getHsnSac() { return hsnSac; }
        public String getTaxable() { return taxable; }
        public String getGstPercent() { return gstPercent; }
        public String getCgst() { return cgst; }
        public String getSgst() { return sgst; }
        public String getIgst() { return igst; }
        public String getTotalTax() { return totalTax; }
    }
}
