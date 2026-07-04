package controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.geometry.Insets;
import javafx.scene.paint.Color;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Set;
import javafx.scene.input.MouseEvent;

public class GenerateGSTInvoiceController implements Initializable {

    @FXML
    private Button btnPreviewInvoice;
    @FXML
    private Button btnGenerateInvoice;

    @FXML
    private HBox breadcrumbContainer;
    @FXML
    private Button btnEditTerms;

    @FXML
    private TextField txtInvoiceNo;
    @FXML
    private DatePicker dpInvoiceDate;
    @FXML
    private ComboBox<String> comboPlaceOfSupply;
    @FXML
    private ComboBox<String> comboReverseCharge;
    @FXML
    private ComboBox<String> comboPaymentTerms;
    @FXML
    private DatePicker dpDueDate;
    @FXML
    private TextField txtVehicleDispatch;
    @FXML
    private TextField txtPoNo;
    @FXML
    private DatePicker dpPoDate;
    @FXML
    private TextField txtDispatchThrough;
    @FXML
    private TextField txtLrTrackingNo;
    @FXML
    private TextField txtRemarks;
    @FXML
    private TextField txtEwayBillNo;

    @FXML
    private ComboBox<String> comboCompanyFrom;
    @FXML
    private ComboBox<model.Client> comboShipTo;
    @FXML
    private ComboBox<model.Client> comboBillTo;

    // Dynamic address labels — Company (From)
    @FXML
    private Label lblCompanyAddress;
    @FXML
    private Label lblCompanyGstin;
    @FXML
    private Label lblCompanyState;
    @FXML
    private Label lblCompanyEmail;

    // Dynamic address labels — Ship To
    @FXML
    private Label lblShipToAddress;
    @FXML
    private Label lblShipToGstin;
    @FXML
    private Label lblShipToState;

    // Dynamic address labels — Bill To
    @FXML
    private Label lblBillToAddress;
    @FXML
    private Label lblBillToGstin;
    @FXML
    private Label lblBillToState;

    @FXML
    private FlowPane flowJobsContainer;

    @FXML
    private TableView<ItemRow> tableItems;
    @FXML
    private MenuButton btnAddItem;
    @FXML
    private Button btnImportJob;
    @FXML
    private Button btnClearAll;

    @FXML
    private TableView<HsnSummaryRow> tableHsnSummary;
    @FXML
    private ComboBox<model.BankDetails> comboBankDetails;
    @FXML
    private Label lblBankHolder;
    @FXML
    private Label lblBankAccountNo;
    @FXML
    private Label lblBankBranchIfsc;

    // Invoice Summary labels
    @FXML
    private Label lblTotalQty;
    @FXML
    private Label lblTotalItems;
    @FXML
    private Label lblTaxable;
    @FXML
    private Label lblCgst;
    @FXML
    private Label lblSgst;
    @FXML
    private Label lblIgst;
    @FXML
    private Label lblRoundOff;
    @FXML
    private Label lblGrandTotal;
    @FXML
    private Label lblGrandTotalWords;
    @FXML
    private Label lblTaxAmountWords;

    @FXML
    private Label lblTermsFooter;

    private final service.InvoiceMasterService invoiceService = new service.InvoiceMasterService();
    private final service.GstPdfInvoiceService pdfService = new service.GstPdfInvoiceService();
    private final service.ClientService clientService = new service.ClientService();
    private final service.JobService jobService = new service.JobService();
    private final service.JobItemService jobItemService = new service.JobItemService();
    private final service.HsnSacService hsnSacService = new service.HsnSacService();
    private final service.SettingsService settingsService = new service.SettingsService();
    private final service.BankDetailsService bankService = new service.BankDetailsService();

    private final List<model.JobSummary> loadedJobSummaries = new ArrayList<>();
    private final Set<String> selectedJobUuids = new LinkedHashSet<>();
    private final ObservableList<ItemRow> itemRows = FXCollections.observableArrayList();
    private final ObservableList<HsnSummaryRow> hsnRows = FXCollections.observableArrayList();
    private final ObservableList<String> printingHsnOptions = FXCollections.observableArrayList();
    private final java.util.Map<String, model.HsnSacInfo> printingHsnInfoByCode = new java.util.HashMap<>();

    private static final DateTimeFormatter JOB_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final double DEFAULT_GST_RATE = 0.18;
    private static final String DEFAULT_COMPANY_STATE_CODE = "07"; // Delhi (07)

