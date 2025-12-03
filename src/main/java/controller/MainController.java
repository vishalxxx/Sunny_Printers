package controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;

public class MainController implements Initializable {

	// Sidebar containers
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

	// Center content root (if you need it later)
	@FXML
	private VBox centerRoot;

	// Top title
	@FXML
	private Label pageTitle;

	// Optional: scroll panes (not mandatory, but ok)
	@FXML
	private ScrollPane sidebarScroll;

	@FXML
	private ScrollPane centerScroll;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		// Make sure only main menu is visible initially
		showOnly(mainSidebar);
		setPageTitle("Dashboard");
	}

	/* ====== Helpers ====== */

	private void setPageTitle(String title) {
		if (pageTitle != null) {
			pageTitle.setText(title);
		}
	}

	private void hideAllSidebars() {
		hideSidebar(mainSidebar);
		hideSidebar(jobsSidebar);
		hideSidebar(clientsSidebar);
		hideSidebar(billingSidebar);
		hideSidebar(paymentSidebar);
		hideSidebar(ledgerSidebar);
	}

	private void hideSidebar(VBox box) {
		if (box != null) {
			box.setVisible(false);
			box.setManaged(false);
		}
	}

	private void showOnly(VBox target) {
		hideAllSidebars();
		if (target != null) {
			target.setVisible(true);
			target.setManaged(true);
		}
	}

	/* ====== Event Handlers (used in FXML) ====== */

	// Called by top-left icon and main "Dashboard" item
	@FXML
	private void openDashboard(MouseEvent event) {
		showOnly(mainSidebar);
		setPageTitle("Dashboard");

		// If later you want to change center area when dashboard is clicked,
		// you can update centerRoot here or load another FXML.
	}

	@FXML
	private void showJobsSubmenu(MouseEvent event) {
		showOnly(jobsSidebar);
		setPageTitle("Job Details");
	}

	@FXML
	private void showClientsSubmenu(MouseEvent event) {
		showOnly(clientsSidebar);
		setPageTitle("Client Details");
	}

	@FXML
	private void showBillingSubmenu(MouseEvent event) {
		showOnly(billingSidebar);
		setPageTitle("Billing");
	}

	@FXML
	private void showPaymentSubmenu(MouseEvent event) {
		showOnly(paymentSidebar);
		setPageTitle("Payments");
	}

	@FXML
	private void showLedgerSubmenu(MouseEvent event) {
		showOnly(ledgerSidebar);
		setPageTitle("Ledger");
	}

	// If you ever re-add showMainSidebar in FXML, you can point it to dashboard:
	@FXML
	private void showMainSidebar(MouseEvent event) {
		openDashboard(event);
	}
}
