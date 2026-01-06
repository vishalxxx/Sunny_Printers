package sunnyprinters;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

	public static void main(String[] args) throws Throwable {

		launch(args);
//		InvoiceBuilderService ib = new InvoiceBuilderService();
//		Invoice in = ib.buildInvoiceForClient(
//		        "Tarun Shah",
//		        LocalDate.of(2024, 1, 1),
//	            LocalDate.of(2026, 1, 10)
//		);
//		InvoiceGenerationService ig = new InvoiceGenerationService();
//		ig.generateExcel(in);

	}

	@Override
	public void start(Stage primaryStage) throws Exception {

		Parent root = FXMLLoader.load(getClass().getResource("/fxml/dashboard.fxml"));

		Scene scene = new Scene(root);
		primaryStage.setScene(scene);
		primaryStage.setTitle("Sunny Printers");
		primaryStage.show();

	}

}
