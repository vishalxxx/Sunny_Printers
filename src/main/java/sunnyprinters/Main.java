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
		// ── Startup Diagnostics ──────────────────────────────────────────────
		logStartupInfo();

		// Load bundled fonts once (works offline)
		loadBundledFonts();

		// Trigger First-Run Database and Folder Initialization
		try {
			utils.DBConnection.ensureDatabaseParentDirectory();
			String dbUrl = utils.DBConnection.getUrl();
			service.LoggerService.info("[Main] Database URL on startup: " + dbUrl);
			// Run detailed database diagnostics
			service.DatabaseDiagnostic.runStartupDiagnostics();
		} catch (Exception ex) {
			service.LoggerService.error("[Main] Error during database initialization: " + ex.getMessage(), ex);
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

		primaryStage.setOnHidden(event -> {
			service.LoggerService.info("[Main] Application window closed. Shutting down.");
		});

		primaryStage.show();
		service.LoggerService.info("[Main] Login window displayed. Application ready.");

		// Asynchronous, non-blocking check for stable software updates at startup
		java.util.concurrent.CompletableFuture.runAsync(() -> {
			try {
				service.LoggerService.info("[Main] Checking for software updates...");
				service.UpdateService updateService = new service.UpdateService();
				service.UpdateService.UpdateCheckResult result = updateService.checkForUpdates(true);
				if (result.getStatus() != service.UpdateService.UpdateStatus.NO_UPDATE) {
					service.LoggerService.info("[Main] Update available. Showing update dialog.");
					javafx.application.Platform.runLater(() -> {
						controller.UpdateDialog dialog = new controller.UpdateDialog(result);
						dialog.initOwner(primaryStage);
						dialog.showAndWait();
					});
				} else {
					service.LoggerService.info("[Main] No software updates available.");
				}
			} catch (Exception ex) {
				service.LoggerService.error("[Main] Startup update check failed: " + ex.getMessage(), ex);
			}
		});
	}

	private static void logStartupInfo() {
		service.LoggerService.info("================================================================");
		service.LoggerService.info("   Sunny Printers ERP - Application Starting");
		service.LoggerService.info("================================================================");
		service.LoggerService.info("Java Version      : " + System.getProperty("java.version"));
		service.LoggerService.info("Java Vendor       : " + System.getProperty("java.vendor"));
		service.LoggerService.info("OS Name           : " + System.getProperty("os.name"));
		service.LoggerService.info("OS Version        : " + System.getProperty("os.version"));
		service.LoggerService.info("OS Architecture   : " + System.getProperty("os.arch"));
		service.LoggerService.info("Working Directory : " + System.getProperty("user.dir"));
		service.LoggerService.info("User Home         : " + System.getProperty("user.home"));
		service.LoggerService.info("User Name         : " + System.getProperty("user.name"));

		// Read app version from version.properties
		try {
			java.util.Properties versionProps = new java.util.Properties();
			java.io.InputStream is = Main.class.getResourceAsStream("/version.properties");
			if (is != null) {
				versionProps.load(is);
				String version = versionProps.getProperty("version", "unknown");
				service.LoggerService.info("App Version       : " + version);
			}
		} catch (Exception e) {
			service.LoggerService.warn("Could not read version.properties: " + e.getMessage());
		}

		service.LoggerService.info("Expected DB Dir   : " + System.getProperty("user.home") + java.io.File.separator + ".sunnyprinters");
		service.LoggerService.info("================================================================");
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