    private final StringProperty termsText = new SimpleStringProperty("");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize logic here
        setupInitialData();
        setupClientCombos();
        setupJobsBox();
        setupItemsTable();
        setupHsnSummaryTable();
        setupBreadcrumbs();
        setupTerms();
        setupBankDetailsCombo();
        loadPrintingHsnOptions();
        refreshInvoiceNoPreview();
    }

    private String fmtMoney(double value) {
        return String.format("₹ %,.2f", value);
    }

    private void loadPrintingHsnOptions() {
        printingHsnOptions.clear();
        printingHsnInfoByCode.clear();
        try {
            List<model.HsnSacInfo> list = hsnSacService.listAllActiveHsnSac();
            for (model.HsnSacInfo i : list) {
                if (i == null || i.getHsnSac() == null || i.getHsnSac().isBlank()) {
                    continue;
                }
                String code = i.getHsnSac().trim();
                printingHsnInfoByCode.put(code, i);
                if (!printingHsnOptions.contains(code)) {
                    printingHsnOptions.add(code);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!printingHsnOptions.contains("NA")) {
            printingHsnOptions.add("NA");
        }
    }

    private void setupBreadcrumbs() {
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null,
                () -> controller.MainController.getInstance().handleBack(null));
    }

    private void setupTerms() {
        String defaults = String.join("\n",
                "• GOODS ONCE SOLD WILL NOT BE TAKEN BACK.",
                "• INTEREST @24% P.A WILL BE CHARGED IF THE AMOUNT IS NOT PAID ON DEMAND.",
                "• ALL DISPUTES SUBJECT TO DELHI JURISDICTION.",
                "• ONLY AFTER PRINTING THE PLATES WILL NOT BE RETURNED.");
        if (termsText.get() == null || termsText.get().isBlank()) {
            termsText.set(defaults);
        }
        if (lblTermsFooter != null) {
            lblTermsFooter.textProperty().bind(termsText);
        }
    }

    private void setupBankDetailsCombo() {
        if (comboBankDetails == null) {
            return;
        }
        List<model.BankDetails> banks = bankService.listActive();
        if (banks == null || banks.isEmpty()) {
            banks = bankService.listAllIncludingInactive();
        }

        comboBankDetails.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(model.BankDetails item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getBankName());
                }
            }
        });

        comboBankDetails.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(model.BankDetails item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getBankName());
                }
            }
        });

        comboBankDetails.getItems().setAll(banks);

        comboBankDetails.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if (lblBankHolder != null) {
                    lblBankHolder.setText(newVal.getAccountHolderName() != null && !newVal.getAccountHolderName().isBlank()
                            ? newVal.getAccountHolderName().toUpperCase()
                            : "—");
                }
                if (lblBankAccountNo != null) {
                    lblBankAccountNo.setText(newVal.getAccountNo() != null && !newVal.getAccountNo().isBlank()
                            ? newVal.getAccountNo()
                            : "—");
                }
                if (lblBankBranchIfsc != null) {
                    lblBankBranchIfsc.setText(newVal.getBranchIfsc() != null && !newVal.getBranchIfsc().isBlank()
                            ? newVal.getBranchIfsc().toUpperCase()
                            : "—");
                }
            } else {
                if (lblBankHolder != null) lblBankHolder.setText("—");
                if (lblBankAccountNo != null) lblBankAccountNo.setText("—");
                if (lblBankBranchIfsc != null) lblBankBranchIfsc.setText("—");
            }
        });

        model.BankDetails defBank = null;
        for (model.BankDetails b : banks) {
            if (b.isDefault()) {
                defBank = b;
                break;
            }
        }
        if (defBank != null) {
            comboBankDetails.getSelectionModel().select(defBank);
        } else if (!banks.isEmpty()) {
            comboBankDetails.getSelectionModel().selectFirst();
        }
    }

    @SuppressWarnings("unchecked")
    private void setupItemsTable() {
        if (tableItems == null) {
            return;
        }

        tableItems.setItems(itemRows);
        tableItems.setEditable(true);

        List<TableColumn<ItemRow, ?>> cols = (List<TableColumn<ItemRow, ?>>) (List<?>) tableItems.getColumns();
        if (cols == null || cols.isEmpty()) {
            return;
        }

        // 0 #, 1 Description, 2 HSN/SAC, 3 Qty, 4 Unit, 5 Rate, 6 Taxable, 7 GST%, 8
        // CGST, 9 SGST, 10 IGST, 11 Total, 12 Action
        if (cols.size() >= 1)
            ((TableColumn<ItemRow, Number>) cols.get(0))
                    .setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getSlNo()));
        
        if (cols.size() >= 2) {
            TableColumn<ItemRow, String> col = (TableColumn<ItemRow, String>) cols.get(1);
            col.setCellValueFactory(c -> c.getValue().descriptionProperty());
            col.setCellFactory(tc -> {
                return new javafx.scene.control.TableCell<>() {
                    private javafx.scene.control.TextArea textArea;
                    private javafx.scene.text.Text textNode;

                    {
                        getStyleClass().add("gst-description-col");
                        getStyleClass().add("gst-editable-text-cell");
                    }

                    @Override
                    public void startEdit() {
                        if (!isEmpty()) {
                            super.startEdit();
                            if (textArea == null) {
                                textArea = new javafx.scene.control.TextArea(getItem());
                                textArea.setWrapText(true);
                                textArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                                    if (!isFocused) {
                                        commitEdit(textArea.getText());
                                    }
                                });
                            } else {
                                textArea.setText(getItem());
                            }
                            double cellHeight = getHeight();
                            double verticalPadding = getInsets().getTop() + getInsets().getBottom();
                            textArea.setPrefHeight(Math.max(60, cellHeight - verticalPadding));
                            setText(null);
                            setGraphic(textArea);
                            textArea.selectAll();
                        }
                    }

                    @Override
                    public void cancelEdit() {
                        super.cancelEdit();
                        setText(null);
                        setGraphic(textNode);
                    }

                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            if (textNode == null) {
                                textNode = new javafx.scene.text.Text(item);
                                textNode.wrappingWidthProperty().bind(getTableColumn().widthProperty().subtract(24));
                                textNode.setLineSpacing(3.0);
                                textNode.setStyle("-fx-fill: #3E312D; -fx-font-size: 11px;");
                            } else {
                                textNode.setText(item);
                            }

                            if (isEditing()) {
                                if (textArea != null) {
                                    textArea.setText(item);
                                }
                                setText(null);
                                setGraphic(textArea);
                            } else {
                                setText(null);
                                setGraphic(textNode);
                            }
                        }
                    }
                };
            });
            col.setOnEditCommit(ev -> {
                ItemRow row = ev.getRowValue();
                if (row != null) {
                    row.setDescription(ev.getNewValue());
                }
            });
            col.setEditable(true);
        }
        
        if (cols.size() >= 3) {
            TableColumn<ItemRow, String> hsnCol = (TableColumn<ItemRow, String>) cols.get(2);
            hsnCol.setCellValueFactory(c -> c.getValue().hsnSacProperty());
            hsnCol.setCellFactory(tc -> {
                ComboBoxTableCell<ItemRow, String> cell = new ComboBoxTableCell<>(printingHsnOptions) {
                    {
                        setOnMouseClicked(e -> {
                            if (!isEmpty()) {
                                getTableView().edit(getIndex(), getTableColumn());
                            }
                        });
                    }

                    @Override
                    public void startEdit() {
                        super.startEdit();
                        if (isEditing() && getGraphic() instanceof ComboBox<?> comboBox) {
                            comboBox.show();
                        }
                    }

                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setGraphic(null);
                        } else if (!isEditing()) {
                            HBox hbox = new HBox(5);
                            hbox.setAlignment(Pos.CENTER);
                            Label label = new Label(item);
                            label.setStyle("-fx-text-fill: #3E312D; -fx-font-size: 12px; -fx-font-weight: 600;");
                            javafx.scene.layout.Region arrow = new javafx.scene.layout.Region();
                            arrow.setStyle("-fx-background-color: #8E7A6B; -fx-shape: 'M 0 0 L 4 4 L 8 0 Z'; -fx-min-width: 8; -fx-min-height: 5; -fx-max-width: 8; -fx-max-height: 5;");
                            hbox.getChildren().addAll(label, arrow);
                            setGraphic(hbox);
                            setText(null);
                        }
                    }
                };
                cell.getStyleClass().add("gst-editable-combo-cell");
                return cell;
            });
            hsnCol.setEditable(true);
            hsnCol.setOnEditCommit(ev -> {
                ItemRow row = ev.getRowValue();
                if (row == null) return;
                String code = ev.getNewValue() != null ? ev.getNewValue().trim() : "—";
                row.setHsnSac(code);
                model.HsnSacInfo info = printingHsnInfoByCode.get(code);
                double gstRate = info != null && info.getGstRate() > 0 ? info.getGstRate() : DEFAULT_GST_RATE;
                row.recalcTaxes(gstRate, isIntraStateSupply());
                refreshHsnSummaryFromItemRows();
                refreshInvoiceSummary();
            });
        }
        
        if (cols.size() >= 4) {
            TableColumn<ItemRow, String> col = (TableColumn<ItemRow, String>) cols.get(3);
            col.setCellValueFactory(c -> c.getValue().qtyProperty());
            col.setCellFactory(tc -> {
                return new TableCell<ItemRow, String>() {
                    private TextField textField;
                    {
                        getStyleClass().add("gst-editable-text-cell");
                    }
                    @Override
                    public void startEdit() {
                        if (!isEmpty()) {
                            ItemRow row = getTableView().getItems().get(getIndex());
                            if (row != null && row.isCharge()) {
                                return;
                            }
                            super.startEdit();
                            createTextField();
                            setText(null);
                            setGraphic(textField);
                            textField.selectAll();
                            textField.requestFocus();
                        }
                    }
                    @Override
                    public void cancelEdit() {
                        super.cancelEdit();
                        setText(getItem());
                        setGraphic(null);
                    }
                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            if (isEditing()) {
                                if (textField != null) {
                                    textField.setText(item);
                                }
                                setText(null);
                                setGraphic(textField);
                            } else {
                                setText(item);
                                setGraphic(null);
                            }
                        }
                    }
                    private void createTextField() {
                        textField = new TextField(getItem() != null ? getItem() : "");
                        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                            if (!isFocused) {
                                commitEdit(textField.getText());
                            }
                        });
                        textField.setOnAction(e -> commitEdit(textField.getText()));
                    }
                };
            });
            col.setEditable(true);
            col.setOnEditCommit(ev -> {
                ItemRow row = ev.getRowValue();
                if (row != null) {
                    row.setQty(ev.getNewValue());
                    refreshHsnSummaryFromItemRows();
                    refreshInvoiceSummary();
                }
            });
        }
        
        if (cols.size() >= 5) {
            TableColumn<ItemRow, String> col = (TableColumn<ItemRow, String>) cols.get(4);
            col.setCellValueFactory(c -> c.getValue().unitProperty());
            col.setCellFactory(tc -> {
                ComboBoxTableCell<ItemRow, String> cell = new ComboBoxTableCell<>(FXCollections.observableArrayList("PCS", "NOS", "PKTS", "BOX", "KGS", "NA")) {
                    {
                        setOnMouseClicked(e -> {
                            if (!isEmpty()) {
                                ItemRow row = getTableView().getItems().get(getIndex());
                                if (row != null && row.isCharge()) {
                                    return;
                                }
                                getTableView().edit(getIndex(), getTableColumn());
                            }
                        });
                    }

                    @Override
                    public void startEdit() {
                        ItemRow row = getTableView().getItems().get(getIndex());
                        if (row != null && row.isCharge()) {
                            return;
                        }
                        super.startEdit();
                        if (isEditing() && getGraphic() instanceof ComboBox<?> comboBox) {
                            comboBox.show();
                        }
                    }

                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setGraphic(null);
                        } else if (!isEditing()) {
                            HBox hbox = new HBox(5);
                            hbox.setAlignment(Pos.CENTER);
                            Label label = new Label(item);
                            label.setStyle("-fx-text-fill: #3E312D; -fx-font-size: 12px; -fx-font-weight: 600;");
                            javafx.scene.layout.Region arrow = new javafx.scene.layout.Region();
                            arrow.setStyle("-fx-background-color: #8E7A6B; -fx-shape: 'M 0 0 L 4 4 L 8 0 Z'; -fx-min-width: 8; -fx-min-height: 5; -fx-max-width: 8; -fx-max-height: 5;");
                            hbox.getChildren().addAll(label, arrow);
                            setGraphic(hbox);
                            setText(null);
                        }
                    }
                };
                cell.getStyleClass().add("gst-editable-combo-cell");
                return cell;
            });
            col.setEditable(true);
            col.setOnEditCommit(ev -> {
                ItemRow row = ev.getRowValue();
                if (row != null) row.setUnit(ev.getNewValue());
            });
        }
        
        if (cols.size() >= 6) {
            TableColumn<ItemRow, String> col = (TableColumn<ItemRow, String>) cols.get(5);
            col.setCellValueFactory(c -> c.getValue().rateProperty());
            col.setCellFactory(tc -> {
                return new TableCell<ItemRow, String>() {
                    private TextField textField;
                    {
                        getStyleClass().add("gst-editable-text-cell");
                    }
                    @Override
                    public void startEdit() {
                        if (!isEmpty()) {
                            ItemRow row = getTableView().getItems().get(getIndex());
                            if (row != null && row.isCharge()) {
                                return;
                            }
                            super.startEdit();
                            createTextField();
                            setText(null);
                            setGraphic(textField);
                            textField.selectAll();
                            textField.requestFocus();
                        }
                    }
                    @Override
                    public void cancelEdit() {
                        super.cancelEdit();
                        setText(getItem());
                        setGraphic(null);
                    }
                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            if (isEditing()) {
                                if (textField != null) {
                                    textField.setText(item);
                                }
                                setText(null);
                                setGraphic(textField);
                            } else {
                                setText(item);
                                setGraphic(null);
                            }
                        }
                    }
                    private void createTextField() {
                        textField = new TextField(getItem() != null ? getItem() : "");
                        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                            if (!isFocused) {
                                commitEdit(textField.getText());
                            }
                        });
                        textField.setOnAction(e -> commitEdit(textField.getText()));
                    }
                };
            });
            col.setEditable(true);
            col.setOnEditCommit(ev -> {
                ItemRow row = ev.getRowValue();
                if (row != null) {
                    row.setRate(ev.getNewValue());
                    refreshHsnSummaryFromItemRows();
                    refreshInvoiceSummary();
                }
            });
        }

        if (cols.size() >= 7)
            ((TableColumn<ItemRow, String>) cols.get(6)).setCellValueFactory(c -> c.getValue().taxableProperty());
        if (cols.size() >= 8)
            ((TableColumn<ItemRow, String>) cols.get(7)).setCellValueFactory(c -> c.getValue().gstPercentProperty());
        if (cols.size() >= 9)
            ((TableColumn<ItemRow, String>) cols.get(8)).setCellValueFactory(c -> c.getValue().cgstProperty());
        if (cols.size() >= 10)
            ((TableColumn<ItemRow, String>) cols.get(9)).setCellValueFactory(c -> c.getValue().sgstProperty());
        if (cols.size() >= 11)
            ((TableColumn<ItemRow, String>) cols.get(10)).setCellValueFactory(c -> c.getValue().igstProperty());
        if (cols.size() >= 12) {
            TableColumn<ItemRow, String> totalCol = (TableColumn<ItemRow, String>) cols.get(11);
            totalCol.setCellValueFactory(c -> c.getValue().totalProperty());
            totalCol.setCellFactory(tc -> {
                return new TableCell<ItemRow, String>() {
                    private TextField textField;
                    {
                        getStyleClass().add("gst-editable-text-cell");
                    }
                    @Override
                    public void startEdit() {
                        if (!isEmpty()) {
                            ItemRow row = getTableView().getItems().get(getIndex());
                            if (row == null || !row.isCustom()) {
                                return;
                            }
                            super.startEdit();
                            createTextField();
                            setText(null);
                            setGraphic(textField);
                            textField.selectAll();
                            textField.requestFocus();
                        }
                    }
                    @Override
                    public void cancelEdit() {
                        super.cancelEdit();
                        setText(getItem());
                        setGraphic(null);
                    }
                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            if (isEditing()) {
                                if (textField != null) {
                                    textField.setText(item);
                                }
                                setText(null);
                                setGraphic(textField);
                            } else {
                                setText(item);
                                setGraphic(null);
                            }
                        }
                    }
                    private void createTextField() {
                        textField = new TextField(getItem() != null ? getItem() : "");
                        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                            if (!isFocused) {
                                commitEdit(textField.getText());
                            }
                        });
                        textField.setOnAction(e -> commitEdit(textField.getText()));
                    }
                };
            });
            totalCol.setEditable(true);
            totalCol.setOnEditCommit(ev -> {
                ItemRow row = ev.getRowValue();
                if (row != null) {
                    row.setTotal(ev.getNewValue());
                    refreshHsnSummaryFromItemRows();
                    refreshInvoiceSummary();
                }
            });
        }

        tableItems.setRowFactory(tv -> {
            TableRow<ItemRow> row = new TableRow<>();
            // Use addEventFilter so the double-click fires BEFORE cell-level handlers
            // Consume on MOUSE_PRESSED to prevent the cell from entering edit mode
            row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    ItemRow rowData = row.getItem();
                    if (rowData != null && !rowData.isCustom() && rowData.getJobUuid() != null) {
                        event.consume(); // prevent double-click from also triggering cell editing
                        showJobItemsPopup(rowData.getJobUuid(), rowData.getDescription());
                    }
                }
            });
            // Also consume MOUSE_CLICKED for safety to prevent propagation
            row.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    ItemRow rowData = row.getItem();
                    if (rowData != null && !rowData.isCustom() && rowData.getJobUuid() != null) {
                        event.consume();
                    }
                }
            });
            return row;
        });
    }

    private void setupInitialData() {
        if (txtInvoiceNo != null) {
            txtInvoiceNo.setEditable(false);
        }
        if (dpInvoiceDate != null) {
            dpInvoiceDate.setValue(java.time.LocalDate.now());
            dpInvoiceDate.valueProperty().addListener((obs, oldV, newV) -> {
                refreshInvoiceNoPreview();
                recalcDueDate();
            });
        }

        if (comboPaymentTerms != null) {
            // If nothing is selected, default to prompt (often "30 Days")
            comboPaymentTerms.valueProperty().addListener((obs, oldV, newV) -> recalcDueDate());
        }

        if (dpDueDate != null) {
            dpDueDate.setEditable(false);
        }

        if (comboPlaceOfSupply != null) {
            comboPlaceOfSupply.valueProperty().addListener((obs, oldV, newV) -> {
                boolean intraState = isIntraStateSupply();
                for (ItemRow r : itemRows) {
                    r.recalcTaxes(r.gstRateRaw.get(), intraState);
                }
                refreshHsnSummaryFromItemRows();
                refreshInvoiceSummary();
            });
        }

        recalcDueDate();
    }

    private void recalcDueDate() {
        if (dpDueDate == null) {
            return;
        }
        java.time.LocalDate inv = dpInvoiceDate != null ? dpInvoiceDate.getValue() : null;
        if (inv == null) {
            inv = java.time.LocalDate.now();
        }

        String term = comboPaymentTerms != null ? comboPaymentTerms.getValue() : null;
        if (term == null || term.isBlank()) {
            term = comboPaymentTerms != null ? comboPaymentTerms.getPromptText() : null;
        }

        int days = parsePaymentTermDays(term);
        if (days <= 0) {
            // fallback: keep due date = invoice date
            dpDueDate.setValue(inv);
            return;
        }
        dpDueDate.setValue(inv.plusDays(days));
    }

    private static int parsePaymentTermDays(String term) {
        if (term == null)
            return 0;
        String t = term.trim().toLowerCase();
        // Accept: "30", "30 days", "Net 30", "NET30"
        String digits = t.replaceAll("[^0-9]", "");
        if (digits.isBlank())
            return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void refreshInvoiceNoPreview() {
        if (txtInvoiceNo == null) {
            return;
        }
        javafx.application.Platform.runLater(() -> txtInvoiceNo.setText("New GST"));
    }

    private void setupClientCombos() {
        // ── Company (From) — single company from settings ─────────────────────
        if (comboCompanyFrom != null) {
            String companyName = utils.CompanyProfile.getName();
            comboCompanyFrom.getItems().setAll(companyName);
            comboCompanyFrom.getSelectionModel().selectFirst();
        }
        populateCompanyDetails();

        // ── Shared client list (only showing clients with completed, uninvoiced jobs) ───────────────────────
        List<model.Client> clients = new java.util.ArrayList<>();
        List<model.Client> allClients = clientService.getAllClients();
        if (allClients != null && jobService != null) {
            for (model.Client c : allClients) {
                if (c != null && c.getClientUuid() != null) {
                    try {
                        List<model.Job> completedJobs = jobService.getCompletedJobsByClient(c.getClientUuid());
                        if (completedJobs != null && !completedJobs.isEmpty()) {
                            clients.add(c);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // ── Bill To (Buyer) ────────────────────────────────────────────────────
        if (comboBillTo != null) {
            comboBillTo.getItems().setAll(clients);
            comboBillTo.setCellFactory(cb -> new ListCell<>() {
                @Override
                protected void updateItem(model.Client item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : clientDisplayName(item));
                }
            });
            comboBillTo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(model.Client item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "Select buyer" : clientDisplayName(item));
                }
            });
            comboBillTo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) {
                    loadJobsForClient(newV.getClientUuid());
                    populateBillToDetails(newV);
                    // Auto-mirror to Ship To if nothing explicitly chosen there yet
                    if (comboShipTo != null && comboShipTo.getValue() == null) {
                        comboShipTo.setValue(newV);
                    }
                } else {
                    clearJobsBox();
                    clearClientDetails(lblBillToAddress, lblBillToGstin, lblBillToState);
                }
            });
        }

        // ── Ship To (Consignee) ─────────────────────────────────────────────────
        if (comboShipTo != null) {
            comboShipTo.getItems().setAll(clients);
            comboShipTo.setCellFactory(cb -> new ListCell<>() {
                @Override
                protected void updateItem(model.Client item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : clientDisplayName(item));
                }
            });
            comboShipTo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(model.Client item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "Select consignee" : clientDisplayName(item));
                }
            });
            comboShipTo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
                if (newV != null) {
                    populateShipToDetails(newV);
                } else {
                    clearClientDetails(lblShipToAddress, lblShipToGstin, lblShipToState);
                }
            });
        }
    }

    /** Fills the Company (From) address block from CompanyProfile settings. */
    private void populateCompanyDetails() {
        String gstin = utils.CompanyProfile.getGst();
        String addr = utils.CompanyProfile.getAddress();
        setLbl(lblCompanyAddress, addr);
        setLbl(lblCompanyGstin, gstin);
        setLbl(lblCompanyState, stateNameFromGstinOrAddress(gstin, addr));
        setLbl(lblCompanyEmail, utils.CompanyProfile.getEmail());
    }

    /**
     * Fills Bill To address block and auto-sets Place of Supply from client GSTIN.
     */
    private void populateBillToDetails(model.Client c) {
        if (c == null) {
            clearClientDetails(lblBillToAddress, lblBillToGstin, lblBillToState);
            return;
        }
        String gstin = c.getGst();
        String addr = c.getBillingAddress();
        setLbl(lblBillToAddress, addr);
        setLbl(lblBillToGstin, gstin);
        String stateName = stateNameFromGstinOrAddress(gstin, addr);
        setLbl(lblBillToState, stateName);
        // Auto-set Place of Supply combo to match client state
        if (comboPlaceOfSupply != null && stateName != null && !stateName.isBlank()) {
            // Find the state code in parenthesis e.g. "(07)"
            int idx = stateName.indexOf('(');
            if (idx != -1 && idx + 3 <= stateName.length()) {
                String code = stateName.substring(idx + 1, idx + 3);
                comboPlaceOfSupply.getItems().stream()
                        .filter(item -> item.contains("(" + code + ")"))
                        .findFirst()
                        .ifPresent(comboPlaceOfSupply::setValue);
            }
        }
    }

    /**
     * Fills Ship To address block (prefers shipping address, falls back to
     * billing).
     */
    private void populateShipToDetails(model.Client c) {
        if (c == null) {
            clearClientDetails(lblShipToAddress, lblShipToGstin, lblShipToState);
            return;
        }
        String addr = c.getShippingAddress();
        if (addr == null || addr.isBlank())
            addr = c.getBillingAddress();
        setLbl(lblShipToAddress, addr);
        setLbl(lblShipToGstin, c.getGst());
        setLbl(lblShipToState, stateNameFromGstinOrAddress(c.getGst(), addr));
    }

    private static void clearClientDetails(Label address, Label gstin, Label state) {
        setLbl(address, "");
        setLbl(gstin, "");
        setLbl(state, "");
    }

    private static void setLbl(Label lbl, String text) {
        if (lbl != null)
            lbl.setText(text != null ? text : "");
    }

    private static String clientDisplayName(model.Client c) {
        if (c == null)
            return "";
        String n = c.getBusinessName();
        return (n != null && !n.isBlank()) ? n : c.getClientName();
    }

    private static final String[] ALL_STATES = {
            "Jammu & Kashmir (01)", "Himachal Pradesh (02)", "Punjab (03)", "Chandigarh (04)",
            "Uttarakhand (05)", "Haryana (06)", "Delhi (07)", "Rajasthan (08)", "Uttar Pradesh (09)",
            "Bihar (10)", "Sikkim (11)", "Arunachal Pradesh (12)", "Nagaland (13)", "Manipur (14)",
            "Mizoram (15)", "Tripura (16)", "Meghalaya (17)", "Assam (18)", "West Bengal (19)",
            "Jharkhand (20)", "Odisha (21)", "Chhattisgarh (22)", "Madhya Pradesh (23)", "Gujarat (24)",
            "Daman & Diu (25)", "Dadra & Nagar Haveli (26)", "Maharashtra (27)", "Karnataka (29)",
            "Goa (30)", "Lakshadweep (31)", "Kerala (32)", "Tamil Nadu (33)", "Puducherry (34)",
            "Andaman & Nicobar Islands (35)", "Telangana (36)", "Andhra Pradesh (New) (37)", "Ladakh (38)"
    };

    /**
     * Maps the first 2 digits of a GSTIN to an Indian state name string.
     * Falls back to text parsing from address if GSTIN is empty.
     */
    private static String stateNameFromGstinOrAddress(String gstin, String address) {
        if (gstin != null && gstin.trim().length() >= 2) {
            String code = gstin.trim().substring(0, 2);
            for (String s : ALL_STATES) {
                if (s.contains("(" + code + ")"))
                    return s;
            }
        }

        // Fallback: search address for known state names
        if (address != null && !address.isBlank()) {
            String upper = address.toUpperCase();
            // Fast check for common abbreviations
            if (upper.contains(" U.P") || upper.contains(" UP ") || upper.endsWith(" UP"))
                return "Uttar Pradesh (09)";
            if (upper.contains(" M.P") || upper.contains(" MP ") || upper.endsWith(" MP"))
                return "Madhya Pradesh (23)";

            for (String s : ALL_STATES) {
                String name = s.substring(0, s.indexOf('(')).trim().toUpperCase();
                if (upper.contains(name)) {
                    return s;
                }
            }
        }
        return "";
    }

    private void setupJobsBox() {
        if (flowJobsContainer == null) {
            return;
        }
        flowJobsContainer.setHgap(14);
        flowJobsContainer.setVgap(10);
        flowJobsContainer.setPrefWrapLength(900);
    }

    private void clearJobsBox() {
        loadedJobSummaries.clear();
        selectedJobUuids.clear();
        itemRows.clear();
        hsnRows.clear();
        if (flowJobsContainer != null) {
            flowJobsContainer.getChildren().clear();
        }
    }

    private void loadJobsForClient(String clientUuid) {
        clearJobsBox();
        if (flowJobsContainer == null) {
            return;
        }
        List<model.JobSummary> jobs = jobService.getJobsByClientId(clientUuid); // completed + uninvoiced
        loadedJobSummaries.addAll(jobs);

        if (jobs.isEmpty()) {
            Label empty = new Label("No completed jobs available for invoicing.");
            empty.getStyleClass().add("gst-muted");
            flowJobsContainer.getChildren().add(empty);
            return;
        }

        for (model.JobSummary js : jobs) {
            HBox card = new HBox(10);
            card.getStyleClass().add("gst-job-chip");
            card.setAlignment(Pos.CENTER_LEFT);

            CheckBox cb = new CheckBox();
            cb.getStyleClass().add("gst-job-check"); // FXML had radio but user asked for checkbox
            cb.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (isSelected) {
                    selectedJobUuids.add(js.getUuid());
                    card.getStyleClass().add("gst-job-chip-selected");
                } else {
                    selectedJobUuids.remove(js.getUuid());
                    card.getStyleClass().remove("gst-job-chip-selected");
                }
                refreshItemsTableFromSelectedJobs();
            });

            // Clicking anywhere on the card toggles the checkbox
            card.setOnMouseClicked(e -> {
                javafx.scene.Node target = (javafx.scene.Node) e.getTarget();
                while (target != null) {
                    if (target == cb)
                        return; // The CheckBox already handled its own click
                    target = target.getParent();
                }
                cb.setSelected(!cb.isSelected());
            });

            VBox infoBox = new VBox(4);
            Label no = new Label(js.getJobNo());
            no.getStyleClass().add("gst-chip-title");
            Label title = new Label(js.getJobTitle());
            title.getStyleClass().add("gst-chip-sub");
            Label date = new Label(
                    "Completed: " + (js.getJobDate() != null ? js.getJobDate().format(JOB_DATE_FMT) : "—"));
            date.getStyleClass().add("gst-chip-meta");
            Label status = new Label("Completed");
            status.getStyleClass().add("gst-chip-status");

            infoBox.getChildren().addAll(no, title, date, status);
            card.getChildren().addAll(cb, infoBox);
            flowJobsContainer.getChildren().add(card);
        }
    }

    private void refreshItemsTableFromSelectedJobs() {
        // Keep custom items (those not in loadedJobSummaries)
        List<ItemRow> customRows = new ArrayList<>();
        java.util.Set<String> loadedUuids = new java.util.HashSet<>();
        for (model.JobSummary js : loadedJobSummaries) {
            loadedUuids.add(js.getUuid());
        }
        for (ItemRow r : itemRows) {
            if (!loadedUuids.contains(r.getJobUuid())) {
                customRows.add(r);
            }
        }

        itemRows.clear();
        if (selectedJobUuids.isEmpty() && customRows.isEmpty()) {
            hsnRows.clear();
            refreshInvoiceSummary();
            return;
        }

        boolean intraState = isIntraStateSupply();
        int sl = 1;
        for (model.JobSummary js : loadedJobSummaries) {
            if (!selectedJobUuids.contains(js.getUuid())) {
                continue;
            }

            List<model.JobItem> items = jobItemService.getJobItems(js.getUuid());
            long qty = jobService.getTotalPrintingQtyForJobUuids(List.of(js.getUuid()));
            double jobTaxable = jobService.getSumJobItemsAmountForJobUuids(List.of(js.getUuid()));
            double rate = qty > 0 ? (jobTaxable / qty) : jobTaxable;
            String hsn = "—";
            double gstRate = DEFAULT_GST_RATE;
            String combinedDesc = "";
            model.Job fullJob = jobService.getJobByUuid(js.getUuid());
            if (fullJob != null && fullJob.getDescription() != null && !fullJob.getDescription().isBlank()) {
                combinedDesc = fullJob.getDescription();
            } else {
                combinedDesc = "PRINTING CHARGES TOWARDS\n    " + js.getJobTitle().toUpperCase();
                if (items != null && !items.isEmpty()) {
                    String itemsText = items.stream()
                            .map(model.JobItem::getDescription)
                            .filter(d -> d != null && !d.isBlank())
                            .map(d -> "    " + d.toUpperCase())
                            .collect(java.util.stream.Collectors.joining("\n"));
                    if (!itemsText.isBlank()) {
                        combinedDesc += "\n" + itemsText;
                    }
                }
                combinedDesc += "\n    COMPLETE-" + qty + " PCS";
            }

            // Try to find the first valid HSN info to use as default
            if (items != null) {
                for (model.JobItem ji : items) {
                    model.HsnSacInfo info = hsnSacService.lookup(ji);
                    if (info != null && info.getHsnSac() != null && !info.getHsnSac().isBlank()) {
                        hsn = info.getHsnSac();
                        if (info.getGstRate() > 0) {
                            gstRate = info.getGstRate();
                        }
                        break;
                    }
                }
            }

            itemRows.add(ItemRow.ofJob(sl++, js.getUuid(), combinedDesc, hsn, qty, "PCS", rate, gstRate, intraState));
        }

        for (ItemRow cr : customRows) {
            itemRows.add(new ItemRow(
                cr.getJobUuid(),
                sl++,
                cr.getDescription(),
                cr.getHsnSac(),
                cr.qtyRaw.get(),
                cr.getUnit(),
                cr.rateRaw.get(),
                cr.gstRateRaw.get(),
                intraState,
                true, // isCustom
                cr.isCharge()
            ));
        }

        refreshHsnSummaryFromItemRows();
        refreshInvoiceSummary();
    }

    @SuppressWarnings("unchecked")
    private void setupHsnSummaryTable() {
        if (tableHsnSummary == null) {
            return;
        }

        tableHsnSummary.setItems(hsnRows);
        List<TableColumn<HsnSummaryRow, ?>> cols = (List<TableColumn<HsnSummaryRow, ?>>) (List<?>) tableHsnSummary
                .getColumns();
        if (cols == null || cols.isEmpty()) {
            return;
        }

        // FXML columns: 0 HSN/SAC, 1 Taxable Value, 2 GST%, 3 CGST, 4 SGST, 5 IGST, 6
        // Total Tax
        if (cols.size() >= 1)
            ((TableColumn<HsnSummaryRow, String>) cols.get(0))
                    .setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getHsnSac()));
        if (cols.size() >= 2)
            ((TableColumn<HsnSummaryRow, String>) cols.get(1))
                    .setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getTaxable()));
        if (cols.size() >= 3)
            ((TableColumn<HsnSummaryRow, String>) cols.get(2))
                    .setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getGstPercent()));
        if (cols.size() >= 4)
            ((TableColumn<HsnSummaryRow, String>) cols.get(3))
                    .setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getCgst()));
        if (cols.size() >= 5)
            ((TableColumn<HsnSummaryRow, String>) cols.get(4))
                    .setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getSgst()));
        if (cols.size() >= 6)
            ((TableColumn<HsnSummaryRow, String>) cols.get(5))
                    .setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getIgst()));
        if (cols.size() >= 7)
            ((TableColumn<HsnSummaryRow, String>) cols.get(6))
                    .setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getTotalTax()));
    }

    private void refreshHsnSummaryFromItemRows() {
        hsnRows.clear();
        if (itemRows.isEmpty()) {
            return;
        }

        class Agg {
            double taxable = 0;
            double cgst = 0;
            double sgst = 0;
            double igst = 0;
            double gstRate = DEFAULT_GST_RATE;
        }

        java.util.Map<String, Agg> byHsn = new java.util.LinkedHashMap<>();
        for (ItemRow r : itemRows) {
            String hsn = r.getHsnSac() != null && !r.getHsnSac().isBlank() ? r.getHsnSac().trim() : "—";
            if ("NA".equalsIgnoreCase(hsn) || "N/A".equalsIgnoreCase(hsn)) {
                continue;
            }
            Agg a = byHsn.computeIfAbsent(hsn, k -> new Agg());
            a.taxable += r.taxableRaw.get();
            a.cgst += r.cgstRaw.get();
            a.sgst += r.sgstRaw.get();
            a.igst += r.igstRaw.get();
            a.gstRate = r.gstRateRaw.get() > 0 ? r.gstRateRaw.get() : a.gstRate;
        }

        for (var e : byHsn.entrySet()) {
            Agg a = e.getValue();
            hsnRows.add(HsnSummaryRow.of(
                    e.getKey(),
                    round2(a.taxable),
                    a.gstRate,
                    round2(a.cgst),
                    round2(a.sgst),
                    round2(a.igst)));
        }
    }

    private void refreshInvoiceSummary() {
        if (lblTaxable == null)
            return; // FXML not bound

        int itemCount = itemRows.size();
        long totalQty = 0;
        double taxable = 0;
        double cgst = 0;
        double sgst = 0;
        double igst = 0;
        String commonUnit = null;

        for (ItemRow r : itemRows) {
            taxable += r.taxableRaw.get();
            cgst += r.cgstRaw.get();
            sgst += r.sgstRaw.get();
            igst += r.igstRaw.get();

            try {
                // Remove commas and parse
                long qty = Long.parseLong(r.getQty().replace(",", "").trim());
                totalQty += qty;
            } catch (Exception e) {
                // ignore
            }
            if (commonUnit == null) {
                commonUnit = r.getUnit();
            } else if (!commonUnit.equals(r.getUnit())) {
                commonUnit = "MIXED";
            }
        }

        double totalTax = cgst + sgst + igst;
        double unroundedTotal = taxable + totalTax;
        double grandTotal = Math.round(unroundedTotal);
        double roundOff = grandTotal - unroundedTotal;

        setLbl(lblTotalItems, String.valueOf(itemCount));

        String unitStr = (commonUnit == null || "MIXED".equals(commonUnit)) ? "UNITS" : commonUnit;
        setLbl(lblTotalQty, String.format("%,d %s", totalQty, unitStr));

        setLbl(lblTaxable, fmtMoney(taxable));
        setLbl(lblCgst, cgst > 0 ? fmtMoney(cgst) : "—");
        setLbl(lblSgst, sgst > 0 ? fmtMoney(sgst) : "—");
        setLbl(lblIgst, igst > 0 ? fmtMoney(igst) : "—");
        setLbl(lblRoundOff, fmtMoney(roundOff));
        setLbl(lblGrandTotal, fmtMoney(grandTotal));

        String words = utils.NumberToWords.convert((long) grandTotal);
        setLbl(lblGrandTotalWords, "(INR " + words + " Only)");
        setLbl(lblTaxAmountWords, utils.NumberToWords.convertToIndianCurrency(totalTax));
    }

    private boolean isIntraStateSupply() {
        String companyGst = utils.CompanyProfile.getGst();
        String companyCode = extractStateCode(companyGst);
        if (companyCode == null || companyCode.isBlank()) {
            companyCode = DEFAULT_COMPANY_STATE_CODE;
        }

        String pos = comboPlaceOfSupply != null ? comboPlaceOfSupply.getValue() : null;
        String posCode = extractStateCode(pos);
        if (posCode == null || posCode.isBlank()) {
            // if unknown, treat as inter-state to avoid splitting
            return false;
        }
        return companyCode.equals(posCode);
    }

    private static String extractStateCode(String s) {
        if (s == null)
            return null;
        // GSTIN starts with 2 digits; combo values contain "(07)" etc.
        String trimmed = s.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((\\d{2})\\)").matcher(trimmed);
        if (m.find())
            return m.group(1);
        m = java.util.regex.Pattern.compile("^(\\d{2})").matcher(trimmed);
        if (m.find())
            return m.group(1);
        return null;
    }

    private record QtyUnit(long qty, String unit) {
        static QtyUnit resolveForJobItem(model.JobItem ji) {
            if (ji == null)
                return new QtyUnit(0, "PCS");

            try {
                switch (ji.getType() != null ? ji.getType().trim().toUpperCase() : "") {
                    case "PRINTING" -> {
                        model.Printing p = new repository.PrintingItemRepository().findByJobItemUuid(ji.getUuid());
                        if (p != null)
                            return new QtyUnit(p.getQty(), p.getUnits());
                    }
                    case "PAPER" -> {
                        model.Paper p = new repository.PaperItemRepository().findByJobItemUuid(ji.getUuid());
                        if (p != null)
                            return new QtyUnit(p.getQty(), p.getUnits());
                    }
                    case "BINDING" -> {
                        model.Binding b = new repository.BindingItemRepository().findByJobItemUuid(ji.getUuid());
                        if (b != null)
                            return new QtyUnit(b.getQty(), "PCS");
                    }
                    case "LAMINATION" -> {
                        model.Lamination l = new repository.LaminationItemRepository().findByJobItemUuid(ji.getUuid());
                        if (l != null)
                            return new QtyUnit(l.getQty(), l.getUnit());
                    }
                    case "CTP" -> {
                        model.CtpPlate c = new repository.CtpItemRepository().findByJobItemUuid(ji.getUuid());
                        if (c != null)
                            return new QtyUnit(c.getQty(), "PCS");
                    }
                    default -> {
                    }
                }
            } catch (Exception e) {
                // ignore and fallback below
            }
            return new QtyUnit(0, "PCS");
        }
    }

    private record Taxes(double cgst, double sgst, double igst, double totalTax, double total) {
        static Taxes compute(double taxable, double gstRate, boolean intraState) {
            if (gstRate < 0)
                gstRate = 0;
            double cgst = 0;
            double sgst = 0;
            double igst = 0;
            if (intraState) {
                cgst = round2(taxable * (gstRate / 2.0));
                sgst = round2(taxable * (gstRate / 2.0));
            } else {
                igst = round2(taxable * gstRate);
            }
            double totalTax = round2(cgst + sgst + igst);
            double total = round2(taxable + totalTax);
            return new Taxes(cgst, sgst, igst, totalTax, total);
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @FXML
    private void handlePreviewInvoice() {
        processInvoiceGeneration("DRAFT", true);
    }

    @FXML
    private void handleGenerateInvoice() {
        processInvoiceGeneration("DRAFT", false);
    }

    private model.Invoice buildInvoiceModel() {
        model.Invoice invoice = new model.Invoice();
        invoice.setInvoiceNo(txtInvoiceNo.getText());
        invoice.setInvoiceDate(dpInvoiceDate.getValue());
        model.Client buyer = comboBillTo != null ? comboBillTo.getValue() : null;
        if (buyer != null) {
            String name = buyer.getBusinessName() != null && !buyer.getBusinessName().isBlank()
                    ? buyer.getBusinessName()
                    : buyer.getClientName();
            invoice.setClientName(name);
            invoice.setClientId(buyer.getClientUuid());
            invoice.setBuyerAddress(buyer.getBillingAddress());
            invoice.setBuyerGstin(buyer.getGst());
            invoice.setBuyerStateName(comboPlaceOfSupply != null ? comboPlaceOfSupply.getValue() : null);
        }
        invoice.setInvoiceType("GST_INVOICE");
        invoice.setMasterDocumentSeries(model.MasterDocumentSeries.GST_INVOICE);

        // Capture metadata fields
        invoice.setPlaceOfSupply(comboPlaceOfSupply != null ? comboPlaceOfSupply.getValue() : null);
        String pTerm = comboPaymentTerms != null ? comboPaymentTerms.getValue() : null;
        if (pTerm == null || pTerm.isBlank()) {
            pTerm = comboPaymentTerms != null ? comboPaymentTerms.getPromptText() : null;
        }
        invoice.setPaymentTerms(pTerm);
        invoice.setDueDate(dpDueDate != null ? dpDueDate.getValue() : null);
        invoice.setVehicleDispatch(txtVehicleDispatch != null ? txtVehicleDispatch.getText() : null);
        invoice.setPoNo(txtPoNo != null ? txtPoNo.getText() : null);
        invoice.setPoDate(dpPoDate != null ? dpPoDate.getValue() : null);
        invoice.setDispatchThrough(txtDispatchThrough != null ? txtDispatchThrough.getText() : null);
        invoice.setLrTrackingNo(txtLrTrackingNo != null ? txtLrTrackingNo.getText() : null);
        invoice.setRemarks(txtRemarks != null ? txtRemarks.getText() : null);
        invoice.setEwayBillNo(txtEwayBillNo != null ? txtEwayBillNo.getText() : null);
        invoice.setBankDetails(comboBankDetails != null ? comboBankDetails.getValue() : null);

        if (selectedJobUuids.isEmpty()) {
            return null;
        }

        if (itemRows.isEmpty()) {
            return null;
        }

        double totalTaxable = 0.0;
        double totalCgst = 0.0;
        double totalSgst = 0.0;
        double totalIgst = 0.0;

        // Build invoice jobs for each selected job using edited fields from tableItems.
        for (model.JobSummary js : loadedJobSummaries) {
            if (!selectedJobUuids.contains(js.getUuid())) {
                continue;
            }

            model.InvoiceJob invJob = new model.InvoiceJob();
            invJob.setJobId(js.getUuid());
            invJob.setJobNo(js.getJobNo());
            invJob.setJobDate(js.getJobDate());

            ItemRow matchingRow = null;
            for (ItemRow r : itemRows) {
                if (r.getJobUuid().equals(js.getUuid())) {
                    matchingRow = r;
                    break;
                }
            }

            if (matchingRow != null) {
                invJob.setJobName(matchingRow.descriptionProperty().get());
                invJob.setHsnSac(matchingRow.hsnSacProperty().get());
                invJob.setQuantity((long) matchingRow.qtyRaw.get());
                invJob.setUnit(matchingRow.unitProperty().get());
                invJob.setRatePerUnit(matchingRow.rateRaw.get());
                invJob.setGstRate(matchingRow.gstRateRaw.get());
                invJob.addLine(new model.InvoiceLine(matchingRow.descriptionProperty().get(), matchingRow.taxableRaw.get()));
                
                totalTaxable += matchingRow.taxableRaw.get();
                totalCgst += matchingRow.cgstRaw.get();
                totalSgst += matchingRow.sgstRaw.get();
                totalIgst += matchingRow.igstRaw.get();
            } else {
                double jobTaxable = jobService.getSumJobItemsAmountForJobUuids(List.of(js.getUuid()));
                long jobQty = jobService.getTotalPrintingQtyForJobUuids(List.of(js.getUuid()));
                invJob.setJobName(js.getJobTitle());
                invJob.setQuantity(jobQty);
                invJob.setUnit("PCS");
                if (jobQty > 0) {
                     invJob.setRatePerUnit(jobTaxable / jobQty);
                }
                invJob.setHsnSac("4821");
                invJob.setGstRate(0.18);
                invJob.addLine(new model.InvoiceLine(js.getJobTitle(), jobTaxable));
                
                totalTaxable += jobTaxable;
                double gstRate = 0.18;
                if (isIntraStateSupply()) {
                    totalCgst += Math.round(jobTaxable * (gstRate / 2.0) * 100.0) / 100.0;
                    totalSgst += Math.round(jobTaxable * (gstRate / 2.0) * 100.0) / 100.0;
                } else {
                    totalIgst += Math.round(jobTaxable * gstRate * 100.0) / 100.0;
                }
            }
            invoice.addJob(invJob);
        }

        // Now loop through any custom rows that are NOT associated with a selected job!
        List<ItemRow> customRowsList = new ArrayList<>();
        java.util.Set<String> selectedUuidsSet = new java.util.HashSet<>(selectedJobUuids);
        for (ItemRow r : itemRows) {
            if (!selectedUuidsSet.contains(r.getJobUuid())) {
                customRowsList.add(r);
                
                String printedDesc = r.getDescription();
                String pctStr = r.getGstPercent();
                if (pctStr != null && !pctStr.isBlank() && !pctStr.equals("—")) {
                    printedDesc = printedDesc + " - " + pctStr;
                }

                model.InvoiceJob invJob = new model.InvoiceJob();
                invJob.setJobId(r.getJobUuid());
                invJob.setJobNo(""); // No job number for custom items
                invJob.setJobDate(invoice.getInvoiceDate());
                invJob.setJobName(printedDesc);
                invJob.setHsnSac(r.getHsnSac());
                invJob.setQuantity((long) r.qtyRaw.get());
                invJob.setUnit(r.getUnit());
                invJob.setRatePerUnit(r.rateRaw.get());
                invJob.setGstRate(r.gstRateRaw.get());
                invJob.addLine(new model.InvoiceLine(printedDesc, r.taxableRaw.get()));

                totalTaxable += r.taxableRaw.get();
                totalCgst += r.cgstRaw.get();
                totalSgst += r.sgstRaw.get();
                totalIgst += r.igstRaw.get();

                invoice.addJob(invJob);
            }
        }

        // Consignee defaults to Ship To selection; if missing, reuse buyer.
        model.Client ship = comboShipTo != null ? comboShipTo.getValue() : null;
        if (ship == null) {
            ship = buyer;
        }
        if (ship != null) {
            String n = ship.getBusinessName() != null && !ship.getBusinessName().isBlank()
                    ? ship.getBusinessName()
                    : ship.getClientName();
            invoice.setConsigneeName(n);
            invoice.setConsigneeAddress(ship.getShippingAddress());
            invoice.setConsigneeGstin(ship.getGst());
            invoice.setConsigneeStateName(comboPlaceOfSupply != null ? comboPlaceOfSupply.getValue() : null);
        }

        // Ensure grand total includes taxes and correct round off
        double unroundedTotal = totalTaxable + totalCgst + totalSgst + totalIgst;
        double grandTotal = Math.round(unroundedTotal);
        invoice.setGrandTotal(grandTotal);
        invoice.setTotalAfterTax(unroundedTotal);
        invoice.setRoundOff(grandTotal - unroundedTotal);

        return invoice;
    }

    /**
     * Saves a snapshot of ALL invoice rows (regular job rows + custom charges) into
     * invoice_additional_charges so that re-downloads always use the values that were
     * shown at the time of original invoice generation — not live job_items data.
     */
    private void saveInvoiceAdditionalChargesToDb(Connection con, String invoiceUuid, List<ItemRow> allRows) throws SQLException {
        // Soft-delete existing rows for this invoice first (idempotent re-save preserving recovery data)
        String deleteSql = "UPDATE invoice_additional_charges SET is_deleted = 1, updated_at = datetime('now'), sync_status = 'PENDING' WHERE invoice_uuid = ?";
        try (PreparedStatement psDel = con.prepareStatement(deleteSql)) {
            psDel.setString(1, invoiceUuid);
            psDel.executeUpdate();
        }

        String insertSql = """
            INSERT INTO invoice_additional_charges (
                uuid, invoice_uuid, charge_type, description, amount, hsn_sac, gst_rate, taxable_flag,
                sync_status, sync_version, is_deleted, is_active, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, 'PENDING', 1, 0, 1, datetime('now'), datetime('now'))
            """;

        try (PreparedStatement ps = con.prepareStatement(insertSql)) {
            for (ItemRow cr : allRows) {
                String chargeType;
                if (!cr.isCustom() && !cr.isCharge() && cr.getJobUuid() != null) {
                    chargeType = "JOB";
                } else if (cr.isCharge()) {
                    chargeType = "CHARGE";
                } else {
                    chargeType = "ITEM";
                }
                String serializedDesc = "QTY:" + cr.qtyRaw.get()
                        + "|UNIT:" + cr.getUnit()
                        + "|RATE:" + cr.rateRaw.get()
                        + "|HSN:" + cr.getHsnSac()
                        + "|GST:" + cr.gstRateRaw.get()
                        + "|DESC:" + cr.getDescription();
                String rowUuid = ("JOB".equals(chargeType) && cr.getJobUuid() != null)
                        ? cr.getJobUuid()
                        : utils.ClientIdentifiers.newUuidString();
                ps.setString(1, rowUuid);
                ps.setString(2, invoiceUuid);
                ps.setString(3, chargeType);
                ps.setString(4, serializedDesc);
                ps.setDouble(5, cr.taxableRaw.get());
                ps.setString(6, cr.getHsnSac());
                ps.setDouble(7, cr.gstRateRaw.get());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void processInvoiceGeneration(String status, boolean downloadPdf) {
        try {
            model.Invoice invoice = buildInvoiceModel();
            if (invoice == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Missing Items");
                alert.setHeaderText("Nothing to generate");
                alert.setContentText("Please select jobs or add items before generating the invoice.");
                alert.showAndWait();
                return;
            }
            invoice.setStatus(status);

            String invoiceUuid = invoiceService.saveGeneratedInvoice(invoice, "GST_INVOICE", status, null);

            // Update regular jobs and save custom jobs to DB
            try (Connection con = utils.DBConnection.getConnection()) {
                con.setAutoCommit(false);
                try {
                    // Update descriptions of regular (selected) jobs that were edited
                    for (model.JobSummary js : loadedJobSummaries) {
                        if (!selectedJobUuids.contains(js.getUuid())) {
                            continue;
                        }
                        ItemRow matchingRow = null;
                        for (ItemRow r : itemRows) {
                            if (r.getJobUuid().equals(js.getUuid())) {
                                matchingRow = r;
                                break;
                            }
                        }
                        if (matchingRow != null) {
                            String editedDesc = matchingRow.getDescription();
                            String updateJobSql = "UPDATE jobs SET description = ?, amount = ?, updated_at = datetime('now'), sync_status = 'PENDING' WHERE uuid = ?";
                            try (PreparedStatement ps = con.prepareStatement(updateJobSql)) {
                                ps.setString(1, editedDesc);
                                ps.setDouble(2, matchingRow.taxableRaw.get());
                                ps.setString(3, js.getUuid());
                                ps.executeUpdate();
                            }

                            // Update job items: keep only one job item with the edited description
                            List<String> itemUuids = new ArrayList<>();
                            String selectItemsSql = "SELECT uuid FROM job_items WHERE job_uuid = ? AND COALESCE(is_deleted, 0) = 0 ORDER BY sort_order, created_at";
                            try (PreparedStatement psSelect = con.prepareStatement(selectItemsSql)) {
                                psSelect.setString(1, js.getUuid());
                                try (ResultSet rs = psSelect.executeQuery()) {
                                    while (rs.next()) {
                                        itemUuids.add(rs.getString(1));
                                    }
                                }
                            }

                            if (!itemUuids.isEmpty()) {
                                String updateFirstItemSql = "UPDATE job_items SET description = ?, amount = ?, updated_at = datetime('now'), sync_status = 'PENDING' WHERE uuid = ?";
                                try (PreparedStatement psUpdateItem = con.prepareStatement(updateFirstItemSql)) {
                                    psUpdateItem.setString(1, editedDesc);
                                    psUpdateItem.setDouble(2, matchingRow.taxableRaw.get());
                                    psUpdateItem.setString(3, itemUuids.get(0));
                                    psUpdateItem.executeUpdate();
                                }
                                if (itemUuids.size() > 1) {
                                    String deleteOtherItemsSql = "UPDATE job_items SET is_deleted = 1, sync_status = 'PENDING', updated_at = datetime('now') WHERE job_uuid = ? AND uuid <> ?";
                                    try (PreparedStatement psDelete = con.prepareStatement(deleteOtherItemsSql)) {
                                        psDelete.setString(1, js.getUuid());
                                        psDelete.setString(2, itemUuids.get(0));
                                        psDelete.executeUpdate();
                                    }
                                }
                            } else {
                                String newItemUuid = utils.ClientIdentifiers.newUuidString();
                                String insertItemSql = """
                                    INSERT INTO job_items (
                                        uuid, job_uuid, type, description, amount, sort_order, sync_status, sync_version,
                                        is_deleted, is_active, created_at, updated_at
                                    ) VALUES (?, ?, 'OTHER', ?, ?, 1, 'PENDING', 1, 0, 1, datetime('now'), datetime('now'))
                                    """;
                                try (PreparedStatement psInsert = con.prepareStatement(insertItemSql)) {
                                    psInsert.setString(1, newItemUuid);
                                    psInsert.setString(2, js.getUuid());
                                    psInsert.setString(3, editedDesc);
                                    psInsert.setDouble(4, matchingRow.taxableRaw.get());
                                    psInsert.executeUpdate();
                                }
                            }
                        }
                    }

                    saveInvoiceAdditionalChargesToDb(con, invoiceUuid, itemRows);
                    
                    con.commit();
                } catch (Exception ex) {
                    con.rollback();
                    throw ex;
                }
            }

            if (downloadPdf) {
                // Generate the premium GST PDF
                java.io.File pdfFile = pdfService.generateGstInvoice(invoice);

                // Auto-open the PDF
                if (pdfFile != null && pdfFile.exists()) {
                    if (java.awt.Desktop.isDesktopSupported()) {
                        java.awt.Desktop.getDesktop().open(pdfFile);
                    }
                }
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText(null);
            if (downloadPdf) {
                alert.setContentText("Invoice " + invoice.getInvoiceNo() + " has been generated as DRAFT and downloaded.");
            } else {
                alert.setContentText("Invoice " + invoice.getInvoiceNo() + " has been generated as DRAFT successfully.");
            }
            alert.showAndWait();
            
            handleClearAll();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Failed to generate invoice");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
            e.printStackTrace();
        }
    }

    private void addCustomItemRow(String selectedItem) {
        String description = selectedItem.toUpperCase();
        
        // Fetch HSN & GST % from HSN master sheet (except for Custom Item, or if not found)
        String hsn = "—";
        double gstRate = DEFAULT_GST_RATE;
        
        if (!"CUSTOM ITEM".equals(description)) {
            try {
                model.HsnSacInfo info = hsnSacService.findBestMatchByNameOrDesc(description);
                if (info == null) {
                    // Try looking up the original option names without the extra word "CHARGES" if not found
                    String baseName = description.replace(" CHARGES", "").trim();
                    info = hsnSacService.findBestMatchByNameOrDesc(baseName);
                }
                if (info != null) {
                    hsn = info.getHsnSac() != null ? info.getHsnSac() : "—";
                    if (info.getGstRate() > 0) {
                        gstRate = info.getGstRate();
                    }
                }
            } catch (Exception e) {
                // fallback to defaults
            }
        }
        
        String customJobUuid = utils.ClientIdentifiers.newUuidString();
        int sl = itemRows.size() + 1;
        
        boolean isChg = !"CUSTOM ITEM".equals(description);
        ItemRow newRow = new ItemRow(
            customJobUuid,
            sl,
            description,
            hsn,
            isChg ? 0.0 : 1.0,
            isChg ? "" : "PCS",
            0.0,
            gstRate,
            isIntraStateSupply(),
            true, // isCustom
            isChg  // isCharge
        );
        
        itemRows.add(newRow);
        refreshHsnSummaryFromItemRows();
        refreshInvoiceSummary();
        
        // Focus the first editable field (Rate/Total) after row creation
        javafx.application.Platform.runLater(() -> {
            int rowIndex = itemRows.indexOf(newRow);
            if (rowIndex >= 0 && rowIndex < tableItems.getItems().size()) {
                tableItems.requestFocus();
                tableItems.getSelectionModel().select(rowIndex);
                int colIndex = newRow.isCharge() ? 11 : 3;
                if (tableItems.getColumns().size() > colIndex) {
                    TableColumn<ItemRow, ?> targetCol = tableItems.getColumns().get(colIndex);
                    tableItems.getFocusModel().focus(rowIndex, targetCol);
                    tableItems.edit(rowIndex, targetCol);
                }
            }
        });
    }

    @FXML
    private void handleFreightOutward() {
        addCustomItemRow("FREIGHT OUTWARD CHARGES");
    }

    @FXML
    private void handlePackingCharges() {
        addCustomItemRow("PACKING CHARGES");
    }

    @FXML
    private void handleDesignCharges() {
        addCustomItemRow("DESIGN CHARGES");
    }

    @FXML
    private void handleMiscCharges() {
        addCustomItemRow("MISCELLANEOUS CHARGES");
    }

    @FXML
    private void handleCustomItem() {
        addCustomItemRow("CUSTOM ITEM");
    }

    @FXML
    private void handleImportJob() {
        System.out.println("Importing Job...");
    }

    @FXML
    private void handleClearAll() {
        if (comboBillTo != null) comboBillTo.getSelectionModel().clearSelection();
        if (comboShipTo != null) comboShipTo.getSelectionModel().clearSelection();
        if (comboPlaceOfSupply != null) comboPlaceOfSupply.getSelectionModel().clearSelection();
        if (comboPaymentTerms != null) comboPaymentTerms.getSelectionModel().clearSelection();
        
        if (dpInvoiceDate != null) dpInvoiceDate.setValue(LocalDate.now());
        if (dpDueDate != null) dpDueDate.setValue(null);
        if (dpPoDate != null) dpPoDate.setValue(null);
        
        if (txtVehicleDispatch != null) txtVehicleDispatch.clear();
        if (txtPoNo != null) txtPoNo.clear();
        if (txtDispatchThrough != null) txtDispatchThrough.clear();
        if (txtLrTrackingNo != null) txtLrTrackingNo.clear();
        if (txtRemarks != null) txtRemarks.clear();
        if (txtEwayBillNo != null) txtEwayBillNo.clear();
        
        itemRows.clear();
        loadedJobSummaries.clear();
        selectedJobUuids.clear();
        
        refreshHsnSummaryFromItemRows();
        refreshInvoiceSummary();
        refreshInvoiceNoPreview();
    }

    @FXML
    private void handleEditTerms() {
        TextArea area = new TextArea();
        area.setWrapText(true);
        area.setText(termsText.get() != null ? termsText.get() : "");
        area.textProperty().addListener((obs, oldV, newV) -> termsText.set(newV));
        area.getStyleClass().addAll("text-area");
        area.setPrefRowCount(10);

        BorderPane root = new BorderPane(area);
        root.setStyle("-fx-padding: 12; -fx-background-color: #FAF6F0;");

        Button btnClose = new Button("Done");
        btnClose.getStyleClass().addAll("action-btn-custom", "btn-terracotta");
        HBox footer = new HBox(btnClose);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setStyle("-fx-padding: 10 0 0 0;");
        root.setBottom(footer);

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Terms & Conditions");
        Scene scene = new Scene(root, 720, 360);
        scene.getStylesheets().addAll(
                getClass().getResource("/css/theme.css").toExternalForm(),
                getClass().getResource("/css/view_job.css").toExternalForm(),
                getClass().getResource("/css/genrate_gst_invoice.css").toExternalForm());
        dlg.setScene(scene);

        btnClose.setOnAction(e -> dlg.close());
        dlg.showAndWait();
    }

    public static final class ItemRow {
        private final String jobUuid;
        private final int slNo;
        private final boolean isCustom;
        private final boolean isCharge;
        
        private final StringProperty description = new SimpleStringProperty("");
        private final StringProperty qty = new SimpleStringProperty("");
        private final StringProperty unit = new SimpleStringProperty("");
        private final StringProperty rate = new SimpleStringProperty("");
        private final StringProperty taxable = new SimpleStringProperty("");
        private final StringProperty hsnSac = new SimpleStringProperty("—");
        private final StringProperty gstPercent = new SimpleStringProperty("—");
        private final StringProperty cgst = new SimpleStringProperty("—");
        private final StringProperty sgst = new SimpleStringProperty("—");
        private final StringProperty igst = new SimpleStringProperty("—");
        private final StringProperty total = new SimpleStringProperty("—");
        
        private final DoubleProperty qtyRaw = new SimpleDoubleProperty(0);
        private final DoubleProperty rateRaw = new SimpleDoubleProperty(0);
        private final DoubleProperty taxableRaw = new SimpleDoubleProperty(0);
        private final DoubleProperty cgstRaw = new SimpleDoubleProperty(0);
        private final DoubleProperty sgstRaw = new SimpleDoubleProperty(0);
        private final DoubleProperty igstRaw = new SimpleDoubleProperty(0);
        private final DoubleProperty gstRateRaw = new SimpleDoubleProperty(DEFAULT_GST_RATE);
        private boolean intraState = true; // Kept for recalculation

        private ItemRow(
                String jobUuid,
                int slNo,
                String desc,
                String hsnSac,
                double qtyRawVal,
                String unitVal,
                double rateRawVal,
                double gstRateRawVal,
                boolean intraState,
                boolean isCustom,
                boolean isCharge) {
            this.jobUuid = jobUuid;
            this.slNo = slNo;
            this.isCustom = isCustom;
            this.isCharge = isCharge;
            this.description.set(desc != null ? desc : "");
            this.hsnSac.set(hsnSac != null ? hsnSac : "—");
            this.unit.set(isCharge ? "" : (unitVal != null ? unitVal : "PCS"));
            this.intraState = intraState;
            this.gstRateRaw.set(gstRateRawVal);

            this.qtyRaw.set(isCharge ? 0 : qtyRawVal);
            this.qty.set(isCharge ? "" : ("NA".equalsIgnoreCase(qty.get()) ? "NA" : String.valueOf((long)qtyRawVal)));
            this.rateRaw.set(isCharge ? 0 : rateRawVal);
            this.rate.set(isCharge ? "" : ("NA".equalsIgnoreCase(rate.get()) ? "NA" : fmtMoney(rateRawVal)));

            if (isCharge) {
                this.taxableRaw.set(0);
                this.taxable.set("");
                recalcTaxes(gstRateRawVal, intraState);
            } else {
                recalcTaxable();
            }
        }

        static ItemRow ofJob(int slNo, String jobUuid, String desc, String hsnSac, long qty, String unit, double rate, double gstRate, boolean intraState) {
            return new ItemRow(jobUuid, slNo, desc, hsnSac, qty, unit, rate, gstRate, intraState, false, false);
        }

        private static String fmtMoney(double v) {
            return String.format("₹ %.2f", v);
        }

        public boolean isCustom() {
            return isCustom;
        }

        public boolean isCharge() {
            return isCharge;
        }

        public void recalcTaxable() {
            if (isCharge) {
                taxable.set("");
                qty.set("");
                rate.set("");
                return;
            }
            double newTaxable = qtyRaw.get() * rateRaw.get();
            taxableRaw.set(newTaxable);
            taxable.set(fmtMoney(newTaxable));
            recalcTaxes(gstRateRaw.get(), this.intraState);
        }

        public void recalcTaxes(double gstRate, boolean intraState) {
            this.intraState = intraState;
            gstRateRaw.set(gstRate);
            Taxes taxes = Taxes.compute(taxableRaw.get(), gstRate, intraState);
            String pct = String.format("%.0f%%", gstRate * 100.0);
            gstPercent.set(pct);
            cgstRaw.set(taxes.cgst());
            sgstRaw.set(taxes.sgst());
            igstRaw.set(taxes.igst());
            cgst.set(!isCharge && taxes.cgst() > 0 ? fmtMoney(taxes.cgst()) : (isCharge ? "" : "—"));
            sgst.set(!isCharge && taxes.sgst() > 0 ? fmtMoney(taxes.sgst()) : (isCharge ? "" : "—"));
            igst.set(!isCharge && taxes.igst() > 0 ? fmtMoney(taxes.igst()) : (isCharge ? "" : "—"));
            total.set(fmtMoney(taxableRaw.get()));
        }

        public String getJobUuid() { return jobUuid; }
        public int getSlNo() { return slNo; }

        public String getDescription() { return description.get(); }
        public void setDescription(String v) { description.set(v); }
        public StringProperty descriptionProperty() { return description; }

        public String getQty() { return isCharge ? "" : qty.get(); }
        public void setQty(String v) { 
            if (isCharge) return;
            qty.set(v);
            if ("NA".equalsIgnoreCase(v) || "N/A".equalsIgnoreCase(v)) {
                qtyRaw.set(0);
            } else {
                try {
                    qtyRaw.set(Double.parseDouble(v.replace(",", "").trim()));
                } catch(Exception e) { service.LoggerService.debug("Failed to parse QTY: " + e.getMessage()); qtyRaw.set(0); }
            }
            recalcTaxable();
        }
        public StringProperty qtyProperty() { return isCharge ? new SimpleStringProperty("") : qty; }

        public String getUnit() { return isCharge ? "" : unit.get(); }
        public void setUnit(String v) { 
            if (isCharge) return;
            unit.set(v); 
        }
        public StringProperty unitProperty() { return isCharge ? new SimpleStringProperty("") : unit; }

        public String getRate() { return isCharge ? "" : rate.get(); }
        public void setRate(String v) { 
            if (isCharge) return;
            rate.set(v); 
            if ("NA".equalsIgnoreCase(v) || "N/A".equalsIgnoreCase(v)) {
                rateRaw.set(0);
            } else {
                try {
                    rateRaw.set(Double.parseDouble(v.replace("₹", "").replace(",", "").trim()));
                } catch(Exception e) { service.LoggerService.debug("Failed to parse Rate: " + e.getMessage()); rateRaw.set(0); }
            }
            recalcTaxable();
        }
        public StringProperty rateProperty() { return isCharge ? new SimpleStringProperty("") : rate; }

        public String getTaxable() { return isCharge ? "" : taxable.get(); }
        public StringProperty taxableProperty() { return isCharge ? new SimpleStringProperty("") : taxable; }

        public String getHsnSac() { return hsnSac.get(); }
        public void setHsnSac(String code) {
            hsnSac.set(code != null && !code.isBlank() ? code.trim() : "—");
        }
        public StringProperty hsnSacProperty() { return hsnSac; }

        public String getGstPercent() { return gstPercent.get(); }
        public StringProperty gstPercentProperty() { return gstPercent; }

        public String getCgst() { return isCharge ? "" : cgst.get(); }
        public StringProperty cgstProperty() { return isCharge ? new SimpleStringProperty("") : cgst; }

        public String getSgst() { return isCharge ? "" : sgst.get(); }
        public StringProperty sgstProperty() { return isCharge ? new SimpleStringProperty("") : sgst; }

        public String getIgst() { return isCharge ? "" : igst.get(); }
        public StringProperty igstProperty() { return isCharge ? new SimpleStringProperty("") : igst; }

        public String getTotal() { return total.get(); }
        public void setTotal(String v) {
            if (!isCustom) return;
            try {
                double totalVal = Double.parseDouble(v.replace("₹", "").replace(",", "").trim());
                double g = gstRateRaw.get();
                double newTaxable = totalVal;
                taxableRaw.set(newTaxable);
                taxable.set(fmtMoney(newTaxable));
                
                if (!isCharge) {
                    if (qtyRaw.get() > 0) {
                        rateRaw.set(newTaxable / qtyRaw.get());
                    } else {
                        rateRaw.set(newTaxable);
                    }
                    rate.set(fmtMoney(rateRaw.get()));
                }
                recalcTaxes(g, this.intraState);
            } catch (Exception e) { service.LoggerService.debug("Failed to calculate total: " + e.getMessage()); }
        }
        public StringProperty totalProperty() { return total; }
    }

    public static final class HsnSummaryRow {
        private final String hsnSac;
        private final String taxable;
        private final String gstPercent;
        private final String cgst;
        private final String sgst;
        private final String igst;
        private final String totalTax;

        private HsnSummaryRow(String hsnSac, String taxable, String gstPercent, String cgst, String sgst, String igst,
                String totalTax) {
            this.hsnSac = hsnSac;
            this.taxable = taxable;
            this.gstPercent = gstPercent;
            this.cgst = cgst;
            this.sgst = sgst;
            this.igst = igst;
            this.totalTax = totalTax;
        }

        static HsnSummaryRow of(String hsnSac, double taxable, double gstRate, double cgst, double sgst, double igst) {
            double totalTax = round2(cgst + sgst + igst);
            String gstPct = String.format("%.0f%%", gstRate * 100.0);
            return new HsnSummaryRow(
                    hsnSac != null ? hsnSac : "—",
                    ItemRow.fmtMoney(taxable),
                    gstPct,
                    cgst > 0 ? ItemRow.fmtMoney(cgst) : "—",
                    sgst > 0 ? ItemRow.fmtMoney(sgst) : "—",
                    igst > 0 ? ItemRow.fmtMoney(igst) : "—",
                    ItemRow.fmtMoney(totalTax));
        }

        public String getHsnSac() {
            return hsnSac;
        }

        public String getTaxable() {
            return taxable;
        }

        public String getGstPercent() {
            return gstPercent;
        }

        public String getCgst() {
            return cgst;
        }

        public String getSgst() {
            return sgst;
        }

        public String getIgst() {
            return igst;
        }

        public String getTotalTax() {
            return totalTax;
        }
    }

    private void showJobItemsPopup(String jobUuid, String jobTitle) {
        if (jobUuid == null || jobUuid.isBlank()) {
            return;
        }
        
        List<model.JobItem> items = List.of();
        try {
            items = jobItemService.getJobItems(jobUuid);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Stage stage = new Stage(javafx.stage.StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        if (tableItems.getScene() != null && tableItems.getScene().getWindow() != null) {
            stage.initOwner(tableItems.getScene().getWindow());
        }
        
        VBox root = new VBox(20);
        root.setStyle("-fx-background-color: #FAF6F0; -fx-background-radius: 16; -fx-padding: 32; " +
                      "-fx-border-width: 0; -fx-background-insets: 0; " +
                      "-fx-effect: dropshadow(three-pass-box, rgba(62, 49, 45, 0.15), 30, 0, 0, 15);");
        root.setMinWidth(550);
        root.setMaxWidth(650);

        // Header Section
        HBox header = new HBox();
        header.setAlignment(Pos.TOP_LEFT);
        
        VBox titleBox = new VBox(4);
        Label idLbl = new Label("JOB ITEMS");
        idLbl.setStyle("-fx-text-fill: #CD7B4E; -fx-font-weight: 800; -fx-font-size: 11px; -fx-letter-spacing: 0.1em;");
        
        Label titleLbl = new Label(jobTitle);
        titleLbl.setStyle("-fx-text-fill: #3E312D; -fx-font-weight: 800; -fx-font-size: 20px; -fx-font-family: 'Inter';");
        
        titleBox.getChildren().addAll(idLbl, titleLbl);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("view-clear-btn");
        closeBtn.setStyle("-fx-min-width: 32; -fx-min-height: 32; -fx-font-size: 14px; -fx-padding: 0;");
        closeBtn.setOnAction(e -> stage.close());
        
        header.getChildren().addAll(titleBox, spacer, closeBtn);

        // Items table
        TableView<model.JobItem> table = new TableView<>();
        table.getStyleClass().add("job-details-popup-table");
        table.setPrefHeight(250);
        table.setMinHeight(120);

        TableColumn<model.JobItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setPrefWidth(100);
        typeCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue() != null && cd.getValue().getType() != null ? cd.getValue().getType() : ""));
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                setText(empty || type == null || type.isBlank() ? null : type);
                setGraphic(null);
            }
        });

        TableColumn<model.JobItem, String> descCol = new TableColumn<>("Description");
        descCol.setPrefWidth(280);
        descCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue() != null && cd.getValue().getDescription() != null ? cd.getValue().getDescription() : ""));
        descCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String desc, boolean empty) {
                super.updateItem(desc, empty);
                setText(empty || desc == null || desc.isBlank() ? null : desc);
                setGraphic(null);
            }
        });

        TableColumn<model.JobItem, String> amtCol = new TableColumn<>("Amount");
        amtCol.setPrefWidth(100);
        amtCol.getStyleClass().add("job-details-amt-cell");
        amtCol.setCellValueFactory(cd -> {
            if (cd.getValue() == null) {
                return new javafx.beans.property.SimpleStringProperty("");
            }
            return new javafx.beans.property.SimpleStringProperty(
                    String.format("%.2f", cd.getValue().getAmount()));
        });
        amtCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String amtText, boolean empty) {
                super.updateItem(amtText, empty);
                if (empty || amtText == null || amtText.isBlank()) {
                    setText(null);
                } else {
                    setText("₹ " + amtText);
                }
                setGraphic(null);
                if (!getStyleClass().contains("job-details-amt-cell")) {
                    getStyleClass().add("job-details-amt-cell");
                }
            }
        });

        table.getColumns().addAll(typeCol, descCol, amtCol);
        table.setItems(FXCollections.observableArrayList(items));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        if (items.isEmpty()) {
            table.setPlaceholder(new Label("No line items for this job."));
        }

        // Grand Total Section
        HBox totalBox = new HBox(12);
        totalBox.setAlignment(Pos.CENTER_RIGHT);
        totalBox.setPadding(new Insets(10, 0, 0, 0));
        
        Label totalLbl = new Label("Grand Total:");
        totalLbl.setStyle("-fx-text-fill: #8B7E74; -fx-font-weight: 700; -fx-font-size: 14px;");
        
        Label totalVal = new Label("₹ " + String.format("%.2f", items.stream().mapToDouble(model.JobItem::getAmount).sum()));
        totalVal.setStyle("-fx-text-fill: #CD7B4E; -fx-font-weight: 800; -fx-font-size: 24px; -fx-font-family: 'Manrope';");
        
        totalBox.getChildren().addAll(totalLbl, totalVal);

        root.getChildren().addAll(header, table, totalBox);

        // Full Screen Overlay for quick dismissal
        StackPane overlay = new StackPane(root);
        overlay.setStyle("-fx-background-color: transparent;");
        overlay.setPadding(new Insets(50));
        overlay.setOnMouseClicked(e -> {
            if (e.getTarget() == overlay) stage.close();
        });

        Scene scene = new Scene(overlay);
        scene.setFill(Color.TRANSPARENT);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/view_job.css").toExternalForm());
        } catch(Exception e) {}
        
        stage.setScene(scene);
        stage.showAndWait();
    }
}
