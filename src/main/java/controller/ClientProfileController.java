package controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;
import model.Client;
import model.Invoice;
import model.InvoiceMaster;
import model.Job;
import utils.ClientDeleteHelper;
import utils.CompanyDataLayout;
import utils.DBConnection;
import utils.DocumentNumbering;
import utils.InvoiceSummaryDialogUtil;
import utils.PaymentDetailsDialogUtil;
import utils.Toast;
import repository.ClientRepository;
import service.InvoiceBuilderService;
import service.InvoiceMasterService;
import service.NumberSequenceAllocationService;
import service.PdfInvoiceService;

import java.net.URL;
import java.io.File;
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
    @FXML private Label lblPipelineMore;
    
    @FXML private VBox cardBalance;

    @FXML private HBox breadcrumbContainer;
    @FXML private Button btnEditProfile;
    @FXML private Button btnDeleteClient;

    @FXML private VBox pipelineContainer;
    @FXML private VBox historyContainer;
    
    @FXML private Label lblAddress;
    @FXML private Label lblTaxId;
    @FXML private Label lblNotes;
    @FXML private Label lblClientId;

    private Client currentClient;
    private final ClientRepository clientRepo = new ClientRepository();

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

    @FXML
    private void handleViewAllJobs() {
        MainController mc = MainController.getInstance();
        if (mc == null) {
            return;
        }
        if (currentClient != null) {
            mc.loadViewJobFiltered(currentClient.getClientUuid());
        } else {
            mc.loadViewJob();
        }
    }

    @FXML
    private void handleBack(javafx.event.Event e) {
        MainController.getInstance().handleBack(e);
    }

    @FXML
    private void handleDeleteClient() {
        if (currentClient == null || !currentClient.hasClientUuid()) {
            return;
        }
        Window owner = btnDeleteClient != null && btnDeleteClient.getScene() != null
                ? btnDeleteClient.getScene().getWindow()
                : null;
        if (ClientDeleteHelper.confirmAndDelete(owner, currentClient, clientRepo)) {
            MainController.getInstance().loadViewClients();
        }
    }


    public void setClient(Client client) {
        this.currentClient = client;
        refreshProfile();
    }

    public void refreshProfile() {
        if (currentClient == null) return;
        
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, currentClient.getBusinessName(), () -> handleBack(null));
        
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

        String clientCodeDisplay = currentClient.getClientCode() != null && !currentClient.getClientCode().isBlank()
                ? currentClient.getClientCode() : "N/A";

        Platform.runLater(() -> {
            if (lblBusinessName != null) lblBusinessName.setText(name);
            if (lblInitial != null) lblInitial.setText(initial);
            if (lblClientId != null) lblClientId.setText(clientCodeDisplay);
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
        int totalActive = 0;
        try (Connection con = DBConnection.getConnection()) {
            String countSql = "SELECT COUNT(*) FROM jobs WHERE client_uuid = ? AND (status IS NULL OR LOWER(TRIM(status)) != 'completed')";
            try (java.sql.PreparedStatement ps = con.prepareStatement(countSql)) {
                ps.setString(1, currentClient.getClientUuid());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    totalActive = rs.getInt(1);
                }
            }
            String sql = "SELECT job_title, status, job_code FROM jobs WHERE client_uuid = ? AND (status IS NULL OR LOWER(TRIM(status)) != 'completed') ORDER BY created_at DESC LIMIT 2";
            try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, currentClient.getClientUuid());
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

        final int totalForUi = totalActive;
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
            if (lblPipelineMore != null) {
                int extra = totalForUi - 2;
                if (totalForUi > 2 && extra > 0) {
                    lblPipelineMore.setText("+" + extra + " more");
                    lblPipelineMore.setVisible(true);
                    lblPipelineMore.setManaged(true);
                } else {
                    lblPipelineMore.setText("");
                    lblPipelineMore.setVisible(false);
                    lblPipelineMore.setManaged(false);
                }
            }
            if (lblActiveJobs != null) lblActiveJobs.setText(String.valueOf(totalForUi));
            if (lblNextDue != null) lblNextDue.setText(activeJobs.isEmpty() ? "No pending jobs" : "Next due in " + (int)(Math.random()*5+1) + " days");
            if (lblTurnaround != null) lblTurnaround.setText("4.2 days");
        });
    }

    private VBox createJobPipelineCard(Job job) {
        VBox card = new VBox();
        card.getStyleClass().add("pipeline-card");
        
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        VBox nameBox = new VBox(1);
        HBox.setHgrow(nameBox, Priority.ALWAYS);
        Label title = new Label(job.getJobTitle() != null ? job.getJobTitle() : "Untitled Job");
        title.getStyleClass().add("job-card-title");
        Label specs = new Label("Job " + (job.getJobNo() != null ? job.getJobNo() : "---"));
        specs.getStyleClass().add("job-card-specs");
        nameBox.getChildren().addAll(title, specs);
        
        String statusText = job.getStatus() == null ? "START" : job.getStatus().toUpperCase();
        boolean isCompleted = statusText.contains("COMPLETED") || statusText.contains("SHIPPED");
        
        Label statusTag = new Label(isCompleted ? "COMPLETED" : "IN PRODUCTION");
        statusTag.getStyleClass().add(isCompleted ? "status-pill-green" : "status-pill-subtle");
        
        header.getChildren().addAll(nameBox, statusTag);
        
        VBox progressSection = new VBox(4);
        progressSection.setStyle("-fx-padding: 8 0 0 0;");
        
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

    /** Same folder key as invoice PDFs: {@code businessName (clientName)} when both exist. */
    private String clientFolderDisplayName() {
        if (currentClient == null) {
            return "Unknown_Client";
        }
        String businessName = currentClient.getBusinessName();
        String clientName = currentClient.getClientName();
        if (businessName != null && !businessName.isBlank()
                && clientName != null && !clientName.isBlank()) {
            return businessName + " (" + clientName + ")";
        }
        if (businessName != null && !businessName.isBlank()) {
            return businessName;
        }
        if (clientName != null && !clientName.isBlank()) {
            return clientName;
        }
        return "Unknown_Client";
    }

    private void loadInvoiceHistory() {
        if (historyContainer == null) return;
        
        List<Invoice> history = new ArrayList<>();
        NumberSequenceAllocationService receiptNumbers = new NumberSequenceAllocationService();
        try (Connection con = DBConnection.getConnection()) {
            String sql = "SELECT invoice_no, invoice_date, amount, status, payment_status, 'INVOICE' as row_type, CAST(NULL AS TEXT) as payment_uuid FROM invoice_master WHERE client_uuid = ? AND is_void = 0 "
                         + "UNION ALL "
                         + "SELECT '', payment_date, amount, 'PAID', 'PAID', 'PAYMENT' as row_type, uuid as payment_uuid FROM payments WHERE client_uuid = ? "
                         + "ORDER BY invoice_date DESC LIMIT 10";
                         
            try (java.sql.PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, currentClient.getClientUuid());
                ps.setString(2, currentClient.getClientUuid());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Invoice inv = new Invoice();
                    String payUuid = rs.getString(7);
                    String dateStr = rs.getString(2);
                    if (dateStr != null && !dateStr.isEmpty()) {
                        try { inv.setInvoiceDate(LocalDate.parse(dateStr.contains(" ") ? dateStr.split(" ")[0] : dateStr)); } catch (Exception e) {}
                    }
                    if (payUuid != null) {
                        inv.setStandalonePaymentUuid(payUuid);
                        // Still need a numeric ID for some legacy logic or just use UUID
                        inv.setInvoiceNo(receiptNumbers.resolvePaymentReceiptNo(con, payUuid, inv.getInvoiceDate(), false));
                    } else {
                        inv.setInvoiceNo(rs.getString(1));
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

        Label idLabel = new Label(inv.getInvoiceNo());
        idLabel.getStyleClass().add("invoice-id-label");
        idLabel.setMinWidth(200);
        idLabel.setPrefWidth(200);
        idLabel.setMaxWidth(200);
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

        final String rowKey = inv.getInvoiceNo();
        final String paymentUuid = inv.getStandalonePaymentUuid();

        // Action Menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem viewItem = new MenuItem("View Details");
        boolean isPaymentRow = paymentUuid != null;
        MenuItem downloadItem = new MenuItem(isPaymentRow ? "Download Receipt" : "Download Invoice");
        contextMenu.getItems().addAll(viewItem, downloadItem);

        viewItem.setOnAction(e -> {
            javafx.stage.Window w = historyContainer.getScene() != null ? historyContainer.getScene().getWindow() : null;
            if (w == null) {
                return;
            }
            if (paymentUuid != null) {
                PaymentDetailsDialogUtil.showByUuid(w, paymentUuid);
            } else if (rowKey != null && !rowKey.isBlank()) {
                InvoiceSummaryDialogUtil.showForInvoiceNo(w, rowKey);
            }
        });

        downloadItem.setOnAction(e -> {
            javafx.stage.Window w = historyContainer.getScene() != null ? historyContainer.getScene().getWindow() : null;
            Stage stage = w instanceof Stage ? (Stage) w : null;
            if (paymentUuid != null) {
                try {
                    LocalDate payDate = inv.getInvoiceDate() != null ? inv.getInvoiceDate() : LocalDate.now();
                    File out = CompanyDataLayout.paymentReceiptPdfPath(clientFolderDisplayName(), payDate, rowKey);
                    new PdfInvoiceService().writePaymentReceiptPdf(paymentUuid, out);
                    if (stage != null) {
                        Toast.showSmall(stage, "Receipt saved");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    new Alert(Alert.AlertType.ERROR, "Could not save PDF: " + ex.getMessage(), ButtonType.OK)
                            .showAndWait();
                }
            } else if (rowKey != null && !rowKey.isBlank()) {
                try {
                    InvoiceMasterService ims = new InvoiceMasterService();
                    InvoiceMaster master = ims.getInvoiceByInvoiceNo(rowKey);
                    if (master == null) {
                        new Alert(Alert.AlertType.INFORMATION, "Invoice not found.", ButtonType.OK).showAndWait();
                        return;
                    }
                    InvoiceBuilderService builder = new InvoiceBuilderService();
                    Invoice full = builder.buildInvoiceFromMasterForPdfExport(master.getUuid());
                    File created;
                    if (model.MasterDocumentSeries.GST_INVOICE == full.getMasterDocumentSeries()) {
                        created = new service.GstPdfInvoiceService().generateGstInvoice(full);
                    } else {
                        created = new PdfInvoiceService().generateSingleInvoicePDF(full);
                    }
                    if (stage != null) {
                        Toast.showSmall(stage, "Invoice PDF saved");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    new Alert(Alert.AlertType.ERROR, "Could not save PDF: " + ex.getMessage(), ButtonType.OK)
                            .showAndWait();
                }
            }
        });

        actionDots.setOnMouseClicked(e -> {
            contextMenu.show(actionDots, Side.BOTTOM, 0, 0);
        });

        row.getChildren().addAll(idLabel, dateLabel, amountLabel, pillContainer, actionDots);
        return row;
    }
}
