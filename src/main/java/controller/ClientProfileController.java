package controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.application.Platform;
import model.Client;
import model.Invoice;
import model.Job;
import utils.DBConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ResourceBundle;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;

public class ClientProfileController implements Initializable {

    @FXML private Label lblInitial;
    @FXML private Label lblBusinessName;
    @FXML private Label lblStatusTag;
    @FXML private Label lblOwnerName;
    @FXML private Label lblEmail;
    @FXML private Label lblPhone;
    
    @FXML private Label lblLtv;
    @FXML private Label lblBalance;
    @FXML private Label lblNextDue;
    @FXML private Label lblActiveJobs;
    @FXML private Label lblTurnaround;
    
    @FXML private VBox cardBalance;

    @FXML private Button btnEditProfile;

    @FXML private VBox pipelineContainer;
    @FXML private VBox historyContainer;
    
    @FXML private Label lblAddress;
    @FXML private Label lblTaxId;
    @FXML private Label lblNotes;

    private Client currentClient;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (btnEditProfile != null) {
            btnEditProfile.setOnAction(e -> {
                if (currentClient != null) {
                    MainController.getInstance().loadEditClient(currentClient);
                }
            });
        }
    }


    public void setClient(Client client) {
        this.currentClient = client;
        refreshProfile();
    }

    public void refreshProfile() {
        if (currentClient == null) return;
        updateUI();
        loadInvoiceHistory();
        loadActivePipeline();
    }

    private void updateUI() {
        String name = currentClient.getBusinessName() != null ? currentClient.getBusinessName() : "Unknown Client";
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        String insight = currentClient.getInsight() != null ? currentClient.getInsight() : "";
        String status = insight.replace("📋 ", "").replace("💚 ", "").replace(" Platinum VIP", "PLATINUM").replace("Preferred VIP", "PREFERRED").trim();
        String owner = (currentClient.getClientName() != null ? currentClient.getClientName() : "N/A") + " (Owner)";
        String email = currentClient.getEmail() != null ? currentClient.getEmail() : "N/A";
        String phone = currentClient.getPhone() != null ? currentClient.getPhone() : "N/A";
        String ltv = String.format("₹%,.2f", currentClient.getLtv());
        String balance = String.format("₹%,.2f", currentClient.getBalance());
        String address = currentClient.getBillingAddress() != null ? currentClient.getBillingAddress() : "N/A";
        String tax = currentClient.getGst() != null && !currentClient.getGst().isEmpty() ? currentClient.getGst() : "N/A";
        String notesStr = currentClient.getNotes() != null && !currentClient.getNotes().isEmpty() ? currentClient.getNotes() : "No internal notes available.";
        String notes = "\"" + notesStr + "\"";

        Platform.runLater(() -> {
            if (lblBusinessName != null) lblBusinessName.setText(name);
            if (lblInitial != null) lblInitial.setText(initial);
            if (lblStatusTag != null) lblStatusTag.setText(status);
            if (lblOwnerName != null) lblOwnerName.setText(owner);
            if (lblEmail != null) lblEmail.setText(email);
            if (lblPhone != null) lblPhone.setText(phone);
            if (lblLtv != null) lblLtv.setText(ltv);
            if (lblAddress != null) lblAddress.setText(address);
            if (lblTaxId != null) lblTaxId.setText(tax);
            if (lblNotes != null) lblNotes.setText(notes);

            if (lblBalance != null) {
                lblBalance.setText(balance);
                
                double bal = currentClient.getBalance();
                cardBalance.getStyleClass().removeAll("kpi-card-unpaid", "kpi-card-paid");
                lblBalance.getStyleClass().removeAll("text-alert", "text-success");
                
                if (bal > 0) {
                    cardBalance.getStyleClass().add("kpi-card-unpaid");
                    lblBalance.getStyleClass().add("text-alert");
                    if (lblNextDue != null) lblNextDue.setText("Payment Overdue");
                } else {
                    cardBalance.getStyleClass().add("kpi-card-paid");
                    lblBalance.getStyleClass().add("text-success");
                    if (lblNextDue != null) lblNextDue.setText("All invoices paid");
                }
            }
        });
    }

    private void loadActivePipeline() {
        List<Job> activeJobs = new ArrayList<>();
        try (Connection con = DBConnection.getConnection()) {
            String sql = "SELECT job_title, status, job_no FROM jobs WHERE client_id = ? AND status != 'Completed' ORDER BY id DESC LIMIT 2";
            try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, currentClient.getId());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Job j = new Job();
                    j.setJobTitle(rs.getString(1));
                    j.setStatus(rs.getString(2));
                    j.setJobNo(rs.getString(3));
                    activeJobs.add(j);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Platform.runLater(() -> {
            if (pipelineContainer == null) return;
            pipelineContainer.getChildren().clear();
            if (activeJobs.isEmpty()) {
                Label placeholder = new Label("No active jobs in production.");
                placeholder.setStyle("-fx-text-fill: #A0AEC0; -fx-font-style: italic;");
                pipelineContainer.getChildren().add(placeholder);
            } else {
                for (Job job : activeJobs) {
                    pipelineContainer.getChildren().add(createJobPipelineCard(job));
                }
            }
            if (lblActiveJobs != null) lblActiveJobs.setText(String.valueOf(activeJobs.size()));
            if (lblNextDue != null) lblNextDue.setText(activeJobs.isEmpty() ? "No pending jobs" : "Next due in " + (int)(Math.random()*5+1) + " days");
            if (lblTurnaround != null) lblTurnaround.setText("4.2 days");
        });
    }

    private VBox createJobPipelineCard(Job job) {
        VBox card = new VBox();
        card.getStyleClass().add("pipeline-card");
        
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        
        VBox nameBox = new VBox(2);
        HBox.setHgrow(nameBox, Priority.ALWAYS);
        Label title = new Label(job.getJobTitle() != null ? job.getJobTitle() : "Untitled Job");
        title.getStyleClass().add("job-card-title");
        Label specs = new Label("Job #" + (job.getJobNo() != null ? job.getJobNo() : "---"));
        specs.getStyleClass().add("job-card-specs");
        nameBox.getChildren().addAll(title, specs);
        
        String statusText = job.getStatus() == null ? "START" : job.getStatus().toUpperCase();
        boolean isCompleted = statusText.contains("COMPLETED") || statusText.contains("SHIPPED");
        
        Label statusTag = new Label(isCompleted ? "COMPLETED" : "IN PRODUCTION");
        statusTag.getStyleClass().add(isCompleted ? "status-pill-green" : "status-pill-subtle");
        
        header.getChildren().addAll(nameBox, statusTag);
        
        VBox progressSection = new VBox(8);
        progressSection.setStyle("-fx-padding: 15 0 0 0;");
        
        double progress = 0.25;
        int activeStep = 0;
        if (statusText.contains("START")) { progress = 0.3; activeStep = 1; }
        else if (statusText.contains("PRINTING")) { progress = 0.6; activeStep = 2; }
        else if (statusText.contains("FINISHING")) { progress = 0.85; activeStep = 3; }
        else if (statusText.contains("PAID") || statusText.contains("SHIPPED")) { progress = 1.0; activeStep = 4; }
        
        ProgressBar bar = new ProgressBar(progress);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add(progress >= 1.0 ? "pipeline-progress-green" : "pipeline-progress");
        
        HBox steps = new HBox();
        steps.setAlignment(Pos.CENTER);
        
        steps.getChildren().addAll(
            createStepLabel("PRE-PRESS", activeStep == 0),
            createSpacer(),
            createStepLabel("PRINTING", activeStep == 1 || activeStep == 2),
            createSpacer(),
            createStepLabel("FINISHING", activeStep == 3),
            createSpacer(),
            createStepLabel(progress >= 1.0 ? "SHIPPED" : "SHIPPING", activeStep == 4)
        );
        
        progressSection.getChildren().addAll(bar, steps);
        card.getChildren().addAll(header, progressSection);
        
        return card;
    }

    private Label createStepLabel(String text, boolean active) {
        Label l = new Label(text);
        l.getStyleClass().add(active ? "progress-step-active" : "progress-step");
        return l;
    }

    private Region createSpacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private void loadInvoiceHistory() {
        if (historyContainer == null) return;
        
        List<Invoice> history = new ArrayList<>();
        try (Connection con = DBConnection.getConnection()) {
            // Unified Financial History Query (Invoices + Standalone Payments)
            String sql = "SELECT invoice_no, invoice_date, amount, status, payment_status, 'INVOICE' as row_type FROM invoice_master WHERE client_id = ? AND is_void = 0 " +
                         "UNION ALL " +
                         "SELECT 'PYMT-' || id, payment_date, amount, 'PAID', 'PAID', 'PAYMENT' as row_type FROM payments WHERE client_id = ? " +
                         "ORDER BY invoice_date DESC LIMIT 8";
                         
            try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, currentClient.getId());
                ps.setInt(2, currentClient.getId());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Invoice inv = new Invoice();
                    inv.setInvoiceNo(rs.getString(1));
                    String dateStr = rs.getString(2);
                    if (dateStr != null && !dateStr.isEmpty()) {
                        try { inv.setInvoiceDate(LocalDate.parse(dateStr)); } catch (Exception e) {}
                    }
                    inv.setGrandTotal(rs.getDouble(3));
                    
                    String rowType = rs.getString(6);
                    if ("INVOICE".equals(rowType)) {
                        String invStatus = rs.getString(4) != null ? rs.getString(4).toUpperCase() : "";
                        String payStatus = rs.getString(5) != null ? rs.getString(5).toUpperCase() : "";
                        
                        String finalStatus = invStatus;
                        if (invStatus.contains("SENT") && !payStatus.contains("UNPAID")) {
                            finalStatus = payStatus;
                        }
                        inv.setStatus(finalStatus);
                    } else {
                        inv.setStatus("PAID"); // Standalone Payment
                    }
                    
                    history.add(inv);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Platform.runLater(() -> {
            historyContainer.getChildren().clear();
            if (history.isEmpty()) {
                Label empty = new Label("No financial activity found.");
                empty.setStyle("-fx-padding: 20; -fx-text-fill: -fx-text-muted;");
                historyContainer.getChildren().add(empty);
            } else {
                for (Invoice inv : history) {
                    historyContainer.getChildren().add(createHistoryRow(inv));
                }
            }
        });
    }

    private HBox createHistoryRow(Invoice inv) {
        HBox row = new HBox(0);
        row.getStyleClass().add("history-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label idLabel = new Label("#" + inv.getInvoiceNo());
        idLabel.getStyleClass().add("invoice-id-label");
        idLabel.setPrefWidth(120);
        idLabel.setAlignment(Pos.CENTER);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        Label dateLabel = new Label(inv.getInvoiceDate() != null ? inv.getInvoiceDate().format(formatter) : "---");
        dateLabel.getStyleClass().add("invoice-date-label");
        dateLabel.setPrefWidth(150);
        dateLabel.setAlignment(Pos.CENTER);

        Label amountLabel = new Label(String.format("₹%,.2f", inv.getGrandTotal()));
        amountLabel.getStyleClass().add("invoice-amount-label");
        amountLabel.setPrefWidth(120);
        amountLabel.setAlignment(Pos.CENTER);

        String status = inv.getStatus() != null ? inv.getStatus().toUpperCase() : "PENDING";
        Label statusPill = new Label(status);
        String pillClass = "status-pill-partial";
        if (status.contains("PAID")) pillClass = "status-pill-paid";
        else if (status.contains("OVERDUE")) pillClass = "status-pill-overdue";
        statusPill.getStyleClass().add(pillClass);
        
        StackPane pillContainer = new StackPane(statusPill);
        pillContainer.setAlignment(Pos.CENTER);
        HBox.setHgrow(pillContainer, Priority.ALWAYS);

        Label actionDots = new Label("•••");
        actionDots.getStyleClass().add("action-dots");
        actionDots.setPrefWidth(60);
        actionDots.setAlignment(Pos.CENTER);
        actionDots.setCursor(Cursor.HAND);

        // Action Menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem viewItem = new MenuItem("View Details");
        MenuItem downloadItem = new MenuItem("Download Receipt");
        contextMenu.getItems().addAll(viewItem, downloadItem);

        viewItem.setOnAction(e -> {
            System.out.println("Viewing details for invoice: " + inv.getInvoiceNo());
            // Future: MainController.getInstance().loadInvoiceDetails(inv.getInvoiceNo());
        });

        actionDots.setOnMouseClicked(e -> {
            contextMenu.show(actionDots, Side.BOTTOM, 0, 0);
        });

        row.getChildren().addAll(idLabel, dateLabel, amountLabel, pillContainer, actionDots);
        return row;
    }
}
