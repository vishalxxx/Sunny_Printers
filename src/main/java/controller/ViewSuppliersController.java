package controller;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import model.Supplier;
import service.SupplierService;
import utils.Toast;

public class ViewSuppliersController implements Initializable {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilterComboBox;
    @FXML private ListView<Supplier> supplierListView;
    @FXML private HBox breadcrumbContainer;
    
    @FXML private Label lblTotalSuppliers;
    @FXML private Label lblActiveSuppliers;
    @FXML private Label lblShowingCount;
    @FXML private HBox paginationContainer;

    private final SupplierService supplierService = new SupplierService();
    private final ObservableList<Supplier> masterList = FXCollections.observableArrayList();
    private FilteredList<Supplier> filteredList;
    private SortedList<Supplier> sortedList;

    private int currentPage = 1;
    private final int itemsPerPage = 8; // Compact high-density list layout

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Populate breadcrumbs
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null, () -> handleBack());

        // Load data from DB
        loadSuppliersData();

        // Bind ComboBox Filters
        typeFilterComboBox.setItems(FXCollections.observableArrayList(
            "All Types", "Paper", "CTP", "Binding", "Lamination", "Ink", "Plates", "Packaging", "Digital", "Other"
        ));
        typeFilterComboBox.setValue("All Types");
        typeFilterComboBox.setOnAction(e -> applyFiltersAndPagination());

        // Bind Search Field Filters
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFiltersAndPagination());

        // Set Custom Cell Factory for Card view consistency
        supplierListView.setCellFactory(listView -> new SupplierCardCell());
    }

    private void loadSuppliersData() {
        masterList.setAll(supplierService.getAllSuppliers());
        
        filteredList = new FilteredList<>(masterList, p -> true);
        sortedList = new SortedList<>(filteredList, (s1, s2) -> s1.getName().compareToIgnoreCase(s2.getName()));
        
        applyFiltersAndPagination();
        updateKpis();
    }

    private void applyFiltersAndPagination() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        String selectedType = typeFilterComboBox.getValue() == null ? "All Types" : typeFilterComboBox.getValue();

        filteredList.setPredicate(s -> {
            if (s == null) return false;
            
            boolean matchesSearch = keyword.isEmpty() 
                || s.getName().toLowerCase().contains(keyword)
                || (s.getbusinessName() != null && s.getbusinessName().toLowerCase().contains(keyword))
                || (s.getPhone() != null && s.getPhone().contains(keyword))
                || (s.getMobile() != null && s.getMobile().contains(keyword))
                || (s.getGstNumber() != null && s.getGstNumber().toLowerCase().contains(keyword));

            boolean matchesType = selectedType.equals("All Types") 
                || (s.getType() != null && s.getType().equalsIgnoreCase(selectedType));

            return matchesSearch && matchesType;
        });

        currentPage = 1;
        updatePagination();
    }

    private void updatePagination() {
        int totalItems = filteredList.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));

        if (currentPage > totalPages) currentPage = totalPages;

        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);

        ObservableList<Supplier> pageItems = FXCollections.observableArrayList(sortedList.subList(startIndex, endIndex));
        supplierListView.setItems(pageItems);

        if (paginationContainer != null) {
            paginationContainer.getChildren().clear();

            Button btnPrev = new Button("<");
            btnPrev.getStyleClass().add("page-btn");
            btnPrev.setDisable(currentPage == 1);
            btnPrev.setOnAction(e -> { currentPage--; updatePagination(); });
            paginationContainer.getChildren().add(btnPrev);

            int startPage = Math.max(1, currentPage - 1);
            int endPage = Math.min(totalPages, startPage + 2);
            if (endPage - startPage < 2 && startPage > 1) {
                startPage = Math.max(1, endPage - 2);
            }

            for (int i = startPage; i <= endPage; i++) {
                Button btnPage = new Button(String.valueOf(i));
                btnPage.getStyleClass().add("page-btn");
                if (i == currentPage) {
                    btnPage.getStyleClass().add("page-btn-active");
                }
                final int pageNum = i;
                btnPage.setOnAction(e -> { currentPage = pageNum; updatePagination(); });
                paginationContainer.getChildren().add(btnPage);
            }

            Button btnNext = new Button(">");
            btnNext.getStyleClass().add("page-btn");
            btnNext.setDisable(currentPage == totalPages);
            btnNext.setOnAction(e -> { currentPage++; updatePagination(); });
            paginationContainer.getChildren().add(btnNext);
        }

        if (lblShowingCount != null) {
            lblShowingCount.setText(totalItems == 0 ? "0 of 0" : (startIndex + 1) + "-" + endIndex + " of " + totalItems);
        }
    }

    private void updateKpis() {
        int total = masterList.size();
        lblTotalSuppliers.setText(String.valueOf(total));
        lblActiveSuppliers.setText(String.valueOf(total)); // Assuming registered suppliers are active
    }

    public void refresh() {
        loadSuppliersData();
    }

    @FXML
    private void handleBack() {
        MainController.getInstance().handleBack(null);
    }

    @FXML
    private void handleBtnAddSupplier() {
        MainController.getInstance().loadAddSupplier();
    }

    // --- Custom Supplier Card Cell Factory ---
    class SupplierCardCell extends ListCell<Supplier> {
        private final HBox root = new HBox(15);
        private final Region icon = new Region();
        private final StackPane iconBox = new StackPane(icon);
        
        private final Label lblName = new Label();
        private final Label lblBusiness = new Label();
        private final VBox nameBox = new VBox(2, lblName, lblBusiness);
        
        private final Label lblType = new Label();
        private final StackPane typeBox = new StackPane(lblType);
        
        private final Label lblDeleted = new Label("DELETED");
        private final StackPane deletedBox = new StackPane(lblDeleted);
        
        private final Label lblPhoneVal = new Label();
        private final VBox phoneBox = new VBox(2, new Label("PHONE"), lblPhoneVal);
        
        private final Label lblGstVal = new Label();
        private final VBox gstBox = new VBox(2, new Label("GSTIN"), lblGstVal);
        
        private final Label lblAddressVal = new Label();
        private final VBox addressBox = new VBox(2, new Label("ADDRESS"), lblAddressVal);
        
        private final Button btnEdit = new Button("Edit Profile");
        private final Button btnDelete = new Button("Delete");
        private final Button btnRevive = new Button("Revive");

        public SupplierCardCell() {
            super();
            
            root.getStyleClass().add("client-card-cell");
            root.setAlignment(Pos.CENTER_LEFT);
            root.setPadding(new Insets(14, 20, 14, 20));

            iconBox.getStyleClass().add("client-icon-box");
            icon.getStyleClass().add("client-icon");
            
            lblName.getStyleClass().add("client-name");
            lblBusiness.getStyleClass().add("client-primary");
            nameBox.setMinWidth(150);
            nameBox.setPrefWidth(180);
            nameBox.setAlignment(Pos.CENTER_LEFT);
            
            lblType.getStyleClass().add("client-status-text");
            typeBox.getStyleClass().addAll("client-status-box", "status-preferred");
            typeBox.setMinWidth(90);
            
            lblDeleted.setStyle("-fx-text-fill: #e53935; -fx-font-weight: bold; -fx-font-size: 11px;");
            deletedBox.setStyle("-fx-background-color: #ffebee; -fx-background-radius: 4; -fx-padding: 4 8 4 8;");
            deletedBox.setMinWidth(80);
            deletedBox.setVisible(false);
            deletedBox.setManaged(false);
            
            ((Label)phoneBox.getChildren().get(0)).getStyleClass().add("client-stat-label");
            lblPhoneVal.getStyleClass().add("client-stat-value");
            phoneBox.setPrefWidth(100);
            
            ((Label)gstBox.getChildren().get(0)).getStyleClass().add("client-stat-label");
            lblGstVal.getStyleClass().add("client-stat-value");
            gstBox.setPrefWidth(120);
            
            ((Label)addressBox.getChildren().get(0)).getStyleClass().add("client-stat-label");
            lblAddressVal.getStyleClass().add("client-stat-value");
            addressBox.setPrefWidth(180);
            
            btnEdit.getStyleClass().add("action-btn-ghost");
            btnEdit.setOnAction(e -> {
                Supplier s = getItem();
                if (s != null) {
                    MainController.getInstance().loadEditSupplier(s);
                }
            });

            btnDelete.getStyleClass().add("view-clients-delete-btn");
            btnDelete.setOnAction(e -> confirmAndDeleteSupplier(getItem()));

            btnRevive.getStyleClass().add("action-btn-ghost");
            btnRevive.setOnAction(e -> confirmAndReviveSupplier(getItem()));

            HBox actionButtons = new HBox(8, btnEdit, btnRevive, btnDelete);
            actionButtons.setAlignment(Pos.CENTER_RIGHT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            root.getChildren().addAll(iconBox, nameBox, spacer, deletedBox, typeBox, phoneBox, gstBox, addressBox, actionButtons);
        }

        @Override
        protected void updateItem(Supplier supplier, boolean empty) {
            super.updateItem(supplier, empty);
            if (empty || supplier == null) {
                setGraphic(null);
            } else {
                String biz = supplier.getbusinessName() == null || supplier.getbusinessName().isEmpty() ? "No Business Name" : supplier.getbusinessName();
                lblName.setText(biz);
                String code = supplier.getSupplierCode();
                if (code == null || code.isBlank()) {
                    code = supplier.getUuid() != null && supplier.getUuid().length() > 8 ? supplier.getUuid().substring(0, 8).toUpperCase() : supplier.getUuid();
                }
                lblBusiness.setText(code + " | " + supplier.getName());
                if (supplier.isDeleted()) {
                    deletedBox.setVisible(true);
                    deletedBox.setManaged(true);
                    btnDelete.setVisible(false);
                    btnDelete.setManaged(false);
                    btnRevive.setVisible(true);
                    btnRevive.setManaged(true);
                } else {
                    deletedBox.setVisible(false);
                    deletedBox.setManaged(false);
                    btnDelete.setVisible(true);
                    btnDelete.setManaged(true);
                    btnRevive.setVisible(false);
                    btnRevive.setManaged(false);
                }
                lblType.setText(supplier.getType());
                lblType.setStyle("");
                String primaryPhone = (supplier.getMobile() != null && !supplier.getMobile().isEmpty()) ? supplier.getMobile() : (supplier.getPhone() != null ? supplier.getPhone() : "");
                lblPhoneVal.setText(primaryPhone.isEmpty() ? "N/A" : primaryPhone);
                lblGstVal.setText(supplier.getGstNumber().isEmpty() ? "N/A" : supplier.getGstNumber());
                lblAddressVal.setText(supplier.getAddress().isEmpty() ? "No Address Details" : supplier.getAddress());
                
                setGraphic(root);
            }
        }
    }

    private void confirmAndDeleteSupplier(Supplier s) {
        if (s == null) return;
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Supplier");
        alert.setHeaderText("Delete " + s.getName() + "?");
        alert.setContentText("Are you sure you want to permanently delete this supplier? This action cannot be undone.");
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        // Apply theme stylesheets
        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
        pane.getStyleClass().add("atelier-alert");
        
        Region icon = new Region();
        icon.getStyleClass().add("alert-icon-warning");
        alert.setGraphic(icon);

        java.util.Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                supplierService.deleteSupplier(s.getUuid());
                masterList.remove(s);
                applyFiltersAndPagination();
                updateKpis();
                Toast.show((Stage) supplierListView.getScene().getWindow(), "Supplier deleted successfully!");
            } catch (Exception e) {
                e.printStackTrace();
                Toast.show((Stage) supplierListView.getScene().getWindow(), "Failed to delete supplier.");
            }
        }
    }

    private void confirmAndReviveSupplier(Supplier s) {
        if (s == null) return;
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Revive Supplier");
        alert.setHeaderText("Revive " + s.getName() + "?");
        alert.setContentText("This will restore the supplier making it visible to all users.");
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
        pane.getStyleClass().add("atelier-alert");
        
        java.util.Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                supplierService.reviveSupplier(s.getUuid());
                s.setDeleted(false);
                s.setActive(true);
                supplierListView.refresh();
                updateKpis();
                Toast.show((Stage) supplierListView.getScene().getWindow(), "Supplier revived successfully!");
            } catch (Exception e) {
                e.printStackTrace();
                Toast.show((Stage) supplierListView.getScene().getWindow(), "Failed to revive supplier.");
            }
        }
    }
}
