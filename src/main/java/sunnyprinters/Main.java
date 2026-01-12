package sunnyprinters;

import java.time.LocalDate;

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

		launch(args);
		
//		Invoice ib = new InvoiceBuilderService().buildInvoiceForClient("Tarun Shah", LocalDate.of(2025, 01, 01), LocalDate.of(2026, 01, 10));
//		new InvoiceGenerationService().generateSingleInvoice(ib);;


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
