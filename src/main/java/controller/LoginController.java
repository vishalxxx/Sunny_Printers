package controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import service.AuthService;
import service.AuthService.LoginResult;
import sunnyprinters.Main;

public class LoginController {

	@FXML
	private Label titleLabel;
	@FXML
	private Label subtitleLabel;
	@FXML
	private Label emailFieldLabel;
	@FXML
	private TextField emailField;
	@FXML
	private PasswordField passwordField;
	@FXML
	private Label errorLabel;
	@FXML
	private Label infoLabel;
	@FXML
	private Button submitButton;

	@FXML
	private javafx.scene.layout.VBox loginFormContainer;
	@FXML
	private javafx.scene.layout.VBox loadingContainer;

	private final AuthService authService = new AuthService();

	@FXML
	private void initialize() {
		showSignInMode();
		hideMessage(errorLabel);
		hideMessage(infoLabel);
	}

	@FXML
	private void onSubmitClick() {
		clearMessages();
		String login = emailField.getText();
		String password = passwordField.getText();
		setBusy(true);

		Thread worker = new Thread(() -> {
			LoginResult result = authService.signIn(login, password);
			Platform.runLater(() -> {
				setBusy(false);
				handleLoginResult(result);
			});
		});
		worker.setDaemon(true);
		worker.setName("auth-login");
		worker.start();
	}

	private void handleLoginResult(LoginResult result) {
		if (result.isSuccess()) {
			String note = result.message();
			if (note != null && !note.isBlank()) {
				showInfo(note);
			}
			navigateToDashboard();
			return;
		}
		String msg = result.message();
		showError(msg != null && !msg.isBlank() ? msg : "Authentication failed.");
	}

	@FXML
	private void onCancelClick() {
		Platform.exit();
	}

	private void showSignInMode() {
		titleLabel.setText("Sign In");
		subtitleLabel.setText("Enter your username and password");
		emailFieldLabel.setText("USERNAME");
		emailField.setPromptText("Enter your username");
		submitButton.setText("SIGN IN");
	}

	private void navigateToDashboard() {
		if (loginFormContainer != null && loadingContainer != null) {
			// Step 1: Fade out the Sign In form
			javafx.animation.FadeTransition fadeOutForm = new javafx.animation.FadeTransition(
				javafx.util.Duration.millis(250), loginFormContainer
			);
			fadeOutForm.setFromValue(1.0);
			fadeOutForm.setToValue(0.0);

			javafx.animation.TranslateTransition translateForm = new javafx.animation.TranslateTransition(
				javafx.util.Duration.millis(250), loginFormContainer
			);
			translateForm.setFromY(0);
			translateForm.setToY(30);

			javafx.animation.ParallelTransition ptForm = new javafx.animation.ParallelTransition(fadeOutForm, translateForm);
			ptForm.setOnFinished(event -> {
				// Hide form container completely
				loginFormContainer.setVisible(false);
				loginFormContainer.setManaged(false);

				// Show loading overlay completely
				loadingContainer.setVisible(true);
				loadingContainer.setManaged(true);
				loadingContainer.setOpacity(0.0);

				// Step 2: Fade in the loading overlay
				javafx.animation.FadeTransition fadeInLoader = new javafx.animation.FadeTransition(
					javafx.util.Duration.millis(200), loadingContainer
				);
				fadeInLoader.setFromValue(0.0);
				fadeInLoader.setToValue(1.0);
				fadeInLoader.setOnFinished(e -> {
					// Step 3: Load the dashboard asynchronously in a background thread
					loadDashboardAsynchronously();
				});
				fadeInLoader.play();
			});
			ptForm.play();
		} else {
			loadDashboardAsynchronously();
		}
	}

	private void loadDashboardAsynchronously() {
		Thread loaderThread = new Thread(() -> {
			try {
				// Parse and build FXML in background thread (perfectly thread-safe since it's not live yet)
				FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
				Parent dashboardRoot = loader.load();

				// Switch scene root and maximize window on the FX Application Thread
				Platform.runLater(() -> {
					try {
						Stage stage = (Stage) emailField.getScene().getWindow();

						// 1. Maximize the stage first while our beautiful responsive split-screen is visible
						if (!stage.isMaximized()) {
							stage.setMaximized(true);
						}

						// 2. Pause for 250ms to allow the OS maximizing transition to complete fluidly
						javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
							javafx.util.Duration.millis(250)
						);
						pause.setOnFinished(event -> {
							try {
								// 3. Smoothly swap root once stage has fully finished maximizing
								Scene scene = stage.getScene();
								if (scene == null) {
									scene = new Scene(dashboardRoot);
									stage.setScene(scene);
								} else {
									scene.setRoot(dashboardRoot);
								}
								Main.applyAppSceneStylesheets(scene);
								stage.show();
							} catch (Exception ex) {
								showError("Transition failed: " + ex.getMessage());
								ex.printStackTrace();
							}
						});
						pause.play();

					} catch (Exception ex) {
						showError("Transition failed: " + ex.getMessage());
						ex.printStackTrace();
					}
				});
			} catch (Exception e) {
				Platform.runLater(() -> {
					showError("Could not load dashboard: " + e.getMessage());
					// Restore form container if loading failed
					if (loginFormContainer != null && loadingContainer != null) {
						loadingContainer.setVisible(false);
						loadingContainer.setManaged(false);
						loginFormContainer.setVisible(true);
						loginFormContainer.setManaged(true);
						loginFormContainer.setOpacity(1.0);
						loginFormContainer.setTranslateY(0);
					}
				});
				e.printStackTrace();
			}
		});
		loaderThread.setDaemon(true);
		loaderThread.setName("dashboard-loader");
		loaderThread.start();
	}

	private void setBusy(boolean busy) {
		submitButton.setDisable(busy);
		emailField.setDisable(busy);
		passwordField.setDisable(busy);
		submitButton.setText(busy ? "Please wait…" : "SIGN IN");
	}

	private void clearMessages() {
		hideMessage(errorLabel);
		hideMessage(infoLabel);
	}

	private void showError(String text) {
		errorLabel.setText(text);
		errorLabel.setVisible(true);
		errorLabel.setManaged(true);
		hideMessage(infoLabel);
	}

	private void showInfo(String text) {
		infoLabel.setText(text);
		infoLabel.setVisible(true);
		infoLabel.setManaged(true);
		hideMessage(errorLabel);
	}

	private static void hideMessage(Label label) {
		if (label == null) {
			return;
		}
		label.setText("");
		label.setVisible(false);
		label.setManaged(false);
	}
}
