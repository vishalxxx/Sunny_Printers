# Graph Report - Sunny_Printers  (2026-05-02)

## Corpus Check
- 116 files · ~867,986 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1827 nodes · 5322 edges · 23 communities detected
- Extraction: 54% EXTRACTED · 46% INFERRED · 0% AMBIGUOUS · INFERRED: 2467 edges (avg confidence: 0.8)
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
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]

## God Nodes (most connected - your core abstractions)
1. `MainController` - 79 edges
2. `ViewInvoicesController` - 75 edges
3. `InvoiceMaster` - 65 edges
4. `AddJobController` - 52 edges
5. `SystemSettings` - 49 edges
6. `ViewJobsController` - 43 edges
7. `GenerateInvoiceController` - 42 edges
8. `MainController` - 41 edges
9. `CtpPlate` - 41 edges
10. `Client` - 38 edges

## Surprising Connections (you probably didn't know these)
- `MainController` --implements--> `Initializable`  [EXTRACTED]
  head_main_controller.java →   _Bridges community 10 → community 14_
- `ClientEditSelectionController` --implements--> `Initializable`  [EXTRACTED]
  src\main\java\controller\ClientEditSelectionController.java →   _Bridges community 14 → community 3_
- `ClientLedgerController` --implements--> `Initializable`  [EXTRACTED]
  src\main\java\controller\ClientLedgerController.java →   _Bridges community 14 → community 16_
- `ClientProfileController` --implements--> `Initializable`  [EXTRACTED]
  src\main\java\controller\ClientProfileController.java →   _Bridges community 14 → community 0_
- `GeneralSettingsController` --implements--> `Initializable`  [EXTRACTED]
  src\main\java\controller\GeneralSettingsController.java →   _Bridges community 14 → community 15_

## Communities

### Community 0 - "Community 0"
Cohesion: 0.03
Nodes (8): ClientProfileController, Invoice, InvoiceMaster, InvoiceMasterRepository, InvoiceBuilderService, InvoiceHistoryRowService, InvoiceMasterService, InvoiceSummaryDialogUtil

### Community 1 - "Community 1"
Cohesion: 0.04
Nodes (14): BindingTabController, CtpTabController, EditJobController, LaminationTabController, PaperTabController, PrintingTabController, Printing, BindingItemRepository (+6 more)

### Community 2 - "Community 2"
Cohesion: 0.02
Nodes (17): GenerateInvoiceController, JobItem, Job, ClientRepository, InvoiceHistoryRepo, JobItemRepository, JobRepository, SchemaChecker (+9 more)

### Community 3 - "Community 3"
Cohesion: 0.03
Nodes (10): ClientEditSelectionController, ClientFormController, BoldListCell, ClientCardCell, InsightListCell, NormalListCell, ViewClientsController, Client (+2 more)

### Community 4 - "Community 4"
Cohesion: 0.04
Nodes (5): AddJobController, CtpPlate, AtomicDB, SQLConsumer, SQLFunction

### Community 5 - "Community 5"
Cohesion: 0.04
Nodes (5): MainController, BreadcrumbUtil, DirtySupport, NavigationManager, NavState

### Community 6 - "Community 6"
Cohesion: 0.04
Nodes (10): InvoiceJob, JobSummary, PrintableLine, InvoiceGenerationService, InvoiceStorageService, PdfInvoiceService, CompanyDataLayout, dirName() (+2 more)

### Community 7 - "Community 7"
Cohesion: 0.05
Nodes (8): SystemSettingsController, getLabel(), getTypeCode(), SystemSettings, SystemSettingsRepository, SettingsService, DocumentNumbering, UndoDeleteManager

### Community 8 - "Community 8"
Cohesion: 0.03
Nodes (4): Binding, Lamination, Paper, Serializable

### Community 9 - "Community 9"
Cohesion: 0.04
Nodes (7): AdjustmentCell, EditingBigDecimalCell, InvoiceRow, NetPaidCell, PaymentRecord, RecordPaymentController, InvoiceAdjustment

### Community 10 - "Community 10"
Cohesion: 0.05
Nodes (6): Application, InvoiceGenerationController, MainController, Main, LoaderManager, Toast

