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

		// BYPASS LOGIN FOR DEV
		model.User admin = new model.User();
		admin.setUsername("Admin");
		admin.setRole("Administrator");
		// Ensure SessionManager exists and has a login method
		utils.SessionManager.getInstance().login(admin);

		FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
		Parent root = loader.load();

		// FXMLLoader loginLoader = new
		// FXMLLoader(getClass().getResource("/fxml/login.fxml"));
		// Parent root = loginLoader.load(); // Login root is AnchorPane

		Scene scene = new Scene(root);
		applyAppSceneStylesheets(scene);

		primaryStage.setScene(scene);
		primaryStage.setTitle("Sunny Printers");
		primaryStage.setMaximized(true);

		primaryStage.setOnCloseRequest(event -> {
			if (controller.MainController.getInstance() != null) {
				if (!controller.MainController.getInstance().canDiscardChanges()) {
					event.consume();
				}
			}
		});

		primaryStage.show();
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
