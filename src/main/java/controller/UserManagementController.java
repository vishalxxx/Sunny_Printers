package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.User;
import repository.UserRepository;
import utils.SessionManager;
import api.supabase.SupabaseReachability;
import api.supabase.SupabaseAuthService;
import api.supabase.SupabaseAuthResult;
import repository.SupabaseSettingsRepository;
import model.SupabaseSettings;
import service.sync.UniversalSyncEngine;
import java.util.UUID;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.beans.property.SimpleStringProperty;
import javafx.application.Platform;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class UserManagementController {

    @FXML private HBox breadcrumbContainer;
    
    // Security Panels
    @FXML private StackPane userManagementRoot;
    @FXML private VBox adminContainer;
    @FXML private VBox accessDeniedContainer;
    
    // Add User Fields
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private Button btnAddUser;
    
    // User List / Table
    @FXML private TextField searchField;
    @FXML private Label lblNetworkStatus;
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colSyncStatus;
    
    private final UserRepository userRepository = new UserRepository();
    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private FilteredList<User> filteredList;

    @FXML
    public void initialize() {
        // 1. Breadcrumbs
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null,
                () -> MainController.getInstance().handleBack(null));
                
        // 2. Role Security Check
        User currentUser = SessionManager.getInstance().getCurrentUser();
        boolean isAdmin = currentUser != null && 
                          (currentUser.getRole() != null && 
                           (currentUser.getRole().equalsIgnoreCase("ADMIN") || 
                            currentUser.getRole().equalsIgnoreCase("ADMINISTRATOR")));
                            
        if (!isAdmin) {
            adminContainer.setVisible(false);
            adminContainer.setManaged(false);
            accessDeniedContainer.setVisible(true);
            accessDeniedContainer.setManaged(true);
            return;
        }
        
        accessDeniedContainer.setVisible(false);
        accessDeniedContainer.setManaged(false);
        adminContainer.setVisible(true);
        adminContainer.setManaged(true);
        
        // 3. Initialize ComboBox
        roleComboBox.setItems(FXCollections.observableArrayList("USER", "ADMIN"));
        roleComboBox.setValue("USER");
        
        // 4. Initialize Table Columns with custom pill rendering
        colUsername.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUsername()));
        
        colRole.setCellValueFactory(cellData -> new SimpleStringProperty(
            cellData.getValue().getRole() != null ? cellData.getValue().getRole().toUpperCase() : "USER"
        ));
        colRole.setCellFactory(column -> new TableCell<User, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label(item);
                    if (item.equalsIgnoreCase("ADMIN") || item.equalsIgnoreCase("ADMINISTRATOR")) {
                        pill.setStyle("-fx-background-color: #FDF4ED; -fx-text-fill: #B25D3B; -fx-padding: 3 8; -fx-background-radius: 4; -fx-font-weight: bold; -fx-font-size: 10px;");
                    } else {
                        pill.setStyle("-fx-background-color: #EEECE8; -fx-text-fill: #5A5A5A; -fx-padding: 3 8; -fx-background-radius: 4; -fx-font-size: 10px;");
                    }
                    setGraphic(pill);
                    setText(null);
                }
            }
        });

        colSyncStatus.setCellValueFactory(cellData -> new SimpleStringProperty(
            cellData.getValue().getSyncStatus() != null ? cellData.getValue().getSyncStatus().toUpperCase() : "PENDING"
        ));
        colSyncStatus.setCellFactory(column -> new TableCell<User, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label(item);
                    if (item.equalsIgnoreCase("SYNCED")) {
                        pill.setStyle("-fx-background-color: #E8F3EE; -fx-text-fill: #1B6B4A; -fx-padding: 3 8; -fx-background-radius: 4; -fx-font-weight: bold; -fx-font-size: 10px;");
                    } else {
                        pill.setStyle("-fx-background-color: #F5F0EA; -fx-text-fill: #8E7A6B; -fx-padding: 3 8; -fx-background-radius: 4; -fx-font-size: 10px;");
                    }
                    setGraphic(pill);
                    setText(null);
                }
            }
        });

        // 5. Setup search filter
        filteredList = new FilteredList<>(userList, p -> true);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredList.setPredicate(user -> {
                if (newValue == null || newValue.isBlank()) {
                    return true;
                }
                String lower = newValue.toLowerCase().trim();
                if (user.getUsername() != null && user.getUsername().toLowerCase().contains(lower)) {
                    return true;
                }
                if (user.getRole() != null && user.getRole().toLowerCase().contains(lower)) {
                    return true;
                }
                return false;
            });
        });
        usersTable.setItems(filteredList);

        // 6. Load data and check connectivity
        refreshData();
    }

    @FXML
    public void refresh() {
        refreshData();
    }

    @FXML
    private void refreshData() {
        // Run connectivity probe asynchronously so UI doesn't hitch
        CompletableFuture.runAsync(() -> {
            boolean online = SupabaseReachability.isReachable();
            Platform.runLater(() -> {
                if (online) {
                    lblNetworkStatus.setText("● Connected to Supabase Cloud");
                    lblNetworkStatus.setStyle("-fx-text-fill: #1B6B4A; -fx-font-weight: 800; -fx-font-size: 11px;");
                } else {
                    lblNetworkStatus.setText("● Offline Mode (Local Fallback)");
                    lblNetworkStatus.setStyle("-fx-text-fill: #B25D3B; -fx-font-weight: 800; -fx-font-size: 11px;");
                }
            });
        });

        // Load users from SQLite
        try {
            List<User> list = userRepository.findAll();
            userList.setAll(list);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to retrieve local user accounts: " + e.getMessage());
        }
    }

    @FXML
    private void handleAddUser() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String role = roleComboBox.getValue();

        // 1. Validation
        if (username == null || username.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please enter a username.");
            return;
        }
        if (password == null || password.length() < 6) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Password must be at least 6 characters long.");
            return;
        }
        if (role == null) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please assign a role to the user.");
            return;
        }

        try {
            // Check if username already exists locally
            User existing = userRepository.findByUsername(username.trim());
            if (existing != null) {
                showAlert(Alert.AlertType.WARNING, "Validation Error", "A user with this username already exists.");
                return;
            }

            // Create user locally with PENDING status (Sync Engine will push to Supabase)
            User newUser = new User();
            newUser.setUuid(UUID.randomUUID().toString());
            newUser.setUsername(username.trim());
            newUser.setPassword(password);
            newUser.setRole(role);
            newUser.setSyncStatus("PENDING");

            userRepository.create(newUser);

            showAlert(Alert.AlertType.INFORMATION, "Success", 
                "User account successfully registered locally!\n" +
                "It will automatically synchronize with Supabase Cloud in the background.");

            // Reset inputs
            usernameField.clear();
            passwordField.clear();
            roleComboBox.setValue("USER");

            refreshData();

            // Schedule background sync
            UniversalSyncEngine.scheduleSyncAsync();
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Database Error", 
                "Failed to register the user locally: " + ex.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        
        // Add premium dialog styles if external css is found
        try {
            alert.getDialogPane().getStyleClass().add("settings-warm-dialog");
            alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
            alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/settings_screens.css").toExternalForm());
        } catch (Exception ignored) {}
        
        alert.showAndWait();
    }
}
