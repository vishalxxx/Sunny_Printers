package sunnyprinters;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

	public static void main(String[] args) throws Throwable {

		launch(args);

		// Invoice ib = new InvoiceBuilderService().buildInvoiceForClient("Tarun Shah",
		// LocalDate.of(2025, 01, 01), LocalDate.of(2026, 01, 10));
		// new InvoiceGenerationService().generateSingleInvoice(ib);;

	}

	@Override
	public void start(Stage primaryStage) throws Exception {

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

}
