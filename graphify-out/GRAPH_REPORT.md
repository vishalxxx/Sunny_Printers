# Graph Report - Sunny_Printers  (2026-05-01)

## Corpus Check
- 106 files · ~860,577 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1658 nodes · 4644 edges · 24 communities detected
- Extraction: 56% EXTRACTED · 44% INFERRED · 0% AMBIGUOUS · INFERRED: 2066 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]

## God Nodes (most connected - your core abstractions)
1. `MainController` - 76 edges
2. `ViewInvoicesController` - 74 edges
3. `InvoiceMaster` - 62 edges
4. `AddJobController` - 52 edges
5. `ViewJobsController` - 43 edges
6. `MainController` - 41 edges
7. `CtpPlate` - 41 edges
8. `GenerateInvoiceController` - 39 edges
9. `Client` - 38 edges
10. `Job` - 35 edges

## Surprising Connections (you probably didn't know these)
- `MainController` --implements--> `Initializable`  [EXTRACTED]
  head_main_controller.java →   _Bridges community 12 → community 3_
- `ClientLedgerController` --implements--> `Initializable`  [EXTRACTED]
  src\main\java\controller\ClientLedgerController.java →   _Bridges community 3 → community 4_
- `GeneralSettingsController` --implements--> `Initializable`  [EXTRACTED]
  src\main\java\controller\GeneralSettingsController.java →   _Bridges community 3 → community 10_
- `MainController` --implements--> `Initializable`  [EXTRACTED]
  src\main\java\controller\MainController.java →   _Bridges community 3 → community 5_
- `PaymentHistoryController` --implements--> `Initializable`  [EXTRACTED]
  src\main\java\controller\PaymentHistoryController.java →   _Bridges community 3 → community 13_

## Communities

### Community 0 - "Community 0"
Cohesion: 0.02
Nodes (12): CreditDebitNoteController, AdjustmentCell, EditingBigDecimalCell, InvoiceRow, NetPaidCell, PaymentRecord, RecordPaymentController, ViewInvoiceJobsController (+4 more)

### Community 1 - "Community 1"
Cohesion: 0.02
Nodes (18): Invoice, Job, JobItem, JobSummary, ClientRepository, InvoiceHistoryRepo, JobItemRepository, JobRepository (+10 more)

### Community 2 - "Community 2"
Cohesion: 0.04
Nodes (14): BindingTabController, CtpTabController, EditJobController, LaminationTabController, PrintingTabController, Printing, BindingItemRepository, CtpItemRepository (+6 more)

### Community 3 - "Community 3"
Cohesion: 0.03
Nodes (15): ClientEditSelectionController, ClientFormController, ClientProfileController, ScreenLoaderController, BoldListCell, ClientCardCell, InsightListCell, NormalListCell (+7 more)

### Community 4 - "Community 4"
Cohesion: 0.03
Nodes (16): ClientComboItem, ClientLedgerController, CurrencyCell, LedgerEntry, StatusCell, GlobalLoaderController, LoginController, ProfileSettingsController (+8 more)

### Community 5 - "Community 5"
Cohesion: 0.05
Nodes (5): MainController, BreadcrumbUtil, DirtySupport, NavigationManager, NavState

### Community 6 - "Community 6"
Cohesion: 0.04
Nodes (5): InvoiceActionState, PaymentRecord, RefStatusCell, ViewInvoicesController, InvoiceAdjustment

### Community 7 - "Community 7"
Cohesion: 0.05
Nodes (6): Application, GenerateInvoiceController, JobItem, InvoiceGenerationController, Main, Toast

### Community 8 - "Community 8"
Cohesion: 0.07
Nodes (4): AddJobController, AtomicDB, SQLConsumer, SQLFunction

### Community 9 - "Community 9"
Cohesion: 0.06
Nodes (4): CreditNoteController, DebitNoteController, PaperTabController, ViewJobsController

### Community 10 - "Community 10"
Cohesion: 0.07
Nodes (9): GeneralSettingsController, SystemSettingsController, EmailSettings, SystemSettings, EmailSettingsRepository, SystemSettingsRepository, SettingsService, EmailUtil (+1 more)

### Community 11 - "Community 11"
Cohesion: 0.06
Nodes (6): InvoiceJob, PrintableLine, InvoiceGenerationService, InvoiceStorageService, PdfInvoiceService, ExcelRegionUtil

