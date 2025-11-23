package controller;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

public class MainController {

	@FXML
	private VBox mainSidebar;
	@FXML
	private VBox jobsSidebar;
	@FXML
	private VBox clientsSidebar;
	@FXML
	private VBox billingSidebar;
	@FXML
	private VBox paymentSidebar;
	@FXML
	private VBox ledgerSidebar;

	// Hide all sidebars
	private void hideAllSidebars() {
		mainSidebar.setVisible(false);
		mainSidebar.setManaged(false);

		jobsSidebar.setVisible(false);
		jobsSidebar.setManaged(false);

		clientsSidebar.setVisible(false);
		clientsSidebar.setManaged(false);

		billingSidebar.setVisible(false);
		billingSidebar.setManaged(false);

		paymentSidebar.setVisible(false);
		paymentSidebar.setManaged(false);
		
		ledgerSidebar.setVisible(false);
		ledgerSidebar.setManaged(false);
		
	}

	// Show main sidebar
	@FXML
	private void showMainSidebar() {
		hideAllSidebars();
		mainSidebar.setVisible(true);
		mainSidebar.setManaged(true);
	}

	@FXML
	private void showJobsSubmenu() {
		hideAllSidebars();
		jobsSidebar.setVisible(true);
		jobsSidebar.setManaged(true);
		jobsSidebar.toFront();
	}

	@FXML
	private void showClientsSubmenu() {
		hideAllSidebars();
		clientsSidebar.setVisible(true);
		clientsSidebar.setManaged(true);
	}

	@FXML
	private void showBillingSubmenu() {
		hideAllSidebars();
		billingSidebar.setVisible(true);
		billingSidebar.setManaged(true);
	}

	@FXML
	private void showPaymentSubmenu() {
		hideAllSidebars();
		paymentSidebar.setVisible(true);
		paymentSidebar.setManaged(true);
	}
	
	@FXML
	private void showLedgerSubmenu() {
		hideAllSidebars();
		ledgerSidebar.setVisible(true);
		ledgerSidebar.setManaged(true);
	}
}
