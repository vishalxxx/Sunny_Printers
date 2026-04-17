package controller;

import java.net.URL;
import java.util.Random;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import model.Client;
import repository.ClientRepository;
import utils.DeleteConfirmationDialog;
import utils.Toast;
import utils.UndoDeleteManager;

public class ViewClientsController implements Initializable {

    @FXML private TextField searchField;
    @FXML private ListView<Client> clientListView;
    @FXML private Label lblTotalClients;
    @FXML private Label lblPreferredClients;
    @FXML private Label lblTotalReceivables;
    @FXML private Label lblShowingCount;
    @FXML private HBox paginationContainer;
    @FXML private ComboBox<String> sortComboBox;
    @FXML private ComboBox<String> insightFilterComboBox;

	private final ClientRepository repo = new ClientRepository();
	private final ObservableList<Client> masterList = FXCollections.observableArrayList();
	private FilteredList<Client> filteredList;
    private javafx.collections.transformation.SortedList<Client> sortedList;
	
    private int currentPage = 1;
    private final int itemsPerPage = 15;

	@Override
	public void initialize(URL url, ResourceBundle rb) {

		// Load DB data
		masterList.setAll(repo.findAllSortedById());
        calculateAnalytics();

		// Filtered List
		filteredList = new FilteredList<>(masterList, p -> true);
        
        // Sorted List
        sortedList = new javafx.collections.transformation.SortedList<>(filteredList);
        clientListView.setItems(sortedList); // We'll manage pagination manually on this later

		searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Sorting Logic
        sortComboBox.setItems(FXCollections.observableArrayList("Sort By", "Highest Balance", "Highest LTV", "Most Active"));
        sortComboBox.setCellFactory(lv -> new NormalListCell());
        sortComboBox.setButtonCell(new BoldListCell());
        sortComboBox.setValue("Sort By");
        sortComboBox.setOnAction(e -> applySort());

        // Insight Filter Logic (Rich Styling with Icons)
        insightFilterComboBox.setItems(FXCollections.observableArrayList(
            "All Insights", "🔴 Financial Risk", "🟡 Platinum VIP", "🟠 Preferred VIP", 
            "🟢 High Potential", "🟣 Inactive High Value", "💚 Advance Payment", "🔵 Good Standing"
        ));
        insightFilterComboBox.setCellFactory(lv -> new InsightListCell(false)); // Normal in list
        insightFilterComboBox.setButtonCell(new InsightListCell(true));  // Bold when selected
        insightFilterComboBox.setValue("All Insights");
        insightFilterComboBox.setOnAction(e -> applyFilters());

        clientListView.setCellFactory(listView -> new ClientCardCell());

        updatePagination();
        updateMetrics();
	}

