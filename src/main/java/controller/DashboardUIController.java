package controller;

import javafx.fxml.FXML;
import javafx.scene.input.MouseEvent;

public class DashboardUIController {

    @FXML
    private void loadClientLedger(MouseEvent event) {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showLedgerSubmenu(event);
            MainController.getInstance().loadClientLedger(event);
        }
    }

    @FXML
    private void loadViewClients(MouseEvent event) {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showClientsSubmenu(event);
            MainController.getInstance().loadViewClients();
        }
    }

    @FXML
    private void loadViewJob(MouseEvent event) {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showJobsSubmenu(event);
            MainController.getInstance().loadViewJob(event);
        }
    }

    @FXML
    private void loadInvoiceGenration(MouseEvent event) {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showBillingSubmenu(event);
            MainController.getInstance().loadInvoiceGenration();
        }
    }

    @FXML
    private void loadRecordPayment(MouseEvent event) {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showPaymentSubmenu(event);
            MainController.getInstance().loadRecordPayment(event);
        }
    }

    @FXML
    private void loadOutstanding(MouseEvent event) {
        // Will implement later, placeholder for now
        System.out.println("Outstanding tile clicked");
    }

    @FXML
    private void loadExpenses(MouseEvent event) {
        // Will implement later, placeholder for now
        System.out.println("Expenses tile clicked");
    }

    @FXML
    private void loadReports(MouseEvent event) {
        // Will implement later, placeholder for now
        System.out.println("Reports tile clicked");
    }

    @FXML
    private void loadUserSettings(MouseEvent event) {
        if (MainController.getInstance() != null) {
            MainController.getInstance().showSettingSubmenu(event);
            MainController.getInstance().loadUserSettings(event);
        }
    }
}