### Community 11 - "Community 11"
Cohesion: 0.06
Nodes (3): CreditDebitNoteController, ViewInvoiceJobsController, ViewJobsController

### Community 12 - "Community 12"
Cohesion: 0.06
Nodes (4): InvoiceActionState, PaymentRecord, RefStatusCell, ViewInvoicesController

### Community 13 - "Community 13"
Cohesion: 0.04
Nodes (11): GlobalLoaderController, LoginController, ProfileSettingsController, ProgressDialogController, User, UserRepository, LoginService, DeleteConfirmationDialog (+3 more)

### Community 14 - "Community 14"
Cohesion: 0.05
Nodes (8): CreditNoteController, DebitNoteController, PaymentHistoryController, PaymentRow, ScreenLoaderController, Initializable, StackPane, ViewportContainer

### Community 15 - "Community 15"
Cohesion: 0.07
Nodes (7): GeneralSettingsController, EmailSettings, Supplier, EmailSettingsRepository, SupplierService, CompanyProfile, EmailUtil

### Community 16 - "Community 16"
Cohesion: 0.08
Nodes (5): ClientComboItem, ClientLedgerController, CurrencyCell, LedgerEntry, StatusCell

### Community 17 - "Community 17"
Cohesion: 0.16
Nodes (2): DownloadsPopupController, DownloadItem

### Community 18 - "Community 18"
Cohesion: 0.14
Nodes (1): JobItem

### Community 19 - "Community 19"
Cohesion: 0.18
Nodes (1): InvoiceLine

### Community 20 - "Community 20"
Cohesion: 0.67
Nodes (1): InvoiceExcelStyles

### Community 23 - "Community 23"
Cohesion: 1.0
Nodes (1): SupplierRepository

### Community 24 - "Community 24"
Cohesion: 1.0
Nodes (1): JobSummaryFormatter

## Knowledge Gaps
- **2 isolated node(s):** `SupplierRepository`, `JobSummaryFormatter`
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 17`** (18 nodes): `DownloadsPopupController`, `.createItemRow()`, `.handleOpenFile()`, `.handleOpenFolder()`, `.handleOpenFolderLocation()`, `.handleViewAll()`, `.initialize()`, `.renderItems()`, `.setDownloads()`, `DownloadItem`, `.DownloadItem()`, `.getDownloadDate()`, `.getFileName()`, `.getFilePath()`, `.getFileSize()`, `.getFileType()`, `DownloadsPopupController.java`, `DownloadItem.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 18`** (14 nodes): `JobItem`, `.getAmount()`, `.getCreatedAt()`, `.getDescription()`, `.getId()`, `.getJobId()`, `.getType()`, `.JobItem()`, `.setAmount()`, `.setCreatedAt()`, `.setId()`, `.setJobId()`, `.setType()`, `JobItem.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 19`** (11 nodes): `InvoiceLine`, `.getAmount()`, `.getDescription()`, `.getSortOrder()`, `.getType()`, `.InvoiceLine()`, `.setAmount()`, `.setDescription()`, `.setSortOrder()`, `.setType()`, `InvoiceLine.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 20`** (4 nodes): `InvoiceExcelStyles.java`, `InvoiceExcelStyles`, `.buildStyle()`, `.InvoiceExcelStyles()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 23`** (2 nodes): `SupplierRepository`, `SupplierRepository.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 24`** (2 nodes): `JobSummaryFormatter.java`, `JobSummaryFormatter`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainController` connect `Community 5` to `Community 10`, `Community 14`?**
  _High betweenness centrality (0.050) - this node is a cross-community bridge._
- **Why does `ViewInvoicesController` connect `Community 12` to `Community 0`, `Community 11`?**
  _High betweenness centrality (0.039) - this node is a cross-community bridge._
- **Why does `Lamination` connect `Community 8` to `Community 1`, `Community 4`?**
  _High betweenness centrality (0.035) - this node is a cross-community bridge._
- **What connects `SupplierRepository`, `JobSummaryFormatter` to the rest of the system?**
  _2 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.03 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.04 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.02 - nodes in this community are weakly interconnected._