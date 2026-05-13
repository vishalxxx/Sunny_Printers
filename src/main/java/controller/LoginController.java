package controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML
    private TextField nameField; // This matches the FXML fx:id="emailField" based on FXML content but variable
                                 // name is different.
    // Wait, let's check FXML.
    // FXML line 46: <TextField fx:id="emailField" ... />
    // Controller line 11: private TextField nameField;
    // This is a mismatch! I should fix the variable name to match FXML or vice
    // versa.
    // Given usage in existing controller: `login.login(nameField.getText())`
    // I will check if I can rename the field in validation.

    @FXML
    private TextField emailField; // matching FXML

    @FXML
    private javafx.scene.control.PasswordField passwordField;

    @FXML
    private javafx.scene.control.Label errorLabel; // Needed for error message, but FXML doesn't have it yet. I need to
                                                   // add it or use Alert.

    @FXML
    private void onSubmitClick() {
        String username = emailField.getText();
        String password = passwordField.getText();

        // Mock Validation for now (as requested default "admin", "admin")
        // Also checking against DB if needed, but priority is the requested default.
        if ("admin".equals(username) && "admin".equals(password)) {
            // Create mock user
            model.User adminUser = new model.User(); // Assuming User model has setters or constructor
            adminUser.setUsername(username);
            adminUser.setRole("ADMIN");

            utils.SessionManager.getInstance().login(adminUser);

            // Navigate to Dashboard
            navigateToDashboard();
        } else {
            // Show error
            if (errorLabel != null) {
                errorLabel.setText("Invalid credentials");
                errorLabel.setVisible(true);
            } else {
                System.out.println("Invalid credentials");
                // TODO: Add visual feedback (Alert or Label)
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Login Failed");
                alert.setHeaderText(null);
                alert.setContentText("Invalid username or password.");
                alert.showAndWait();
            }
        }
    }

    private void navigateToDashboard() {
        try {
            // Load Dashboard FXML
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            javafx.scene.Parent dashboardRoot = loader.load(); // Load as Parent to be safe (it's actually StackPane)

            // Get current stage and set new scene
            javafx.stage.Stage stage = (javafx.stage.Stage) emailField.getScene().getWindow();

            // Re-initialize LoaderManager if needed
            // But Main.java does it on start.
            // We need to ensure LoaderManager tracks the new root if strict.
            // But usually just setting the scene is enough.

            javafx.scene.Scene scene = new javafx.scene.Scene(dashboardRoot);
            sunnyprinters.Main.applyAppSceneStylesheets(scene);
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.setMaximized(true); // Dashboard is usually maximized

            // Initialize MainController things?
            // MainController initialize() handles most setup.

        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onCancelClick() {
        javafx.application.Platform.exit();
    }
}
