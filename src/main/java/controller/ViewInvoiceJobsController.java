package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;

import model.Client;
import model.InvoiceMaster;
import model.Job;

import service.ClientService;
import service.InvoiceMasterService;
import service.JobService;

import utils.Toast;

import java.time.LocalDate;
import java.util.List;

public class ViewInvoiceJobsController {

    private final ClientService clientService = new ClientService();
    private final InvoiceMasterService invoiceMasterService = new InvoiceMasterService();
    private final JobService jobService = new JobService();

    @FXML
    private ComboBox<Client> clientComboBox;
    @FXML
    private ComboBox<InvoiceMaster> invoiceComboBox;
    @FXML
    private Label resultLabel;
    @FXML
    private TableView<Job> jobsTable;

    @FXML
    private javafx.scene.layout.HBox searchModeBox;
    @FXML
    private javafx.scene.layout.VBox editModeBox;
    @FXML
    private ComboBox<Client> txtClientName;
    @FXML
    private ComboBox<InvoiceMaster> txtInvoiceNo;
    @FXML
    private DatePicker dpInvoiceDate;
    @FXML
    private javafx.scene.layout.Region invoiceIconSearch, invoiceIconEdit;
    @FXML
    private TextField txtProcessStatus;

    @FXML
    private TableColumn<Job, Boolean> selectCol;
    @FXML
    private TableColumn<Job, Job> idCol;
    @FXML
    private TableColumn<Job, String> jobNoCol;
    @FXML
    private TableColumn<Job, String> titleCol;
    @FXML
    private TableColumn<Job, LocalDate> dateCol;
    @FXML
    private TableColumn<Job, Double> jobTotalCol;
    @FXML
    private TableColumn<Job, String> statusCol;
    @FXML
    private TableColumn<Job, String> remarksCol;
    @FXML
    private TableColumn<Job, Void> actionsCol;

    @FXML
    private javafx.scene.layout.HBox breadcrumbContainer;
    @FXML
    private javafx.scene.layout.HBox jobActionsBox;
    @FXML
    private Button btnJobView, btnJobEdit, btnJobCancel, btnJobUnlink;
    @FXML
    private Button btnSave, btnDiscard;
    @FXML
    private Label paginationInfoLabel;
    @FXML
    private TextField txtGotoPage;

    private final ObservableList<Job> tableData = FXCollections.observableArrayList();

    public static InvoiceMaster pendingPrefillInvoice;
    public static boolean viewOnlyMode = false;
    private boolean isViewOnly = false;
    private InvoiceMaster currentEditInvoice;
    private final java.util.Set<String> jobsToCancel = new java.util.HashSet<>();
    private final java.util.Map<String, String> jobsToUpdateStatus = new java.util.HashMap<>();
    private final java.util.Set<String> jobsToUnlink = new java.util.HashSet<>();
    private final java.util.Set<String> jobsToAdd = new java.util.HashSet<>();
    private boolean isUpdating = false;
    @FXML
    private Button btnAddJob;

    @FXML
    private void initialize() {
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null,
                () -> MainController.getInstance().handleBack(null));
        setupTable();
        setupClientComboBox();
        setupInvoiceComboBox();
        setupAutoPopupDatePicker(dpInvoiceDate);
        setupEditModeComboBoxes();
        
        // Robust popup width synchronization
        syncPopupWidth(clientComboBox);
        syncPopupWidth(invoiceComboBox);
        syncPopupWidth(txtClientName);
        syncPopupWidth(txtInvoiceNo);

        loadClients();

        clientComboBox.valueProperty().addListener((obs, oldV, newV) -> onClientSelected(newV));
        invoiceComboBox.valueProperty().addListener((obs, oldV, newV) -> onInvoiceSelected(newV));

        // Stylesheet dynamically added was removed to prevent conflicts with Taste
        // design

        setupJobActionBarStyles();