### Community 12 - "Community 12"
Cohesion: 0.1
Nodes (1): MainController

### Community 13 - "Community 13"
Cohesion: 0.12
Nodes (2): PaymentHistoryController, PaymentRow

### Community 14 - "Community 14"
Cohesion: 0.07
Nodes (1): Lamination

### Community 15 - "Community 15"
Cohesion: 0.07
Nodes (1): CtpPlate

### Community 16 - "Community 16"
Cohesion: 0.07
Nodes (1): Binding

### Community 17 - "Community 17"
Cohesion: 0.14
Nodes (1): Paper

### Community 18 - "Community 18"
Cohesion: 0.17
Nodes (1): InvoiceHistoryRow

### Community 19 - "Community 19"
Cohesion: 0.13
Nodes (1): DashboardJobDTO

### Community 20 - "Community 20"
Cohesion: 0.18
Nodes (1): InvoiceLine

### Community 21 - "Community 21"
Cohesion: 0.67
Nodes (1): InvoiceExcelStyles

### Community 24 - "Community 24"
Cohesion: 1.0
Nodes (1): SupplierRepository

### Community 25 - "Community 25"
Cohesion: 1.0
Nodes (1): JobSummaryFormatter

## Knowledge Gaps
- **2 isolated node(s):** `SupplierRepository`, `JobSummaryFormatter`
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 12`** (40 nodes): `.setRootPane()`, `MainController`, `.animateSidebarWidth()`, `.applyCollapsedStyleToAll()`, `.collapseSidebar()`, `.expandSidebar()`, `.getInstance()`, `.getRoot()`, `.goDashboard()`, `.hideAllSidebars()`, `.hideCenterLoader()`, `.hideGlobalLoader()`, `.hideSidebar()`, `.initialize()`, `.loadAddClient()`, `.loadAddJob()`, `.loadCenterScreen()`, `.loadClientLedger()`, `.loadInvoiceGenration()`, `.loadInvoiceSettings()`, `.loadRecordPayment()`, `.loadUserSettings()`, `.loadViewClients()`, `.loadViewJob()`, `.MainController()`, `.openCenterDashboard()`, `.openDashboard()`, `.setCenterContent()`, `.setCenterView()`, `.setPageTitle()`, `.showBillingSubmenu()`, `.showCenterLoader()`, `.showClientsSubmenu()`, `.showGlobalLoader()`, `.showJobsSubmenu()`, `.showLedgerSubmenu()`, `.showMainSidebar()`, `.showOnly()`, `.showPaymentSubmenu()`, `.showSettingSubmenu()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 13`** (34 nodes): `PaymentHistoryController`, `.addDetailRow()`, `.initialize()`, `.loadClients()`, `.loadInvoices()`, `.loadPaymentData()`, `.onFilter()`, `.onReset()`, `.refreshTotal()`, `.setupAutoPopupDatePicker()`, `.setupClientCombo()`, `.setupSearchFilter()`, `.setupTableColumns()`, `.setupTableDoubleClickHandler()`, `.showPaymentDetailsDialog()`, `PaymentRow`, `.amountProperty()`, `.clientProperty()`, `.dateProperty()`, `.getAmount()`, `.getAmountRaw()`, `.getClient()`, `.getDate()`, `.getId()`, `.getInvoiceRef()`, `.getMethod()`, `.getReference()`, `.getType()`, `.invoiceRefProperty()`, `.methodProperty()`, `.PaymentRow()`, `.referenceProperty()`, `.typeProperty()`, `.findAllSortedById()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 14`** (32 nodes): `Lamination`, `.captureOriginal()`, `.copy()`, `.getAmount()`, `.getId()`, `.getJobItemId()`, `.getNotes()`, `.getQty()`, `.getSide()`, `.getSize()`, `.getType()`, `.isDeleted()`, `.isDifferentFromOriginal()`, `.isNew()`, `.isSameAsOriginal()`, `.isUpdated()`, `.Lamination()`, `.resetFlags()`, `.setAmount()`, `.setDeleted()`, `.setId()`, `.setJobItemId()`, `.setNew()`, `.setNotes()`, `.setQty()`, `.setSide()`, `.setSize()`, `.setType()`, `.setUpdated()`, `.toShortText()`, `.toString()`, `Lamination.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 15`** (31 nodes): `CtpPlate`, `.captureOriginal()`, `.copy()`, `.CtpPlate()`, `.getAmount()`, `.getColor()`, `.getCreatedAt()`, `.getId()`, `.getJobItemId()`, `.getNotes()`, `.getQty()`, `.getUpdatedAt()`, `.isDeleted()`, `.isDifferentFromOriginal()`, `.isNew()`, `.isSameAsOriginal()`, `.isUpdated()`, `.resetFlags()`, `.setAmount()`, `.setColor()`, `.setCreatedAt()`, `.setDeleted()`, `.setId()`, `.setJobItemId()`, `.setNew()`, `.setNotes()`, `.setQty()`, `.setUpdated()`, `.setUpdatedAt()`, `.toString()`, `CtpPlate.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 16`** (29 nodes): `Binding`, `.Binding()`, `.captureOriginal()`, `.copy()`, `.getAmount()`, `.getId()`, `.getJobItemId()`, `.getNotes()`, `.getOriginalSnapshot()`, `.getQty()`, `.isDeleted()`, `.isDifferentFromOriginal()`, `.isNew()`, `.isSameAsOriginal()`, `.isUpdated()`, `.resetFlags()`, `.setAmount()`, `.setDeleted()`, `.setId()`, `.setJobItemId()`, `.setNew()`, `.setNotes()`, `.setOriginalSnapshot()`, `.setProcess()`, `.setQty()`, `.setRate()`, `.setUpdated()`, `.toString()`, `Binding.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 17`** (28 nodes): `Paper`, `.captureOriginal()`, `.copy()`, `.getAmount()`, `.getJobItemId()`, `.getNotes()`, `.getQty()`, `.getType()`, `.getUnits()`, `.isDeleted()`, `.isDifferentFromOriginal()`, `.isNew()`, `.isSameAsOriginal()`, `.isUpdated()`, `.Paper()`, `.resetFlags()`, `.setAmount()`, `.setDeleted()`, `.setGsm()`, `.setJobItemId()`, `.setNew()`, `.setNotes()`, `.setQty()`, `.setSource()`, `.setType()`, `.setUnits()`, `.setUpdated()`, `.toString()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 18`** (15 nodes): `.setupRecentInvoiceTable()`, `InvoiceHistoryRow`, `.amountProperty()`, `.dateProperty()`, `.getAmount()`, `.getClientName()`, `.getDate()`, `.getInvoiceNo()`, `.getStatus()`, `.getType()`, `.InvoiceHistoryRow()`, `.invoiceNoProperty()`, `.statusProperty()`, `.typeProperty()`, `InvoiceHistoryRow.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 19`** (15 nodes): `DashboardJobDTO`, `.DashboardJobDTO()`, `.dueDateProperty()`, `.getDueDate()`, `.getOrderClient()`, `.getProjectDetails()`, `.getReceived()`, `.getValuation()`, `.getWorkflow()`, `.orderClientProperty()`, `.projectDetailsProperty()`, `.receivedProperty()`, `.valuationProperty()`, `.workflowProperty()`, `DashboardJobDTO.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 20`** (11 nodes): `InvoiceLine`, `.getAmount()`, `.getDescription()`, `.getSortOrder()`, `.getType()`, `.InvoiceLine()`, `.setAmount()`, `.setDescription()`, `.setSortOrder()`, `.setType()`, `InvoiceLine.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 21`** (4 nodes): `InvoiceExcelStyles.java`, `InvoiceExcelStyles`, `.buildStyle()`, `.InvoiceExcelStyles()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 24`** (2 nodes): `SupplierRepository`, `SupplierRepository.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 25`** (2 nodes): `JobSummaryFormatter.java`, `JobSummaryFormatter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ViewInvoicesController` connect `Community 6` to `Community 0`, `Community 9`?**
  _High betweenness centrality (0.043) - this node is a cross-community bridge._
- **Why does `MainController` connect `Community 5` to `Community 1`, `Community 3`, `Community 7`?**
  _High betweenness centrality (0.043) - this node is a cross-community bridge._
- **Why does `Invoice` connect `Community 1` to `Community 11`, `Community 0`, `Community 3`?**
  _High betweenness centrality (0.028) - this node is a cross-community bridge._
- **What connects `SupplierRepository`, `JobSummaryFormatter` to the rest of the system?**
  _2 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.02 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.02 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.04 - nodes in this community are weakly interconnected._