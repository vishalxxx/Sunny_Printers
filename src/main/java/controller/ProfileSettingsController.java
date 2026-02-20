package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import model.User;
import utils.SessionManager;

import java.io.File;

public class ProfileSettingsController {

    @FXML
    private ImageView profileImageView;

    @FXML
    private TextField usernameField;

    @FXML
    private TextField roleField;

    @FXML
    private PasswordField newPasswordField;

    @FXML
    private PasswordField confirmPasswordField;

    private File selectedImageFile;

    @FXML
    public void initialize() {
        loadUserData();
    }

    private void loadUserData() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null) {
            usernameField.setText(user.getUsername());
            roleField.setText(user.getRole());

            // In a real app, we would load the image from user.getProfileImageUrl()
            // For now, if it's null, we keep default
        }
    }

    @FXML
    private void handleChangePhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));

        File file = fileChooser.showOpenDialog(usernameField.getScene().getWindow());
        if (file != null) {
            selectedImageFile = file;
            Image image = new Image(file.toURI().toString());
            profileImageView.setImage(image);

            // Center crop logic can be added here if needed,
            // but the Circle clip in FXML handles the circular aspect.
        }
    }

    @FXML
    private void handleSave() {
        String newPass = newPasswordField.getText();
        String confirmPass = confirmPasswordField.getText();

        if (!newPass.isEmpty()) {
            if (!newPass.equals(confirmPass)) {
                showAlert(Alert.AlertType.ERROR, "Password Mismatch", "New password and confirmation do not match.");
                return;
            }
            // TODO: Update password in backend
            System.out.println("Updating password to: " + newPass);
        }

        if (selectedImageFile != null) {
            // TODO: Upload/Save image logic
            System.out.println("Updating profile image: " + selectedImageFile.getAbsolutePath());
            // For demo, update session user (temporary)
            User user = SessionManager.getInstance().getCurrentUser();
            if (user != null) {
                user.setProfileImageUrl(selectedImageFile.toURI().toString());
                // Notify MainController to update header image?
                // We might need an event bus or direct reference,
                // or just refresh MainController on next load.
            }
        }

        showAlert(Alert.AlertType.INFORMATION, "Success", "Profile updated successfully!");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