        // ✅ Job selection logic
        jobsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            updateJobActionBar(newSel);
        });

        // ✅ Double click to edit job
        jobsTable.setRowFactory(tv -> {
            TableRow<Job> row = new TableRow<>();
            row.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (e.getButton() != MouseButton.PRIMARY || row.isEmpty() || jobsTable == null) {
                    return;
                }
                if (e.isShiftDown() || e.isControlDown() || e.isShortcutDown()) {
                    return;
                }
                Job item = row.getItem();
                if (item != null) {
                    int index = row.getIndex();
                    if (jobsTable.getSelectionModel().isSelected(index)) {
                        jobsTable.getSelectionModel().clearSelection(index);
                    } else {
                        jobsTable.getSelectionModel().clearAndSelect(index);
                    }
                }
            });
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Job item = row.getItem();
                    String invoiceStatus = currentEditInvoice != null && currentEditInvoice.getStatus() != null
                            ? currentEditInvoice.getStatus().toUpperCase()
                            : "";

                    if (isViewOnly || "FINAL".equals(invoiceStatus) || "SENT".equals(invoiceStatus)
                            || "PAID".equals(invoiceStatus)) {
                        showJobDetails(item);
                    } else {
                        openEditJobScreen(item);
                    }
                }
            });
            return row;
        });

        if (pendingPrefillInvoice != null) {
            final InvoiceMaster prefill = pendingPrefillInvoice;
            pendingPrefillInvoice = null;
            currentEditInvoice = prefill;

            searchModeBox.setVisible(false);
            searchModeBox.setManaged(false);
            editModeBox.setVisible(true);
            editModeBox.setManaged(true);
            jobActionsBox.setVisible(true);
            jobActionsBox.setManaged(true);

            this.isViewOnly = viewOnlyMode;
            viewOnlyMode = false; // Reset for next use

            onInvoiceSelected(prefill);
        } else {
            isViewOnly = false;

            searchModeBox.setVisible(true);
            searchModeBox.setManaged(true);
            editModeBox.setVisible(false);
            editModeBox.setManaged(false);
            jobActionsBox.setVisible(false);
            jobActionsBox.setManaged(false);
        }
    }

    private void setupTable() {
        // ✅ Checkbox Column
        selectCol.setCellValueFactory(f -> f.getValue().selectedProperty());
        selectCol.setCellFactory(col -> new TableCell<Job, Boolean>() {
            private final CheckBox checkBox = new CheckBox();
            private final javafx.beans.value.ChangeListener<Boolean> rowSelectionListener = (obs, oldVal, newVal) -> {
                checkBox.setSelected(newVal != null && newVal);
            };

            {
                checkBox.getStyleClass().add("vi-row-select-cb");
                checkBox.setFocusTraversable(false);
                checkBox.setMnemonicParsing(false);
                
                checkBox.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        e.consume();
                        TableRow<?> row = getTableRow();
                        if (row != null && !row.isEmpty()) {
                            int index = row.getIndex();
                            if (jobsTable != null) {
                                jobsTable.requestFocus();
                            }
                            if (jobsTable.getSelectionModel().isSelected(index)) {
                                jobsTable.getSelectionModel().clearSelection(index);
                            } else {
                                jobsTable.getSelectionModel().select(index);
                            }
                        }
                    }
                });
                checkBox.addEventFilter(MouseEvent.MOUSE_RELEASED, MouseEvent::consume);
                checkBox.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);

                tableRowProperty().addListener((obs, oldRow, newRow) -> {
                    if (oldRow != null) {
                        oldRow.selectedProperty().removeListener(rowSelectionListener);
                    }
                    if (newRow != null) {
                        newRow.selectedProperty().addListener(rowSelectionListener);
                        checkBox.setSelected(newRow.isSelected());
                    } else {
                        checkBox.setSelected(false);
                    }
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    TableRow<?> row = getTableRow();
                    if (row != null) {
                        checkBox.setSelected(row.isSelected());
                    } else {
                        checkBox.setSelected(false);
                    }
                    setAlignment(javafx.geometry.Pos.CENTER);
                    setGraphic(checkBox);
                }
            }
        });
        selectCol.setEditable(true);
        jobsTable.setEditable(true);

        idCol.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cd.getValue()));
        idCol.setCellFactory(col -> new TableCell<Job, Job>() {
            @Override
            protected void updateItem(Job job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) {
                    setText(null);
                } else {
                    setText(String.valueOf(getIndex() + 1));
                    setStyle("-fx-text-fill: #A7A69D; -fx-font-weight: 500;");
                }
            }
        });

        // ✅ Job No: Orange/Terracotta bold
        jobNoCol.setCellValueFactory(new PropertyValueFactory<>("jobNo"));
        jobNoCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    setText(item);
                    setStyle("-fx-text-fill: #CD7B4E; -fx-font-weight: 800; -fx-font-size: 13px;");
                }
            }
        });

        // ✅ Job Title: Icon + Title + Category
        titleCol.setCellValueFactory(new PropertyValueFactory<>("jobTitle"));
        titleCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    javafx.scene.layout.HBox container = new javafx.scene.layout.HBox(12);
                    container.setAlignment(javafx.geometry.Pos.CENTER_LEFT); // Left alignment for nice wrapping
                    setStyle("-fx-alignment: CENTER_LEFT;"); // Center left in cell

                    // Icon (No background, larger size)
                    javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
                    icon.setContent("M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z");
                    icon.setFill(javafx.scene.paint.Color.web("#9D4EDD"));
                    icon.setScaleX(1.1); icon.setScaleY(1.1); // Made it bigger

                    javafx.scene.layout.VBox textNodes = new javafx.scene.layout.VBox(2);
                    textNodes.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    javafx.scene.layout.HBox.setHgrow(textNodes, javafx.scene.layout.Priority.ALWAYS);
                    textNodes.setMaxWidth(Double.MAX_VALUE);

                    Label lblTitle = new Label(item);
                    lblTitle.setStyle("-fx-font-weight: 800; -fx-text-fill: #3E312D; -fx-font-size: 13px;");
                    lblTitle.setWrapText(true);
                    lblTitle.maxWidthProperty().bind(col.widthProperty().subtract(60));

                    Label lblSub = new Label("Printing Job");
                    lblSub.setStyle("-fx-text-fill: #A7A69D; -fx-font-size: 11px;");
                    textNodes.getChildren().addAll(lblTitle, lblSub);

                    container.getChildren().addAll(icon, textNodes);
                    setGraphic(container);
                }
            }
        });

        // ✅ Job Date: Icon + Date
        dateCol.setCellValueFactory(new PropertyValueFactory<>("jobDate"));
        dateCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    javafx.scene.layout.HBox container = new javafx.scene.layout.HBox(8);
                    container.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
                    icon.setContent("M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zM7 10h5v5H7z");
                    icon.setFill(javafx.scene.paint.Color.web("#A7A69D"));
                    icon.setScaleX(0.7); icon.setScaleY(0.7);
                    Label lblDate = new Label(item.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    lblDate.setStyle("-fx-text-fill: #3E312D; -fx-font-weight: 600; -fx-font-size: 12px;");
                    container.getChildren().addAll(icon, lblDate);
                    setGraphic(container);
                }
            }
        });

        // ✅ Job Total: Bold + Incl. GST
        jobTotalCol.setCellValueFactory(new PropertyValueFactory<>("jobTotal"));
        jobTotalCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    javafx.scene.layout.VBox container = new javafx.scene.layout.VBox(1);
                    container.setAlignment(javafx.geometry.Pos.CENTER); // Centered content
                    setStyle("-fx-alignment: CENTER;");
                    Label lblAmt = new Label(String.format("₹ %,.2f", item));
                    lblAmt.setStyle("-fx-font-weight: 900; -fx-text-fill: #CD7B4E; -fx-font-size: 13px;");
                    Label lblGst = new Label("(Incl. GST)");
                    lblGst.setStyle("-fx-text-fill: #A7A69D; -fx-font-size: 10px;");
                    container.getChildren().addAll(lblAmt, lblGst);
                    setGraphic(container);
                }
            }
        });

        // ✅ Status: Pill Style
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label pill = new Label(item.toUpperCase());
                    String color = "#FA8C16"; // Orange
                    String bg = "#FFF2E8";
                    if (item.equalsIgnoreCase("Invoiced") || item.equalsIgnoreCase("Completed")) {
                        color = "#52C41A"; bg = "#F6FFED";
                    } else if (item.equalsIgnoreCase("Cancelled")) {
                        color = "#F5222D"; bg = "#FFF1F0";
                    }
                    pill.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + color + "; " +
                            "-fx-padding: 4 10; -fx-background-radius: 6; -fx-font-weight: 800; -fx-font-size: 11px;");
                    setStyle("-fx-alignment: CENTER;");
                    setGraphic(pill);
                }
            }
        });

        remarksCol.setCellValueFactory(new PropertyValueFactory<>("remarks"));
        remarksCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else if (item == null || item.isBlank()) {
                    setText("—");
                    setStyle("-fx-text-fill: #A7A69D; -fx-alignment: CENTER;");
                    setWrapText(false);
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #3E312D; -fx-font-size: 12px; -fx-alignment: CENTER;");
                    setWrapText(true);
                }
            }
        });

        // ✅ Actions Column: Button row
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.layout.HBox container = new javafx.scene.layout.HBox(8);
            {
                container.setAlignment(javafx.geometry.Pos.CENTER); // Centered content
                setStyle("-fx-alignment: CENTER;");
                Button btnV = createCircularBtn("M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z", "#00BCD4");
                Button btnE = createCircularBtn("M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z", "#FA8C16");
                Button btnM = createCircularBtn("M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z", "#A7A69D");
                
                btnV.setOnAction(e -> {
                    TableRow<?> row = getTableRow();
                    if (row != null && !row.isEmpty()) {
                        Job rowJob = (Job) row.getItem();
                        if (rowJob != null) {
                            showJobDetails(rowJob);
                        }
                    }
                });
                btnE.setOnAction(e -> {
                    TableRow<?> row = getTableRow();
                    if (row != null && !row.isEmpty()) {
                        Job rowJob = (Job) row.getItem();
                        if (rowJob != null) {
                            openEditJobScreen(rowJob);
                        }
                    }
                });
                
                container.getChildren().addAll(btnV, btnE, btnM);
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : container);
            }
        });

        jobsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        jobsTable.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener.Change<? extends Job> c) -> {
            for (Job job : tableData) {
                if (job != null) {
                    job.setSelected(jobsTable.getSelectionModel().getSelectedItems().contains(job));
                }
            }
        });
        jobsTable.setItems(tableData);
        jobsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        jobsTable.setFixedCellSize(80); // Optimal editorial density for wrapping
    }

    private Button createCircularBtn(String path, String color) {
        Button btn = new Button();
        javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
        svg.setContent(path);
        svg.setFill(javafx.scene.paint.Color.web(color));
        svg.setScaleX(0.7); svg.setScaleY(0.7);
        btn.setGraphic(svg);
        btn.getStyleClass().add("row-action-btn");
        return btn;
    }

    private void setupJobActionBarStyles() {
        // Styles are now handled purely by CSS classes in view_job.css
    }

    private void updateJobActionBar(Job job) {
        if (job == null) {
            btnJobView.setDisable(true);
            btnJobEdit.setDisable(true);
            btnJobCancel.setDisable(true);
            btnJobUnlink.setDisable(true);
            return;
        }

        btnJobView.setDisable(false);
        btnJobUnlink.setDisable(false);

        String status = job.getStatus() != null ? job.getStatus().toLowerCase() : "";
        boolean isCancelled = status.startsWith("cancel");
        // boolean isCompleted = status.equals("completed");
        // boolean isInProgress = status.equals("in progress");
        boolean isInvoiced = job.getInvoiceUuid() != null && !job.getInvoiceUuid().isBlank();

        boolean isInvoiceDrafted = status.equals("invoice drafted") || status.equals("invoice_drafted");

        if (isInvoiced || status.equals("invoiced")) {
            // btnJobStart removed

            // Allow cancelling if invoice is Draft
            if (currentEditInvoice != null) {
                String invStatus = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toUpperCase()
                        : "";
                btnJobCancel.setDisable(!"DRAFT".equals(invStatus));
            } else {
                btnJobCancel.setDisable(true);
            }

            // Allow editing only if invoice is Draft/Final
            if (currentEditInvoice != null) {
                String invStatus = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toLowerCase()
                        : "";
                boolean canEditInvoiced = invStatus.equals("draft") || invStatus.equals("final");
                btnJobEdit.setDisable(!canEditInvoiced);
            } else {
                btnJobEdit.setDisable(true);
            }
        } else if (isInvoiceDrafted) {
            // btnJobStart removed

            // Allow cancelling if invoice is Draft
            if (currentEditInvoice != null) {
                String invS = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toUpperCase()
                        : "";
                btnJobCancel.setDisable(!"DRAFT".equals(invS));
            } else {
                btnJobCancel.setDisable(true);
            }

            // Allow editing if the parent invoice is Draft/Final
            if (currentEditInvoice != null) {
                String invS = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toUpperCase()
                        : "";
                boolean canEdit = "DRAFT".equals(invS) || "FINAL".equals(invS);
                btnJobEdit.setDisable(!canEdit);
            } else {
                btnJobEdit.setDisable(true);
            }
        } else {
            // boolean disableProgressBtns = isCancelled || isCompleted;
            // btnJobStart removed
            btnJobEdit.setDisable(isCancelled);
            btnJobCancel.setDisable(isCancelled);
        }

        // Final check on action bar based on Invoice Status and View Mode
        if (currentEditInvoice != null) {
            String invStatus = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toUpperCase()
                    : "";
            boolean isDraft = "DRAFT".equals(invStatus);
            boolean isLocked = "FINAL".equals(invStatus) || "SENT".equals(invStatus) || "PAID".equals(invStatus)
                    || isViewOnly;

            if (isDraft) {
                // For draft invoices, we allow Edit and Unlink
                btnJobEdit.setDisable(false);
                btnJobUnlink.setDisable(false);
                btnJobCancel.setDisable(false);
                btnAddJob.setDisable(false);
            } else if (isLocked) {
                btnJobEdit.setDisable(true);
                btnJobCancel.setDisable(true);
                btnJobUnlink.setDisable(true);
                btnAddJob.setDisable(true);
            } else {
                btnJobEdit.setDisable(false);
                btnJobUnlink.setDisable(false);
                btnAddJob.setDisable(false);
            }
        } else if (isViewOnly) {
            btnJobEdit.setDisable(true);
            btnJobCancel.setDisable(true);
            btnJobUnlink.setDisable(true);
            btnAddJob.setDisable(true);
        }
    }

    @FXML
    private void handleJobViewAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null)
            showJobDetails(selected);
    }

    @FXML
    private void handleJobEditAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null)
            openEditJobScreen(selected);
    }

    @FXML
    private void handleJobStartAction() {
        // Method kept for FXML compatibility if needed, but logic removed
    }

    @FXML
    private void handleJobCancelAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            jobsToCancel.add(selected.getUuid());
            selected.setStatus("Cancelled");
            tableData.remove(selected);
            resultLabel.setText("Showing " + tableData.size() + " jobs");
            toast("Job marked for cancellation and removal. Click Save to persist.");
        }
    }

    @FXML
    private void handleJobUnlinkAction() {
        Job selected = jobsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            String msg = "Unlink job " + selected.getJobNo() + " from this invoice?";
            if (tableData.size() <= 1) {
                String invStatus = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toUpperCase()
                        : "";
                String invNo = currentEditInvoice.getInvoiceNo() != null
                        ? currentEditInvoice.getInvoiceNo().toUpperCase()
                        : "";
                boolean isTemp = "DRAFT".equals(invStatus) && invNo.startsWith("TEMP-");

                if (isTemp) {
                    msg = "Unlinking the last job will result in the temporary invoice being deleted and jobs moved back to Completed status. Proceed?";
                } else if (!"DRAFT".equals(invStatus) && !"FINAL".equals(invStatus)) {
                    msg = "Unlinking the last job will result in the Invoice being CANCELLED. Proceed?";
                } else {
                    msg = "Unlink the last job? The invoice will remain as an empty " + invStatus
                            + " invoice. Proceed?";
                }
            }

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
            alert.setHeaderText("Caution: Unlink Job");
            alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            alert.getDialogPane().setStyle("-fx-font-family: 'Segoe UI';");
            alert.showAndWait().ifPresent(type -> {
                if (type == ButtonType.YES) {
                    jobsToUnlink.add(selected.getUuid());
                    tableData.remove(selected);
                    resultLabel.setText("Showing " + tableData.size() + " jobs");
                    toast("Job unlinked locally. Click Save to persist.");
                }
            });
        }
    }

    @FXML
    private void handleAddJobsToInvoice() {
        if (currentEditInvoice == null)
            return;

        String invStatus = currentEditInvoice.getStatus() != null ? currentEditInvoice.getStatus().toLowerCase() : "";
        if (!invStatus.equals("draft") && !invStatus.equals("final")) {
            toast("Adding jobs is only allowed for Draft or Final invoices.");
            return;
        }

        List<Job> availableJobs = jobService.getCompletedJobsByClient(currentEditInvoice.getClientId());
        if (availableJobs.isEmpty()) {
            toast("No completed jobs found for this client.");
            return;
        }

        // Show choice dialog
        Dialog<List<Job>> dialog = new Dialog<>();
        dialog.setTitle("Add Jobs to Invoice");
        dialog.setHeaderText("Select completed jobs to add to invoice " + currentEditInvoice.getInvoiceNo());

        ListView<Job> listView = new ListView<>(FXCollections.observableArrayList(availableJobs));
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Job item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null)
                    setText(null);
                else
                    setText(item.getJobNo() + " - " + item.getJobTitle() + " (₹" + item.getJobTotal() + ")");
            }
        });
        listView.setPrefHeight(250);

        dialog.getDialogPane().setContent(listView);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK)
                return listView.getSelectionModel().getSelectedItems();
            return null;
        });

        dialog.showAndWait().ifPresent(selected -> {
            for (Job j : selected) {
                if (!tableData.contains(j)) {
                    jobsToAdd.add(j.getUuid());
                    j.setStatus("Invoiced"); // Local update for UI
                    tableData.add(j);
                }
            }
            resultLabel.setText("Showing " + tableData.size() + " jobs");
            toast("Jobs added locally. Click Save to persist.");
        });
    }

    private javafx.scene.Node createIcon(String pathStr) {
        javafx.scene.shape.SVGPath svg = new javafx.scene.shape.SVGPath();
        svg.setContent(pathStr);
        svg.setFill(javafx.scene.paint.Color.WHITE);
        svg.setScaleX(0.7);
        svg.setScaleY(0.7);
        javafx.scene.layout.StackPane pane = new javafx.scene.layout.StackPane(svg);
        pane.setPrefSize(16, 16);
        pane.setMinSize(16, 16);
        pane.setMaxSize(16, 16);
        return pane;
    }

    private void showJobDetails(Job job) {
        service.JobItemService jis = new service.JobItemService();
        java.util.List<model.JobItem> items = jis.getJobItems(job.getUuid());

        String clientName = "Unknown Client";
        if (job.getClientId() != null) {
            for (Client c : clientComboBox.getItems()) {
                if (job.getClientId() != null && job.getClientId().equals(c.getClientUuid())) {
                    clientName = c.getBusinessName();
                    break;
                }
            }
        }

        String formattedDate = "-";
        if (job.getJobDate() != null) {
            formattedDate = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy").format(job.getJobDate());
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Job View - " + job.getJobNo());
        dialog.setHeaderText(
                "Client: " + clientName + "\nJob Title: " + job.getJobTitle() + "\nDate: " + formattedDate);

        DialogPane dialogPane = dialog.getDialogPane();
        try {
            dialogPane.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
            dialogPane.getStylesheets().add(getClass().getResource("/css/invoice_genration.css").toExternalForm());
        } catch (Exception e) {
        }
        dialogPane.setStyle("-fx-background-color: #1e1e1e; -fx-border-color: #444444; -fx-border-width: 1px;");

        TableView<model.JobItem> table = new TableView<>();

        TableColumn<model.JobItem, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(260);

        TableColumn<model.JobItem, Double> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(new PropertyValueFactory<>("amount"));
        amtCol.setPrefWidth(100);

        table.getColumns().add(descCol);
        table.getColumns().add(amtCol);
        table.setItems(FXCollections.observableArrayList(items));
        table.setPrefHeight(200);

        double total = items.stream().mapToDouble(model.JobItem::getAmount).sum();
        Label lblTotal = new Label("Grand Total: ₹" + String.format("%.2f", total));
        lblTotal.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-alignment: center-right; -fx-pref-width: 360px; -fx-text-fill: white;");

        javafx.scene.layout.VBox vbox = new javafx.scene.layout.VBox(10, table, lblTotal);
        dialogPane.setContent(vbox);
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);

        javafx.scene.Node header = dialogPane.lookup(".header-panel");
        if (header != null)
            header.setStyle("-fx-background-color: #2b2b2b;");
        javafx.scene.Node headerText = dialogPane.lookup(".header-panel .label");
        if (headerText != null)
            headerText.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");

        javafx.scene.Node graphic = dialogPane.lookup(".header-panel .graphic-container");
        if (graphic != null) {
            graphic.setManaged(false);
            graphic.setVisible(false);
        }

        Button closeBtn = (Button) dialogPane.lookupButton(ButtonType.CLOSE);
        if (closeBtn != null) {
            closeBtn.setStyle("-fx-background-color: #333333; -fx-text-fill: white; -fx-cursor: hand;");
        }

        dialog.showAndWait();
    }

    private void openEditJobScreen(Job job) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/edit_job.fxml"));
            javafx.scene.Parent view = loader.load();
            EditJobController controller = loader.getController();
            controller.openForEdit(job);

            utils.NavigationManager.getInstance().push("/fxml/edit_job.fxml", "Edit Job", "Editing Job...",
                    "billingSidebar");
            utils.NavigationManager.getInstance().updateCurrentState(view, controller);

            MainController.getInstance().setCenterView(view);
        } catch (Exception ex) {
            ex.printStackTrace();
            toast("Failed to open Edit Job");
        }
    }

    private void setupClientComboBox() {
        clientComboBox.setEditable(false);
        clientComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Client item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Select Client");
                } else {
                    setText(item.getBusinessName());
                }
            }
        });
        clientComboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Client item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getBusinessName() + " (" + item.getClientName() + ")");
                }
            }
        });
    }

    private void setupEditModeComboBoxes() {
        txtClientName.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Client item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select Client" : item.getBusinessName());
            }
        });
        txtClientName.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Client item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getBusinessName() + " (" + item.getClientName() + ")");
            }
        });
        txtClientName.setConverter(new javafx.util.StringConverter<Client>() {
            @Override public String toString(Client c) { return c == null ? "" : c.getBusinessName(); }
            @Override public Client fromString(String s) { return null; }
        });

        txtInvoiceNo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(InvoiceMaster item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select Invoice..." : item.getInvoiceNo());
            }
        });
        txtInvoiceNo.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(InvoiceMaster item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getInvoiceNo() + " - ₹" + item.getAmount());
            }
        });
        txtInvoiceNo.setConverter(new javafx.util.StringConverter<InvoiceMaster>() {
            @Override public String toString(InvoiceMaster i) { return i == null ? "" : i.getInvoiceNo(); }
            @Override public InvoiceMaster fromString(String s) { return null; }
        });

        txtClientName.valueProperty().addListener((obs, oldV, newV) -> {
            if (isUpdating) return;
            if (newV != null) {
                isUpdating = true;
                try {
                    clientComboBox.setValue(newV);
                    List<InvoiceMaster> invoices = invoiceMasterService.getInvoicesByClientId(newV.getClientUuid());
                    if (invoices.isEmpty()) {
                        txtInvoiceNo.getItems().clear();
                        txtInvoiceNo.setPromptText("No invoices found");
                        txtInvoiceNo.setDisable(true);
                        if (invoiceIconEdit != null) invoiceIconEdit.setStyle("-fx-background-color: #A7A69D; -fx-opacity: 0.5;");
                    } else {
                        txtInvoiceNo.getItems().setAll(invoices);
                        txtInvoiceNo.setPromptText("Select Invoice...");
                        txtInvoiceNo.setDisable(false);
                        if (invoiceIconEdit != null) invoiceIconEdit.setStyle("-fx-background-color: #CD7B4E; -fx-opacity: 1;");
                    }
                } finally {
                    isUpdating = false;
                }
            }
        });

        txtInvoiceNo.valueProperty().addListener((obs, oldV, newV) -> {
            if (isUpdating) return;
            if (newV != null) {
                isUpdating = true;
                try {
                    invoiceComboBox.setValue(newV);
                    onInvoiceSelected(newV);
                } finally {
                    isUpdating = false;
                }
            }
        });
    }

    private void setupInvoiceComboBox() {
        invoiceComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(InvoiceMaster item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select Invoice..." : item.getInvoiceNo() + " - ₹" + item.getAmount());
            }
        });
        invoiceComboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(InvoiceMaster item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.getInvoiceNo() + " - ₹" + item.getAmount() + " [" + item.getInvoiceDate() + "]");
            }
        });
        invoiceComboBox.setConverter(new javafx.util.StringConverter<InvoiceMaster>() {
            @Override public String toString(InvoiceMaster i) { return i == null ? "" : i.getInvoiceNo(); }
            @Override public InvoiceMaster fromString(String s) { return null; }
        });
    }

    private void loadClients() {
        List<Client> clients = clientService.getAllClients();
        clientComboBox.getItems().setAll(clients);
        txtClientName.getItems().setAll(clients);
    }

    private void onClientSelected(Client client) {
        invoiceComboBox.getSelectionModel().clearSelection();
        tableData.clear();
        resultLabel.setText("Showing 0 jobs");

        if (client == null) {
            invoiceComboBox.setDisable(true);
            return;
        }

        invoiceComboBox.setDisable(false);
        List<InvoiceMaster> invoices = invoiceMasterService.getInvoicesByClientId(client.getClientUuid());
        if (invoices.isEmpty()) {
            invoiceComboBox.getItems().clear();
            invoiceComboBox.setPromptText("No invoices for this client");
            if (invoiceIconSearch != null) invoiceIconSearch.setStyle("-fx-background-color: #A7A69D; -fx-opacity: 0.5;");
            toast("No invoices found for " + client.getBusinessName());
        } else {
            invoiceComboBox.getItems().setAll(invoices);
            invoiceComboBox.setPromptText("Select Invoice...");
            if (invoiceIconSearch != null) invoiceIconSearch.setStyle("-fx-background-color: #CD7B4E; -fx-opacity: 1;");
        }
    }

    private void onInvoiceSelected(InvoiceMaster invoice) {
        jobsToCancel.clear();
        jobsToUpdateStatus.clear();
        jobsToUnlink.clear();
        jobsToAdd.clear();
        tableData.clear();
        if (invoice == null) {
            editModeBox.setVisible(false);
            editModeBox.setManaged(false);
            jobActionsBox.setVisible(false);
            jobActionsBox.setManaged(false);
            resultLabel.setText("Showing 0 jobs");
            return;
        }

        // Show the info card and actions
        editModeBox.setVisible(true);
        editModeBox.setManaged(true);
        jobActionsBox.setVisible(true);
        jobActionsBox.setManaged(true);

        // Populate client name for search mode too
        if (clientComboBox.getValue() != null) {
            isUpdating = true;
            try {
                txtClientName.setValue(clientComboBox.getValue());
            } finally {
                isUpdating = false;
            }
        }

        populateInvoiceDetails(invoice);

        Thread thread = new Thread(() -> {
            try {
                List<Job> jobs = jobService.getJobsByInvoice(invoice);
                Platform.runLater(() -> {
                    tableData.setAll(jobs);
                    resultLabel.setText("Showing " + jobs.size() + " jobs");
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }

    private void populateInvoiceDetails(InvoiceMaster inv) {
        if (inv == null)
            return;

        // Populate Client Name automatically
        Client matchingClient = clientComboBox.getItems().stream()
                .filter(c -> inv.getClientId() != null && inv.getClientId().equals(c.getClientUuid()))
                .findFirst().orElse(null);

        isUpdating = true;
        try {
            if (matchingClient != null) {
                txtClientName.setValue(matchingClient);
                List<InvoiceMaster> invoices = invoiceMasterService.getInvoicesByClientId(matchingClient.getClientUuid());
                txtInvoiceNo.getItems().setAll(invoices);
            } else if (clientComboBox.getValue() != null) {
                txtClientName.setValue(clientComboBox.getValue());
                List<InvoiceMaster> invoices = invoiceMasterService.getInvoicesByClientId(clientComboBox.getValue().getClientUuid());
                txtInvoiceNo.getItems().setAll(invoices);
            }

            txtInvoiceNo.setValue(inv);
        } finally {
            isUpdating = false;
        }
        dpInvoiceDate.setValue(inv.getInvoiceDate());
        txtProcessStatus.setText(inv.getStatus());

        String stat = inv.getStatus() != null ? inv.getStatus().toUpperCase() : "";
        boolean isDraft = "DRAFT".equals(stat);

        // Locking logic
        boolean isLocked = !isDraft; // Consistent with user request to lock anything not Draft
        dpInvoiceDate.setDisable(isLocked);

        // Button Visibility
        boolean canSave = isDraft && !isViewOnly; // 🔥 Respect both status and view mode
        btnSave.setVisible(canSave);
        btnSave.setManaged(canSave);

        // For Discard/Close button
        btnDiscard.setVisible(true);
        btnDiscard.setManaged(true);
        if (isDraft) {
            btnDiscard.setText(pendingPrefillInvoice == null ? "Discard" : "Discard Changes");
        } else {
            btnDiscard.setText("Close");
        }

    }

    private void toast(String message) {
        Stage stage = (Stage) ((Node) clientComboBox).getScene().getWindow();
        Platform.runLater(() -> Toast.show(stage, message));
    }

    private void setupAutoPopupDatePicker(DatePicker dp) {
        dp.setEditable(false);

        dp.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null)
                    return;
                if (date.isAfter(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-opacity: 0.35;");
                }
            }
        });

        dp.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.isAfter(LocalDate.now())) {
                dp.setValue(oldVal);
            }
        });

        dp.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (!dp.isShowing())
                dp.show();
        });

        dp.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV && !dp.isShowing())
                dp.show();
        });
    }

    @FXML
    private void handleSaveChanges() {
        if (currentEditInvoice == null) {
            MainController.getInstance().handleBack(null);
            return;
        }

        // 🛑 Requirement: Revised invoices cannot be empty
        String invNo = currentEditInvoice.getInvoiceNo();
        if (invNo != null && invNo.contains("-R") && tableData.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Empty revised invoice not possible please cancel the invoice..");
            alert.showAndWait();
            return;
        }

        LocalDate newDate = dpInvoiceDate.getValue();
        boolean dateChanged = newDate != null && !newDate.equals(currentEditInvoice.getInvoiceDate());
        boolean jobsChanged = !jobsToCancel.isEmpty() || !jobsToUnlink.isEmpty();
        boolean hasAddedJobs = !jobsToAdd.isEmpty();
        boolean hasStatusChanges = !jobsToUpdateStatus.isEmpty();

        if (dateChanged || jobsChanged || hasAddedJobs || hasStatusChanges) {
            final String invoiceUuid = currentEditInvoice.getUuid();
            if (invoiceUuid == null || invoiceUuid.isBlank()) {
                toast("Error: invoice has no UUID — cannot save job links.");
                return;
            }
            try {
                utils.AtomicDB.runVoid(con -> {
                    // 1. Update Date
                    if (dateChanged) {
                        try (java.sql.PreparedStatement ps = con
                                .prepareStatement("UPDATE invoice_master SET invoice_date = ? WHERE uuid = ?")) {
                            ps.setString(1, newDate.toString());
                            ps.setString(2, invoiceUuid);
                            ps.executeUpdate();
                        }
                    }

                    // 2. Status Updates
                    if (hasStatusChanges) {
                        try (java.sql.PreparedStatement psS = con
                                .prepareStatement("UPDATE jobs SET status = ? WHERE uuid = ?")) {
                            for (java.util.Map.Entry<String, String> entry : jobsToUpdateStatus.entrySet()) {
                                psS.setString(1, entry.getValue());
                                psS.setString(2, entry.getKey());
                                psS.addBatch();
                            }
                            psS.executeBatch();
                        }
                    }

                    // 3. Cancel Jobs
                    if (!jobsToCancel.isEmpty()) {
                        try (java.sql.PreparedStatement psC = con.prepareStatement(
                                "UPDATE jobs SET status = 'Cancelled', invoice_uuid = NULL, sync_status = 'PENDING', "
                                        + "updated_at = datetime('now') WHERE uuid = ?")) {
                            for (String jobUuid : jobsToCancel) {
                                psC.setString(1, jobUuid);
                                psC.addBatch();
                                service.InvoiceMasterService.deleteInvoiceJobMapping(con, invoiceUuid, jobUuid);
                            }
                            psC.executeBatch();
                        }
                    }

                    // 4. Unlink Jobs
                    if (!jobsToUnlink.isEmpty()) {
                        try (java.sql.PreparedStatement psU = con.prepareStatement(
                                "UPDATE jobs SET invoice_uuid = NULL, status = 'Completed', sync_status = 'PENDING', "
                                        + "updated_at = datetime('now') WHERE uuid = ?")) {
                            for (String jobUuid : jobsToUnlink) {
                                psU.setString(1, jobUuid);
                                psU.addBatch();
                                service.InvoiceMasterService.deleteInvoiceJobMapping(con, invoiceUuid, jobUuid);
                            }
                            psU.executeBatch();
                        }
                    }

                    // 5. Add Jobs
                    if (hasAddedJobs) {
                        boolean isRevision = invNo != null && invNo.contains("-R");
                        String statusToSet = isRevision ? "Invoiced" : "Invoice Drafted";
                        invoiceMasterService.linkJobUuidsToInvoice(con, invoiceUuid,
                                new java.util.ArrayList<>(jobsToAdd), statusToSet);
                    }

                    // 6. Recalculate Totals
                    try (java.sql.PreparedStatement psT = con.prepareStatement(
                            """
                                    UPDATE invoice_master SET
                                      amount = (SELECT COALESCE(SUM(ji.amount), 0)
                                                FROM job_items ji
                                                JOIN jobs j ON ji.job_uuid = j.uuid
                                                WHERE j.invoice_uuid = ?),
                                      due_amount = (SELECT COALESCE(SUM(ji.amount), 0)
                                                    FROM job_items ji
                                                    JOIN jobs j ON ji.job_uuid = j.uuid
                                                    WHERE j.invoice_uuid = ?)
                                        + (SELECT COALESCE(SUM(amount), 0)
                                           FROM invoice_adjustments
                                           WHERE invoice_uuid = ? AND type = 'Debit Note')
                                        - (SELECT COALESCE(SUM(amount), 0)
                                           FROM invoice_adjustments
                                           WHERE invoice_uuid = ? AND type = 'Credit Note')
                                        - paid_amount
                                    WHERE uuid = ?
                                    """)) {
                        psT.setString(1, invoiceUuid);
                        psT.setString(2, invoiceUuid);
                        psT.setString(3, invoiceUuid);
                        psT.setString(4, invoiceUuid);
                        psT.setString(5, invoiceUuid);
                        psT.executeUpdate();
                    }

                    // 7. Cleanup
                    invoiceMasterService.deleteEmptyInvoices(con);
                });

                if (ViewInvoicesController.getInstance() != null) {
                    ViewInvoicesController.getInstance().refresh();
                }
                toast("Changes saved successfully!");
                MainController.getInstance().handleBack(null);
            } catch (Exception e) {
                e.printStackTrace();
                toast("Error saving changes: " + e.getMessage());
            }
        } else {
            MainController.getInstance().handleBack(null);
        }
    }

    @FXML
    private void handleDiscardChanges() {
        MainController.getInstance().handleBack(null);
    }

    public void refresh() {
        Platform.runLater(() -> {
            if (currentEditInvoice != null) {
                InvoiceMaster updated = invoiceMasterService.getInvoiceById(currentEditInvoice.getUuid());
                if (updated != null) {
                    currentEditInvoice = updated;
                    
                    isUpdating = true;
                    try {
                        String clientUuid = updated.getClientId();
                        if (clientUuid != null) {
                            List<InvoiceMaster> invoices = invoiceMasterService.getInvoicesByClientId(clientUuid);
                            invoiceComboBox.getItems().setAll(invoices);
                        }
                        invoiceComboBox.setValue(updated);
                    } finally {
                        isUpdating = false;
                    }

                    populateInvoiceDetails(updated);

                    // Re-load jobs
                    Thread thread = new Thread(() -> {
                        try {
                            List<Job> jobs = jobService.getJobsByInvoice(updated);
                            Platform.runLater(() -> {
                                tableData.setAll(jobs);
                                resultLabel.setText("Showing " + jobs.size() + " jobs");
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    thread.start();
                }
            } else {
                if (clientComboBox.getValue() != null) {
                    onClientSelected(clientComboBox.getValue());
                }
            }
        });
    }

    private void syncPopupWidth(ComboBox<?> comboBox) {
        comboBox.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                Platform.runLater(() -> {
                    Node popupContent = comboBox.lookup(".combo-box-popup");
                    if (popupContent != null) {
                        Node listView = popupContent.lookup(".list-view");
                        if (listView != null) {
                            // Match the width of the ComboBox exactly
                            double width = comboBox.getWidth();
                            listView.setStyle("-fx-min-width: " + width + "; -fx-pref-width: " + width + "; -fx-max-width: " + width + ";");
                        }
                    }
                });
            }
        });
    }
}
