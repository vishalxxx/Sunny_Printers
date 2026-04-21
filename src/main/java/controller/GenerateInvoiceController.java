package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import java.util.List;
import java.time.LocalDate;
import model.Client;
import service.ClientService;
import javafx.collections.transformation.FilteredList;
import javafx.collections.ListChangeListener;
import javafx.application.Platform;
import javafx.scene.Node;

public class GenerateInvoiceController {

    @FXML
    private Button breadcrumbBackBtn;
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
    public void initialize() {
        setupTable();
        if (jobsCard != null) {
            jobsCard.setMaxHeight(Region.USE_PREF_SIZE);
        }
        setupFields();
        setupClientCombo();
        setupJobSearch();
    }

    private final ClientService clientService = new ClientService();
    private final service.JobService jobService = new service.JobService();
    private final ObservableList<Client> masterClients = FXCollections.observableArrayList();
    private final ObservableList<JobItem> masterJobs = FXCollections.observableArrayList();
    private FilteredList<Client> filteredClients;
    private FilteredList<JobItem> filteredJobs;

    private void setupClientCombo() {
        if (clientComboBox == null)
            return;

        filteredClients = new FilteredList<>(masterClients, c -> true);
        clientComboBox.setItems(filteredClients);
        clientComboBox.setEditable(false);

        // Selection listener to load jobs
        clientComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                loadJobsForClient(newV);
            } else {
                masterJobs.clear();
                updateSummary();
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

        // Load data
        try {
            masterClients.setAll(clientService.getAllClients());
        } catch (Exception e) {
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

        jobsTable.getItems()
                .addListener((ListChangeListener<JobItem>) c -> Platform.runLater(this::refreshInvoiceLastRowStyles));

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

        // Size table to content; when empty, reserve body height so the placeholder
        // does not overlap the header
        jobsTable.setFixedCellSize(72);
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
        Platform.runLater(this::refreshInvoiceLastRowStyles);
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
        if (selectAllCheckbox == null || jobsTable == null) {
            return;
        }
        var showSelectAll = Bindings.greaterThan(Bindings.size(jobsTable.getItems()), 1);
        selectAllCheckbox.visibleProperty().bind(showSelectAll);
        selectAllCheckbox.managedProperty().bind(showSelectAll);
        jobsTable.getItems().addListener((ListChangeListener<JobItem>) c -> {
            if (jobsTable.getItems().size() <= 1) {
                selectAllCheckbox.setSelected(false);
            }
        });
    }

    private void setupFields() {
        if (invoiceNoField != null) {
            invoiceNoField.setEditable(false);
        }
        termsCombo.setItems(FXCollections.observableArrayList("Net 30", "Net 15", "Due on Receipt", "Custom"));
        termsCombo.setValue("Net 30");
        invoiceDatePicker.setValue(LocalDate.now());

        termsCombo.valueProperty().addListener((o, oldVal, newVal) -> applyTermsToDueDate());
        invoiceDatePicker.valueProperty().addListener((o, oldVal, newVal) -> {
            if (isAutoDueDateTerms(termsCombo.getValue())) {
                applyTermsToDueDate();
            }
        });

        applyTermsToDueDate();
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

    private void loadJobsForClient(Client client) {
        if (client == null)
            return;

        masterJobs.clear();
        try {
            System.out.println("DEBUG: Loading jobs for client ID: " + client.getId());
            List<model.Job> jobs = jobService.getCompletedJobsByClient(client.getId());
            System.out.println("DEBUG: Found " + jobs.size() + " jobs.");

            if (jobs.isEmpty()) {
                // Diagnostic: check if ANY jobs exist for this client
                List<model.Job> allJobs = jobService.getFullJobsByClientId(client.getId());
                System.out.println("DEBUG: Diagnostic - Total jobs (any status) for this client: " + allJobs.size());
                for (model.Job aj : allJobs) {
                    System.out.println("DEBUG: Job " + aj.getJobNo() + " has status: " + aj.getStatus());
                }
            }

            for (model.Job j : jobs) {
                String dateStr = j.getJobDate() != null ? j.getJobDate().toString() : "-";
                String totalStr = j.getJobTotal() != null ? String.format("₹ %,.2f", j.getJobTotal()) : "₹ 0.00";

                masterJobs.add(new JobItem(
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

        itemCountLabel.setText(count + (count == 1 ? " job" : " jobs"));
        totalAmountLabel.setText(String.format("₹ %,.2f", total));
    }

    @FXML
    private void handleBack() {
        MainController.getInstance().handleBack(null);
    }

    @FXML
    private void handlePreview() {
        System.out.println("Previewing Invoice...");
    }

    @FXML
    private void handleGenerate() {
        System.out.println("Generating Invoice...");
    }

    // Helper class for TableView
    public static class JobItem {
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
        private final SimpleStringProperty name;
        private final SimpleStringProperty subtitle;
        private final SimpleStringProperty date;
        private final SimpleStringProperty qty;
        private final SimpleStringProperty rate;
        private final SimpleStringProperty total;

        public JobItem(String name, String subtitle, String date, String qty, String rate, String total) {
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
