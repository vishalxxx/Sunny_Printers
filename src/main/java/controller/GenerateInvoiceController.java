package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.Region;
import java.time.LocalDate;

public class GenerateInvoiceController {

    @FXML private Button breadcrumbBackBtn;
    @FXML private TableView<JobItem> jobsTable;
    @FXML private TableColumn<JobItem, Boolean> selectCol;
    @FXML private TableColumn<JobItem, String> jobNameCol;
    @FXML private TableColumn<JobItem, String> dateCol;
    @FXML private TableColumn<JobItem, String> qtyCol;
    @FXML private TableColumn<JobItem, String> rateCol;
    @FXML private TableColumn<JobItem, String> totalCol;
    @FXML private CheckBox selectAllCheckbox;

    @FXML private TextField jobSearchField;
    @FXML private TextField clientSearchField;
    @FXML private TextField invoiceNoField;
    @FXML private ComboBox<String> termsCombo;
    @FXML private DatePicker invoiceDatePicker;
    @FXML private DatePicker dueDatePicker;
    @FXML private TextArea notesArea;

    @FXML private Label itemCountLabel;
    @FXML private Label totalAmountLabel;

    @FXML
    public void initialize() {
        setupTable();
        setupFields();
        loadDummyData();
    }

    private void setupTable() {
        // Select column with checkboxes
        selectCol.setCellValueFactory(data -> data.getValue().selectedProperty());
        selectCol.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setOnAction(e -> {
                    JobItem item = getTableView().getItems().get(getIndex());
                    item.setSelected(checkBox.isSelected());
                    updateSummary();
                });
            }
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(item);
                    setGraphic(checkBox);
                }
            }
        });

        jobNameCol.setCellValueFactory(data -> data.getValue().nameProperty());
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
                } else {
                    JobItem job = getTableView().getItems().get(getIndex());
                    title.setText(item);
                    subtitle.setText(job.getSubtitle());
                    setGraphic(container);
                }
            }
        });

        dateCol.setCellValueFactory(data -> data.getValue().dateProperty());
        qtyCol.setCellValueFactory(data -> data.getValue().qtyProperty());
        rateCol.setCellValueFactory(data -> data.getValue().rateProperty());
        
        totalCol.setCellValueFactory(data -> data.getValue().totalProperty());
        totalCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setStyle("-fx-font-weight: 800; -fx-alignment: CENTER_RIGHT;");
                }
            }
        });

        selectAllCheckbox.setOnAction(e -> {
            boolean selected = selectAllCheckbox.isSelected();
            jobsTable.getItems().forEach(item -> item.setSelected(selected));
            jobsTable.refresh();
            updateSummary();
        });
    }

    private void setupFields() {
        termsCombo.setItems(FXCollections.observableArrayList("Net 30", "Net 15", "Due on Receipt", "Custom"));
        termsCombo.setValue("Net 30");
        invoiceDatePicker.setValue(LocalDate.now());
        dueDatePicker.setValue(LocalDate.now().plusDays(30));
    }

    private void loadDummyData() {
        ObservableList<JobItem> items = FXCollections.observableArrayList(
            new JobItem("Quarterly Product Catalog - 64pg", "Offset Print • Matte Finish", "Oct 12, 2023", "500 units", "₹ 12.50", "₹ 6,250.00"),
            new JobItem("Executive Business Cards - Premium", "Spot UV • 400gsm Cotton", "Oct 14, 2023", "200 units", "₹ 4.30", "₹ 860.00"),
            new JobItem("Promotional Flyers - A5", "Digital Print • Glossy", "Oct 15, 2023", "700 units", "₹ 1.00", "₹ 700.00"),
            new JobItem("Banner Vinyl Print - 6x4ft", "Outdoor Quality", "Oct 16, 2023", "1 unit", "₹ 1,200.00", "₹ 1,200.00")
        );
        items.get(0).setSelected(true);
        items.get(1).setSelected(true);
        items.get(2).setSelected(true);
        jobsTable.setItems(items);
        updateSummary();
    }

    private void updateSummary() {
        long count = jobsTable.getItems().stream().filter(JobItem::isSelected).count();
        double total = jobsTable.getItems().stream()
                .filter(JobItem::isSelected)
                .mapToDouble(item -> {
                    String cleanTotal = item.getTotal().replace("₹", "").replace(",", "").trim();
                    try { return Double.parseDouble(cleanTotal); } catch (Exception e) { return 0.0; }
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
 
        public SimpleBooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean val) { selected.set(val); }
 
        public SimpleStringProperty nameProperty() { return name; }
        public String getSubtitle() { return subtitle.get(); }
        public SimpleStringProperty dateProperty() { return date; }
        public SimpleStringProperty qtyProperty() { return qty; }
        public SimpleStringProperty rateProperty() { return rate; }
        public SimpleStringProperty totalProperty() { return total; }
        public String getTotal() { return total.get(); }
    }
}
