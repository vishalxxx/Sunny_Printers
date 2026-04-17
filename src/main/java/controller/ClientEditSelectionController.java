package controller;
 
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import model.Client;
import repository.ClientRepository;

public class ClientEditSelectionController implements Initializable {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> riskCombo;
    @FXML private VBox clientListContainer;
    @FXML private Label lblShowingCount;
    @FXML private HBox paginationContainer;
    @FXML private TextField pageInput;

    private ClientRepository clientRepo = new ClientRepository();
    private ObservableList<Client> masterData = FXCollections.observableArrayList();
    private FilteredList<Client> filteredData;

    private int currentPage = 1;
    private final int rowsPerPage = 10;
    private int totalPages = 1;

    // Search constants
    private final double DEFAULT_SEARCH_WIDTH = 280.0;
    private final double EXPANDED_SEARCH_WIDTH = 400.0;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupComboBoxes();
        loadInitialData();
        setupFiltering();
        setupPageInput();
        setupSearchAnimation();
    }

    private void setupComboBoxes() {
        statusCombo.getItems().setAll("All Status", "Active", "Inactive");
        statusCombo.setValue("All Status");
        
        riskCombo.getItems().setAll("All Risk Level", "High", "Medium", "Low");
        riskCombo.setValue("All Risk Level");
    }

    private void loadInitialData() {
        new Thread(() -> {
            List<Client> clients = clientRepo.findAllSortedById();
            Platform.runLater(() -> {
                masterData.setAll(clients);
                filteredData = new FilteredList<>(masterData, p -> true);
                updatePaginationAndRender();
            });
        }).start();
    }

    private void setupFiltering() {
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            currentPage = 1;
            updateFilter();
        });
        statusCombo.valueProperty().addListener((obs, oldV, newV) -> {
            currentPage = 1;
            updateFilter();
        });
        riskCombo.valueProperty().addListener((obs, oldV, newV) -> {
            currentPage = 1;
            updateFilter();
        });
    }

    private void setupSearchAnimation() {
        searchField.focusedProperty().addListener((obs, oldV, isFocused) -> {
            if (isFocused) {
                animateSearchWidth(EXPANDED_SEARCH_WIDTH);
            } else {
                animateSearchWidth(DEFAULT_SEARCH_WIDTH);
            }
        });
    }

    private void animateSearchWidth(double targetWidth) {
        Timeline timeline = new Timeline();
        KeyValue kv = new KeyValue(searchField.prefWidthProperty(), targetWidth, Interpolator.EASE_OUT);
        KeyFrame kf = new KeyFrame(Duration.millis(300), kv);
        timeline.getKeyFrames().add(kf);
        timeline.play();
    }

    private void setupPageInput() {
        pageInput.setOnAction(e -> {
            try {
                int targetPage = Integer.parseInt(pageInput.getText());
                goToPage(targetPage);
                pageInput.clear();
            } catch (NumberFormatException ex) {
                pageInput.clear();
            }
        });
    }

    private void updateFilter() {
        if (filteredData == null) return;
        
        String search = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String status = statusCombo.getValue();
        String risk = riskCombo.getValue();

        filteredData.setPredicate(client -> {
            boolean matchesSearch = search.isEmpty() || 
                (client.getBusinessName() != null && client.getBusinessName().toLowerCase().contains(search)) ||
                (client.getClientName() != null && client.getClientName().toLowerCase().contains(search)) ||
                (client.getPhone() != null && client.getPhone().contains(search)) ||
                (client.getEmail() != null && client.getEmail().toLowerCase().contains(search));

            boolean matchesStatus = status.equals("All Status") || 
                (client.getSegment() != null && client.getSegment().equalsIgnoreCase(status));

            boolean matchesRisk = risk.equals("All Risk Level"); 
            if (!matchesRisk) {
                 String mockRisk = "Low";
                 if (client.getId() % 3 == 0) mockRisk = "High";
                 else if (client.getId() % 2 == 0) mockRisk = "Medium";
                 matchesRisk = mockRisk.equalsIgnoreCase(risk);
            }
            
            return matchesSearch && matchesStatus && matchesRisk;
        });
        
        updatePaginationAndRender();
    }

    private void updatePaginationAndRender() {
        if (filteredData == null) return;
        
        int totalItems = filteredData.size();
        totalPages = (int) Math.ceil((double) totalItems / rowsPerPage);
        if (totalPages == 0) totalPages = 1;
        
        if (currentPage > totalPages) currentPage = totalPages;
        if (currentPage < 1) currentPage = 1;

        renderList();
        renderPaginationUI();
        updateShowingCount();
    }

    private void goToPage(int page) {
        if (page >= 1 && page <= totalPages) {
            currentPage = page;
            updatePaginationAndRender();
        }
    }

    private void updateShowingCount() {
        if (filteredData != null) {
            int start = (currentPage - 1) * rowsPerPage + 1;
            int end = Math.min(currentPage * rowsPerPage, filteredData.size());
            if (filteredData.isEmpty()) start = 0;
            lblShowingCount.setText(start + "-" + end + " of " + filteredData.size());
        }
    }

    private void renderPaginationUI() {
        paginationContainer.getChildren().clear();
        
        // Prev Button
        Button prevBtn = new Button("‹");
        prevBtn.getStyleClass().add("page-btn");
        prevBtn.setDisable(currentPage == 1);
        prevBtn.setOnAction(e -> goToPage(currentPage - 1));
        paginationContainer.getChildren().add(prevBtn);

        // Logic for page numbers (show few around current)
        int startPage = Math.max(1, currentPage - 1);
        int endPage = Math.min(totalPages, startPage + 2);
        if (endPage - startPage < 2) startPage = Math.max(1, endPage - 2);

        for (int i = startPage; i <= endPage; i++) {
            final int p = i;
            Button pBtn = new Button(String.valueOf(i));
            pBtn.getStyleClass().add("page-btn");
            if (i == currentPage) pBtn.getStyleClass().add("page-btn-active");
            pBtn.setOnAction(e -> goToPage(p));
            paginationContainer.getChildren().add(pBtn);
        }

        if (endPage < totalPages) {
            Label dot = new Label("...");
            dot.getStyleClass().add("footer-text");
            paginationContainer.getChildren().add(dot);
            
            Button lastBtn = new Button(String.valueOf(totalPages));
            lastBtn.getStyleClass().add("page-btn");
            lastBtn.setOnAction(e -> goToPage(totalPages));
            paginationContainer.getChildren().add(lastBtn);
        }

        // Next Button
        Button nextBtn = new Button("›");
        nextBtn.getStyleClass().add("page-btn");
        nextBtn.setDisable(currentPage == totalPages);
        nextBtn.setOnAction(e -> goToPage(currentPage + 1));
        paginationContainer.getChildren().add(nextBtn);
    }

    private void renderList() {
        clientListContainer.getChildren().clear();
        
        int start = (currentPage - 1) * rowsPerPage;
        int end = Math.min(start + rowsPerPage, filteredData.size());
        
        for (int i = start; i < end; i++) {
            clientListContainer.getChildren().add(createClientRow(filteredData.get(i)));
        }
    }

    private HBox createClientRow(Client client) {
        HBox row = new HBox();
        row.getStyleClass().add("client-card");
        VBox.setMargin(row, new Insets(0, 0, 10, 0));

        // Column 1: Client
        HBox col1 = new HBox(15);
        col1.setMinWidth(220); col1.setPrefWidth(220); col1.setAlignment(Pos.CENTER_LEFT);
        
        StackPane iconContainer = new StackPane();
        iconContainer.getStyleClass().add("card-icon-container");
        Region icon = new Region();
        icon.setStyle("-fx-shape: 'M20 8h-3V4H3c-1.1 0-2 .9-2 2v11h2c0 1.66 1.34 3 3 3s3-1.34 3-3h6c0 1.66 1.34 3 3 3s3-1.34 3-3h2v-5l-3-4zM6 18.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm13.5-9l1.96 2.5H17V9.5h2.5zm-1.5 9c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z'; -fx-background-color: #A0522D; -fx-min-width: 20; -fx-max-width: 20; -fx-min-height: 20; -fx-max-height: 20;");
        iconContainer.getChildren().add(icon);

        VBox names = new VBox(2);
        Label bizLabel = new Label(client.getBusinessName()); bizLabel.getStyleClass().add("client-business-name");
        Label clLabel = new Label(client.getClientName()); clLabel.getStyleClass().add("client-sub-text");
        names.getChildren().addAll(bizLabel, clLabel);
        
        col1.getChildren().addAll(iconContainer, names);

        // Column 2: Contact
        HBox col2 = new HBox();
        col2.setMinWidth(160); col2.setPrefWidth(160); col2.setAlignment(Pos.CENTER);
        VBox contactBox = new VBox(2); contactBox.setAlignment(Pos.CENTER);
        Label nameLabel = new Label(client.getClientName()); nameLabel.getStyleClass().add("phone-text");
        Label roleLabel = new Label("Primary Contact"); roleLabel.getStyleClass().add("client-sub-text");
        contactBox.getChildren().addAll(nameLabel, roleLabel);
        col2.getChildren().add(contactBox);

        // Column 3: Phone
        HBox col3 = new HBox();
        col3.setMinWidth(150); col3.setPrefWidth(150); col3.setAlignment(Pos.CENTER_LEFT);
        VBox phoneBox = new VBox(2); phoneBox.setAlignment(Pos.CENTER_LEFT);
        Label p1 = new Label(client.getPhone()); p1.getStyleClass().add("phone-text");
        Label p2 = new Label(client.getAltPhone()); p2.getStyleClass().add("client-sub-text");
        phoneBox.getChildren().addAll(p1, p2);
        col3.getChildren().add(phoneBox);

        // Column 4: Email
        HBox col4 = new HBox();
        col4.setMinWidth(200); col4.setPrefWidth(200); col4.setAlignment(Pos.CENTER_LEFT);
        Label emailLabel = new Label(client.getEmail()); emailLabel.getStyleClass().add("email-text");
        col4.getChildren().add(emailLabel);

        // Column 5: Risk
        HBox col5 = new HBox(8);
        col5.setMinWidth(100); col5.setPrefWidth(100); col5.setAlignment(Pos.CENTER_LEFT);
        Region dot = new Region(); dot.getStyleClass().add("risk-dot");
        String riskLvl = "Low"; String riskClr = "#4A7C59";
        if (client.getId() % 3 == 0) { riskLvl = "High"; riskClr = "#B45309"; }
        else if (client.getId() % 2 == 0) { riskLvl = "Medium"; riskClr = "#DC9D38"; }
        dot.setStyle("-fx-background-color: " + riskClr + ";");
        Label riskLabel = new Label(riskLvl);
        riskLabel.setStyle("-fx-text-fill: " + riskClr + "; -fx-font-weight: 800; -fx-font-size: 14;");
        col5.getChildren().addAll(dot, riskLabel);

        // Column 6: Status
        HBox col6 = new HBox();
        col6.setMinWidth(100); col6.setPrefWidth(100); col6.setAlignment(Pos.CENTER);
        Label statusLabel = new Label(client.getSegment());
        if ("Inactive".equalsIgnoreCase(client.getSegment())) statusLabel.getStyleClass().add("status-pill-inactive");
        else statusLabel.getStyleClass().add("status-label");
        col6.getChildren().add(statusLabel);

        // Column 7: Actions
        HBox col7 = new HBox();
        col7.setMinWidth(150); col7.setPrefWidth(150); col7.setAlignment(Pos.CENTER);
        Button editBtn = new Button("Edit Profile");
        editBtn.getStyleClass().add("btn-select-main");
        editBtn.setOnAction(e -> MainController.getInstance().loadEditClient(client));
        col7.getChildren().add(editBtn);

        row.getChildren().addAll(col1, col2, col3, col4, col5, col6, col7);
        return row;
    }

    @FXML
    private void handleRootClick() {
        if (searchField != null) {
            searchField.getParent().requestFocus();
        }
    }
}
