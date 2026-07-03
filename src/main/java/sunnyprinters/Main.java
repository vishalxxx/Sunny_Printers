package sunnyprinters;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.text.Font;

public class Main extends Application {

	public static void main(String[] args) throws Throwable {

		launch(args);

		// Invoice ib = new InvoiceBuilderService().buildInvoiceForClient("Tarun Shah",
		// LocalDate.of(2025, 01, 01), LocalDate.of(2026, 01, 10));
		// new InvoiceGenerationService().generateSingleInvoice(ib);;

	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		// Load bundled fonts once (works offline)
		loadBundledFonts();

		// Trigger First-Run Database and Folder Initialization
		try {
			utils.DBConnection.ensureDatabaseParentDirectory();
			// Referencing DBConnection class forces static block execution to setup schema
			String dbUrl = utils.DBConnection.getUrl();
			System.out.println("[Main] Database initialized on startup: " + dbUrl);
		} catch (Exception ex) {
			System.err.println("[Main] Error during database initialization: " + ex.getMessage());
			ex.printStackTrace();
		}

		FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
		Parent root = loader.load();

		Scene scene = new Scene(root);
		applyAppSceneStylesheets(scene);

		primaryStage.setScene(scene);
		primaryStage.setTitle("Sunny Printers");
		primaryStage.setMaximized(false);
		primaryStage.setWidth(760);
		primaryStage.setHeight(500);
		primaryStage.centerOnScreen();

		primaryStage.setOnCloseRequest(event -> {
			if (controller.MainController.getInstance() != null) {
				if (!controller.MainController.getInstance().canDiscardChanges()) {
					event.consume();
				}
			}
		});

		primaryStage.show();

		// Asynchronous, non-blocking check for stable software updates at startup
		java.util.concurrent.CompletableFuture.runAsync(() -> {
			try {
				service.UpdateService updateService = new service.UpdateService();
				service.UpdateService.UpdateCheckResult result = updateService.checkForUpdates(true);
				if (result.getStatus() != service.UpdateService.UpdateStatus.NO_UPDATE) {
					javafx.application.Platform.runLater(() -> {
						controller.UpdateDialog dialog = new controller.UpdateDialog(result);
						dialog.initOwner(primaryStage);
						dialog.showAndWait();
					});
				}
			} catch (Exception ex) {
				System.err.println("[Main] Asynchronous startup update check failed: " + ex.getMessage());
			}
		});
	}

	private static void loadBundledFonts() {
		try {
			Font.loadFont(Main.class.getResourceAsStream("/fonts/Inter-Variable.ttf"), 13);
			Font.loadFont(Main.class.getResourceAsStream("/fonts/Inter-Italic-Variable.ttf"), 13);
			Font.loadFont(Main.class.getResourceAsStream("/fonts/Manrope-Variable.ttf"), 13);
		} catch (Exception ignored) {
			// If fonts fail to load, JavaFX will fall back to system fonts.
		}
	}

	/** Same ordering as the primary stage: required when dashboard is shown after login. */
	public static void applyAppSceneStylesheets(Scene scene) {
		java.util.ArrayList<String> urls = new java.util.ArrayList<>();
		addSceneStyle(urls, "/css/theme.css");
		addSceneStyle(urls, "/css/compact_screens.css");
		addSceneStyle(urls, "/css/client_ledger.css");
		addSceneStyle(urls, "/css/settings_screens.css");
		scene.getStylesheets().setAll(urls);
	}

	private static void addSceneStyle(java.util.List<String> list, String classpath) {
		java.net.URL u = Main.class.getResource(classpath);
		if (u != null) {
			list.add(u.toExternalForm());
		}
	}
}