    // --- Simple Normal Cell for Dropdown List ---
    class NormalListCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                setText(item);
                setStyle("-fx-font-weight: 500; -fx-text-fill: #3E312D;");
            }
        }
    }

    // --- Simple Normal Cell for Selected Button ---
    class BoldListCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
            } else {
                setText(item);
                // Switch to 500 weight as requested
                setStyle("-fx-font-weight: 500; -fx-text-fill: #000000;");
            }
        }
    }

    // --- Custom ComboBox Cell for Icons ---
    class InsightListCell extends ListCell<String> {
        private final boolean isBold;
        public InsightListCell(boolean isBold) { this.isBold = isBold; }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                setText(item);
                
                String color = "#3E312D"; 
                if (item.contains("Red") || item.contains("Risk")) color = "#E53935";
                else if (item.contains("Platinum")) color = "#C9A227";
                else if (item.contains("Preferred")) color = "#D4A373";
                else if (item.contains("Potential")) color = "#2E7D32";
                else if (item.contains("Inactive")) color = "#6D6875";
                else if (item.contains("Advance")) color = "#1B9C85";
                else if (item.contains("Good")) color = "#2F6FED";
                
                if (isBold) {
                    color = "#000000";
                } 
                else if (isSelected()) {
                    color = "#FFFFFF";
                }

                // Force 500 weight for all states as requested
                setStyle("-fx-font-weight: 500; -fx-text-fill: " + color + ";");
            }
        }
    }

    private void applyFilters() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        String insight = insightFilterComboBox.getValue();

        filteredList.setPredicate(client -> {
            // 1. Keyword check
            boolean matchesSearch = keyword.isBlank() || 
                    client.getBusinessName().toLowerCase().contains(keyword) || 
                    client.getClientName().toLowerCase().contains(keyword) ||
                    String.valueOf(client.getId()).contains(keyword);

            // 2. Insight check
            boolean matchesInsight = insight == null || insight.equals("All Insights") || 
                                     client.getInsight().equals(insight);

            return matchesSearch && matchesInsight;
        });

        currentPage = 1;
        updatePagination();
        updateMetrics();
    }

    private void calculateAnalytics() {
        try (java.sql.Connection con = utils.DBConnection.getConnection()) {
            for (Client c : masterList) {
                int clientId = c.getId();
                
                // 1. LTV Logic: Sum of all invoices (Paid + Unpaid)
                double ltv = 0;
                String sqlLtv = "SELECT SUM(amount) FROM invoice_master WHERE client_id = ? AND is_void = 0";
                try (java.sql.PreparedStatement ps = con.prepareStatement(sqlLtv)) {
                    ps.setInt(1, clientId);
                    java.sql.ResultSet rs = ps.executeQuery();
                    if (rs.next()) ltv = rs.getDouble(1);
                }
                c.setLtv(ltv);
                
                // 2. Balance Logic: Total outstanding - Payments
                // (Using repo logic for unallocated balance if applicable, or simple aggregation)
                double totalInvoiced = ltv;
                double totalPaid = 0;
                String sqlPaid = "SELECT SUM(amount) FROM payments WHERE client_id = ?";
                try (java.sql.PreparedStatement ps = con.prepareStatement(sqlPaid)) {
                    ps.setInt(1, clientId);
                    java.sql.ResultSet rs = ps.executeQuery();
                    if (rs.next()) totalPaid = rs.getDouble(1);
                }
                
                // Adjust for CN/DN
                double adjustments = 0;
                String sqlAdj = "SELECT SUM(CASE WHEN type='Debit Note' THEN amount ELSE -amount END) FROM invoice_adjustments WHERE invoice_id IN (SELECT id FROM invoice_master WHERE client_id = ?)";
                try (java.sql.PreparedStatement ps = con.prepareStatement(sqlAdj)) {
                    ps.setInt(1, clientId);
                    java.sql.ResultSet rs = ps.executeQuery();
                    if (rs.next()) adjustments = rs.getDouble(1);
                }
                
                double bal = totalInvoiced + adjustments - totalPaid;
                c.setBalance(bal);
                
                // 3. Activity Score (Simulated: count in last 30 days)
                int activity = 0;
                String sqlAct = "SELECT (SELECT COUNT(*) FROM invoice_master WHERE client_id = ? AND invoice_date > date('now','-30 days')) + " +
                                "(SELECT COUNT(*) FROM payments WHERE client_id = ? AND payment_date > date('now','-30 days'))";
                try (java.sql.PreparedStatement ps = con.prepareStatement(sqlAct)) {
                    ps.setInt(1, clientId);
                    ps.setInt(2, clientId);
                    java.sql.ResultSet rs = ps.executeQuery();
                    if (rs.next()) activity = rs.getInt(1);
                }
                c.setActivityScore(activity);
                
                // 4. Segmentation Logic
                if (activity == 0) {
                    c.setSegment("Inactive");
                } else if (ltv > 50000 && bal < 1000 && activity > 5) {
                    c.setSegment("Preferred");
                } else {
                    c.setSegment("Active");
                }
                
                // 5. Insights Intelligence (Improved Heuristics)
                if (bal > 40000 || (ltv > 0 && bal/ltv > 0.4)) {
                    c.setInsight("🔴 Financial Risk");
                } else if (ltv > 100000 && bal <= 0) {
                    c.setInsight("🟡 Platinum VIP");
                } else if (ltv > 50000 && bal <= 0) {
                    c.setInsight("🟠 Preferred VIP");
                } else if (ltv < 25000 && activity > 8) {
                    c.setInsight("🟢 High Potential");
                } else if (ltv > 60000 && activity == 0) {
                    c.setInsight("🟣 Inactive High Value");
                } else if (bal < 0) {
                    c.setInsight("💚 Advance Payment");
                } else {
                    c.setInsight("🔵 Good Standing");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applySort() {
        String criteria = sortComboBox.getValue();
        if (criteria == null) return;

        sortedList.setComparator((c1, c2) -> {
            switch (criteria) {
                case "Highest Balance":
                    return Double.compare(c2.getBalance(), c1.getBalance());
                case "Highest LTV":
                    return Double.compare(c2.getLtv(), c1.getLtv());
                case "Most Active":
                    return Integer.compare(c2.getActivityScore(), c1.getActivityScore());
                default:
                    return Integer.compare(c1.getId(), c2.getId());
            }
        });
        updatePagination();
    }

    private void updatePagination() {
        int totalItems = filteredList.size();
        final int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        
        if (currentPage > totalPages) currentPage = totalPages;
        
        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
        
        ObservableList<Client> pageItems = FXCollections.observableArrayList(sortedList.subList(startIndex, endIndex));
        clientListView.setItems(pageItems);
        
        if (paginationContainer != null) {
            paginationContainer.getChildren().clear();
            
            Button btnPrev = new Button("<");
            btnPrev.getStyleClass().add("page-btn");
            btnPrev.setDisable(currentPage == 1);
            btnPrev.setOnAction(e -> { currentPage--; updatePagination(); });
            paginationContainer.getChildren().add(btnPrev);
            
            // Logic to show a max of 3 pages (sliding window)
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
            
            // Page input field
            Label jumpLabel = new Label("Go to:");
            jumpLabel.setStyle("-fx-text-fill: #A79F99; -fx-font-size: 11px; -fx-padding: 0 0 0 10;");
            
            TextField txtJump = new TextField();
            txtJump.getStyleClass().add("search-field");
            txtJump.setStyle("-fx-padding: 4 8; -fx-font-size: 11px; -fx-background-radius: 8; -fx-border-radius: 8;");
            txtJump.setPrefWidth(45);
            txtJump.setPromptText("Pg");
            txtJump.setOnAction(e -> {
                try {
                    int p = Integer.parseInt(txtJump.getText().trim());
                    if (p >= 1 && p <= totalPages) {
                        currentPage = p;
                        updatePagination();
                    } else {
                        txtJump.setText(String.valueOf(currentPage));
                    }
                } catch (NumberFormatException ex) {
                    txtJump.setText(String.valueOf(currentPage));
                }
            });
            paginationContainer.getChildren().addAll(jumpLabel, txtJump);
        }
        
        if (lblShowingCount != null) {
            lblShowingCount.setText(totalItems == 0 ? "0 of 0" : (startIndex + 1) + "-" + endIndex + " of " + masterList.size());
        }
    }

    private void updateMetrics() {
        int total = filteredList.size();
        lblTotalClients.setText(String.format("%,d", total));
        
        int preferred = (int) filteredList.stream().filter(c -> "Preferred".equals(c.getSegment())).count();
        double receivables = filteredList.stream().filter(c -> c.getBalance() > 0).mapToDouble(Client::getBalance).sum();
        
        lblPreferredClients.setText(String.valueOf(preferred));
        lblTotalReceivables.setText(String.format("$%,.1fk", receivables / 1000.0));
    }

    // --- Custom Cell Factory for Card Aesthetic ---
    class ClientCardCell extends ListCell<Client> {
        private final HBox root = new HBox(20);
        private final Region icon = new Region();
        private final StackPane iconBox = new StackPane(icon);
        
        private final Label lblBusiness = new Label();
        private final Label lblPrimary = new Label();
        private final VBox nameBox = new VBox(4, lblBusiness, lblPrimary);
        
        private final Label lblStatus = new Label();
        private final StackPane statusBox = new StackPane(lblStatus);
        
        private final Label lblLtvVal = new Label();
        private final VBox ltvBox = new VBox(4, new Label("LTV"), lblLtvVal);
        
        private final Label lblBalanceVal = new Label();
        private final VBox balanceBox = new VBox(4, new Label("BALANCE"), lblBalanceVal);
        
        private final Label lblInsight = new Label();
        private final VBox insightBox = new VBox(4, new Label("INSIGHTS"), lblInsight);
        
        private final HBox activityBox = new HBox(3);
        private final VBox activityContainer = new VBox(4, new Label("ACTIVITY"), activityBox);
        
        private final Button btnProfile = new Button("View Profile");
        
        private final Random rand = new Random();

        public ClientCardCell() {
            super();
            
            // Stylistic Setup
            root.getStyleClass().add("client-card-cell");
            root.setAlignment(Pos.CENTER_LEFT);
            root.setPadding(new Insets(12, 25, 12, 25));

            iconBox.getStyleClass().add("client-icon-box");
            icon.getStyleClass().add("client-icon");
            
            lblBusiness.getStyleClass().add("client-name");
            lblPrimary.getStyleClass().add("client-primary");
            nameBox.setMinWidth(150);
            nameBox.setPrefWidth(200);
            nameBox.setAlignment(Pos.CENTER_LEFT);
            
            lblStatus.getStyleClass().add("client-status-text");
            statusBox.getStyleClass().add("client-status-box");
            statusBox.setMinWidth(80);
            
            ((Label)ltvBox.getChildren().get(0)).getStyleClass().add("client-stat-label");
            lblLtvVal.getStyleClass().add("client-stat-value");
            ltvBox.setPrefWidth(80);
            
            ((Label)balanceBox.getChildren().get(0)).getStyleClass().add("client-stat-label");
            lblBalanceVal.getStyleClass().add("client-stat-value");
            balanceBox.setPrefWidth(80);
            
            ((Label)insightBox.getChildren().get(0)).getStyleClass().add("client-stat-label");
            lblInsight.getStyleClass().add("client-stat-value");
            lblInsight.setStyle("-fx-font-size: 11px; -fx-text-fill: #8E4D21;");
            insightBox.setPrefWidth(120);
            
            ((Label)activityContainer.getChildren().get(0)).getStyleClass().add("client-stat-label");
            activityBox.setAlignment(Pos.BOTTOM_CENTER);
            activityBox.setPrefHeight(20);
            activityContainer.setPrefWidth(70);
            activityContainer.setAlignment(Pos.TOP_CENTER);
            
            btnProfile.getStyleClass().add("action-btn-ghost");
            btnProfile.setOnAction(e -> {
                Client client = getItem();
                if (client != null) {
                    MainController.getInstance().loadClientProfile(client);
                }
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            root.getChildren().addAll(iconBox, nameBox, spacer, statusBox, ltvBox, balanceBox, insightBox, activityContainer, btnProfile);
        }

        @Override
        protected void updateItem(Client client, boolean empty) {
            super.updateItem(client, empty);

            if (empty || client == null) {
                setGraphic(null);
            } else {
                lblBusiness.setText(client.getBusinessName());
                lblPrimary.setText("Primary: " + (client.getClientName().isBlank() ? "N/A" : client.getClientName()));
                
                // 1. Segmentation
                statusBox.getStyleClass().removeAll("status-preferred", "status-active", "status-inactive");
                lblStatus.setText(client.getSegment());
                if ("Preferred".equals(client.getSegment())) {
                    statusBox.getStyleClass().add("status-preferred");
                } else if ("Active".equals(client.getSegment())) {
                    statusBox.getStyleClass().add("status-active");
                } else {
                    statusBox.getStyleClass().add("status-inactive");
                }
                
                // 2. LTV
                lblLtvVal.setText(String.format("₹%,.0f", client.getLtv()));
                
                // 3. Balance
                double bal = client.getBalance();
                lblBalanceVal.setText(bal == 0 ? "₹0" : String.format("₹%,.0f", bal));
                if (bal > 0) {
                    lblBalanceVal.setStyle("-fx-text-fill: #E53E3E;"); // Red (Owes)
                } else if (bal < 0) {
                    lblBalanceVal.setStyle("-fx-text-fill: #38A169;"); // Green (Advance)
                } else {
                    lblBalanceVal.setStyle("-fx-text-fill: #4A5568;"); // Neutral
                }

                // 4. Insights with Tooltips
                String insight = client.getInsight();
                lblInsight.setText(insight);
                
                String color = "#3E312D";
                String tooltipText = "";
                switch (insight) {
                    case "🔴 Financial Risk": 
                        color = "#E53935";
                        tooltipText = "High outstanding balance (>40%) relative to Lifetime Value. Follow-up recommended."; break;
                    case "🟡 Platinum VIP": 
                        color = "#C9A227";
                        tooltipText = "Elite partner (LTV > ₹100k) with no debt. Top priority for priority service."; break;
                    case "🟠 Preferred VIP": 
                        color = "#D4A373";
                        tooltipText = "Valued recurring partner with a healthy financial record."; break;
                    case "🟢 High Potential": 
                        color = "#2E7D32";
                        tooltipText = "Growing account with low history but very high recent frequency."; break;
                    case "🟣 Inactive High Value": 
                        color = "#6D6875";
                        tooltipText = "Historically significant client with zero activity in 30 days. Needs re-engagement."; break;
                    case "💚 Advance Payment": 
                        color = "#1B9C85";
                        tooltipText = "Client has a credit balance or has paid in advance of invoicing."; break;
                    case "🔵 Good Standing": 
                        color = "#2F6FED";
                        tooltipText = "Standard active client with a healthy balance/activity ratio."; break;
                    default: 
                        tooltipText = "No specific intelligence needed at this time."; break;
                }
                lblInsight.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: " + color + ";");
                lblInsight.setTooltip(new javafx.scene.control.Tooltip(tooltipText));
                
                // 5. Activity Sparkline
                activityBox.getChildren().clear();
                rand.setSeed(client.getId());
                int score = client.getActivityScore();
                
                if (score == 0) { // Inactive
                    for (int i=0; i<5; i++) {
                        Region dot = new Region();
                        dot.setStyle("-fx-background-color: #E2E8F0; -fx-min-width: 4; -fx-min-height: 4; -fx-background-radius: 50;");
                        activityBox.getChildren().add(dot);
                    }
                } else {
                    for (int i=0; i<5; i++) {
                        Region bar = new Region();
                        bar.getStyleClass().add("activity-bar");
                        // Higher score = taller bars on average
                        int height = 2 + (score/4) + rand.nextInt(12);
                        bar.setPrefHeight(height);
                        bar.setMinHeight(height);
                        bar.setMaxHeight(height);
                        activityBox.getChildren().add(bar);
                    }
                }

                setGraphic(root);
            }
        }
    }

	private void openEditClient(Client client) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/client_form.fxml"));
			Parent view = loader.load();

			ClientFormController controller = loader.getController();
			controller.setClientData(client);

			// Open inside main window
			MainController.getInstance().setCenterContent(view);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
