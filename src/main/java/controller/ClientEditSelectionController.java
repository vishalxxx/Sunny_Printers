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
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import model.Client;
import repository.ClientRepository;
import utils.NavigationManager;

public class ClientEditSelectionController implements Initializable {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> riskCombo;
    @FXML private Button breadcrumbBackBtn;
    @FXML private VBox clientListContainer;
    @FXML private Label lblShowingCount;
    @FXML private HBox paginationContainer;
    /** Optional in FXML; created in code if missing (pagination row rebuilds children). */
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
        if (breadcrumbBackBtn != null) {
            breadcrumbBackBtn.visibleProperty().bind(NavigationManager.getInstance().canGoBackProperty());
            breadcrumbBackBtn.managedProperty().bind(breadcrumbBackBtn.visibleProperty());
        }
        setupComboBoxes();
        ensurePageInputField();
        loadInitialData();
        setupFiltering();
        setupPageInput();
        setupSearchAnimation();
    }

    @FXML
    private void handleBack(javafx.event.Event e) {
        MainController.getInstance().handleBack(e);
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

    private void ensurePageInputField() {
        if (pageInput != null) {
            return;
        }
        pageInput = new TextField();
        pageInput.setPromptText("Page");
        pageInput.setPrefColumnCount(4);
        pageInput.setMaxWidth(72);
        pageInput.getStyleClass().add("goto-field");
    }

    private void setupPageInput() {
        if (pageInput == null) {
            return;
        }
        pageInput.setOnAction(e -> {
            try {
                int targetPage = Integer.parseInt(pageInput.getText().trim());
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
        Button prevBtn = new Button("<");
        prevBtn.getStyleClass().add("page-btn");
        prevBtn.setDisable(currentPage == 1);
        prevBtn.setOnAction(e -> goToPage(currentPage - 1));
        paginationContainer.getChildren().add(prevBtn);

        // Logic to show a max of 3 pages (sliding window) - matching view_client.css style
        int startPage = Math.max(1, currentPage - 1);
        int endPage = Math.min(totalPages, startPage + 2);
        if (endPage - startPage < 2 && startPage > 1) {
            startPage = Math.max(1, endPage - 2);
        }

        for (int i = startPage; i <= endPage; i++) {
            final int p = i;
            Button pBtn = new Button(String.valueOf(i));
            pBtn.getStyleClass().add("page-btn");
            if (i == currentPage) pBtn.getStyleClass().add("page-btn-active");
            pBtn.setOnAction(e -> goToPage(p));
            paginationContainer.getChildren().add(pBtn);
        }

        // Next Button
        Button nextBtn = new Button(">");
        nextBtn.getStyleClass().add("page-btn");
        nextBtn.setDisable(currentPage == totalPages);
        nextBtn.setOnAction(e -> goToPage(currentPage + 1));
        paginationContainer.getChildren().add(nextBtn);

        // Jump to page label
        Label jumpLabel = new Label("Go to:");
        jumpLabel.setStyle("-fx-text-fill: #A79F99; -fx-font-size: 11px; -fx-padding: 0 0 0 10;");
        paginationContainer.getChildren().add(jumpLabel);
        
        // Ensure pageInput is managed within the container if it was separate in FXML
        // But the user wants the exact type on view client screen which has it inside paginationContainer
        if (pageInput != null) {
            pageInput.getStyleClass().add("goto-field");
            if (!paginationContainer.getChildren().contains(pageInput)) {
                paginationContainer.getChildren().add(pageInput);
            }
        }
    }

    private void renderList() {
        clientListContainer.getChildren().clear();
        
        int start = (currentPage - 1) * rowsPerPage;
        int end = Math.min(start + rowsPerPage, filteredData.size());
        
        for (int i = start; i < end; i++) {
            clientListContainer.getChildren().add(createClientRow(filteredData.get(i)));
        }
    }

    private GridPane createClientRow(Client client) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("client-card");
        grid.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(grid, new Insets(0, 0, 10, 0));

        // Define Column Constraints
        ColumnConstraints col1 = new ColumnConstraints(250);
        ColumnConstraints col2 = new ColumnConstraints(200);
        ColumnConstraints col3 = new ColumnConstraints(120);
        ColumnConstraints col4 = new ColumnConstraints(220);
        ColumnConstraints col5 = new ColumnConstraints(120);
        ColumnConstraints col6 = new ColumnConstraints(100);
        ColumnConstraints col7 = new ColumnConstraints(150);
        col7.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(col1, col2, col3, col4, col5, col6, col7);

        // Column 0: Client Info
        HBox clientBox = new HBox(15);
        clientBox.setAlignment(Pos.CENTER_LEFT);
        
        StackPane iconContainer = new StackPane();
        iconContainer.getStyleClass().add("card-icon-container");
        Region icon = new Region();
        icon.setStyle("-fx-shape: 'M20 8h-3V4H3c-1.1 0-2 .9-2 2v11h2c0 1.66 1.34 3 3 3s3-1.34 3-3h6c0 1.66 1.34 3 3 3s3-1.34 3-3h2v-5l-3-4zM6 18.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm13.5-9l1.96 2.5H17V9.5h2.5zm-1.5 9c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z'; -fx-background-color: #A0522D; -fx-min-width: 20; -fx-max-width: 20; -fx-min-height: 20; -fx-max-height: 20;");
        iconContainer.getChildren().add(icon);

        VBox names = new VBox(2);
        Label bizLabel = new Label(client.getBusinessName()); bizLabel.getStyleClass().add("client-business-name");
        Label clLabel = new Label(client.getClientName()); clLabel.getStyleClass().add("client-sub-text");
        names.getChildren().addAll(bizLabel, clLabel);
        clientBox.getChildren().addAll(iconContainer, names);
        grid.add(clientBox, 0, 0);

        // Column 1: Primary Contact
        VBox contactBox = new VBox(2); contactBox.setAlignment(Pos.CENTER_LEFT);
        Label nameLabel = new Label(client.getClientName()); nameLabel.getStyleClass().add("phone-text");
        Label roleLabel = new Label("Primary Contact"); roleLabel.getStyleClass().add("client-sub-text");
        contactBox.getChildren().addAll(nameLabel, roleLabel);
        grid.add(contactBox, 1, 0);

        // Column 2: Phone
        VBox phoneBox = new VBox(2); phoneBox.setAlignment(Pos.CENTER);
        Label p1 = new Label(client.getPhone()); p1.getStyleClass().add("phone-text");
        Label p2 = new Label(client.getAltPhone()); p2.getStyleClass().add("client-sub-text");
        phoneBox.getChildren().addAll(p1, p2);
        grid.add(phoneBox, 2, 0);

        // Column 3: Email
        Label emailLabel = new Label(client.getEmail()); emailLabel.getStyleClass().add("email-text");
        emailLabel.setAlignment(Pos.CENTER);
        emailLabel.setMaxWidth(Double.MAX_VALUE);
        grid.add(emailLabel, 3, 0);

        // Column 4: Risk
        HBox riskBox = new HBox(8);
        riskBox.setAlignment(Pos.CENTER_LEFT);
        Region dot = new Region(); dot.getStyleClass().add("risk-dot");
        String riskLvl = "Low"; String riskClr = "#4A7C59";
        if (client.getId() % 3 == 0) { riskLvl = "High"; riskClr = "#B45309"; }
        else if (client.getId() % 2 == 0) { riskLvl = "Medium"; riskClr = "#DC9D38"; }
        dot.setStyle("-fx-background-color: " + riskClr + ";");
        Label riskLabel = new Label(riskLvl);
        riskLabel.setStyle("-fx-text-fill: " + riskClr + "; -fx-font-weight: 800; -fx-font-size: 14;");
        riskLabel.setAlignment(Pos.CENTER_LEFT);
        riskBox.getChildren().addAll(dot, riskLabel);
        grid.add(riskBox, 4, 0);

        // Column 5: Status
        Label statusLabel = new Label(client.getSegment());
        statusLabel.setAlignment(Pos.CENTER_LEFT);
        if ("Inactive".equalsIgnoreCase(client.getSegment())) statusLabel.getStyleClass().add("status-pill-inactive");
        else statusLabel.getStyleClass().add("status-label");
        grid.add(statusLabel, 5, 0);

        // Column 6: Actions
        HBox actionBox = new HBox();
        actionBox.setAlignment(Pos.CENTER);
        Button editBtn = new Button("Edit Profile");
        editBtn.getStyleClass().add("btn-select-main");
        editBtn.setMinWidth(110);
        editBtn.setOnAction(e -> MainController.getInstance().loadEditClient(client));
        actionBox.getChildren().add(editBtn);
        grid.add(actionBox, 6, 0);

        return grid;
    }

    @FXML
    private void handleRootClick() {
        if (searchField != null) {
            searchField.getParent().requestFocus();
        }
    }
}
