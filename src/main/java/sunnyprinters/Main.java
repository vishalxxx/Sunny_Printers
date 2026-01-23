package sunnyprinters;


import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;


public class Main extends Application {

	public static void main(String[] args) throws Throwable {

		launch(args);
		
//		Invoice ib = new InvoiceBuilderService().buildInvoiceForClient("Tarun Shah", LocalDate.of(2025, 01, 01), LocalDate.of(2026, 01, 10));
//		new InvoiceGenerationService().generateSingleInvoice(ib);;


	}

	@Override
	public void start(Stage primaryStage) throws Exception {

	    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));

	    StackPane root = loader.load();   // ✅ MUST be StackPane

	    Scene scene = new Scene(root);

	    primaryStage.setScene(scene);
	    primaryStage.setTitle("Sunny Printers");

	    // ✅ init loader system BEFORE showing stage
	    utils.LoaderManager.init(root);

	    primaryStage.show();
	}


}
