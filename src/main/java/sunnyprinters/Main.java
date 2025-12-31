package sunnyprinters;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import model.Invoice;
import service.InvoiceBuilderService;
import service.InvoiceGenerationService;

public class Main extends Application {

	public static void main(String[] args) throws Throwable {

		// launch(args);
		InvoiceBuilderService ib = new InvoiceBuilderService();
		Invoice in = ib.buildInvoiceForJob(43);
		InvoiceGenerationService ig = new InvoiceGenerationService();
		ig.generateExcel(in);

	}

	@Override
	public void start(Stage primaryStage) throws Exception {

		Parent root = FXMLLoader.load(getClass().getResource("/fxml/ht.fxml"));

		Scene scene = new Scene(root);
		primaryStage.setScene(scene);
		primaryStage.setTitle("Sunny Printers");
		primaryStage.show();

	}

}
