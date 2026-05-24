package controller;

import java.sql.Connection;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.Node;

import model.*;
import repository.*;
import service.JobItemService;
import utils.Toast;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import service.JobService;

public class EditJobController implements utils.DirtySupport {

    @Override
    public boolean hasUnsavedChanges() {
        if (currentJob == null) return false;
        
        // Check if any tab has changes
        boolean changed = (paperTabController != null && paperTabController.hasChanges())
                || (printingTabController != null && printingTabController.hasChanges())
                || (bindingTabController != null && bindingTabController.hasChanges())
                || (laminationTabController != null && laminationTabController.hasChanges())
                || (ctpTabController != null && ctpTabController.hasChanges());
        
        // Check remarks and image
        if (!changed) {
            String currentRemarks = jobRemarksArea.getText() != null ? jobRemarksArea.getText() : "";
            String oldRemarks = currentJob.getRemarks() != null ? currentJob.getRemarks() : "";
            if (!currentRemarks.equals(oldRemarks)) changed = true;
            if (selectedImageFile != null) changed = true;
        }
        
        return changed;
    }

    /* =====================================================
       CURRENT JOB
       ===================================================== */
    private Job currentJob;
    private File selectedImageFile;

    /* =====================================================
       GENERAL TAB
       ===================================================== */
    @FXML private Tab generalTab;
    @FXML private ImageView jobImagePreview;
    @FXML private Label filePlaceholder;
    @FXML private javafx.scene.control.Button uploadJobImageBtn;
    @FXML private javafx.scene.control.TextArea jobRemarksArea;
    @FXML private StackPane previewContainer;

    /* =====================================================
       TABS
       ===================================================== */
    @FXML private javafx.scene.control.TabPane jobTabPane;
    @FXML private Tab paperTab;
    @FXML private Tab printingTab;
    @FXML private Tab ctpTab;
    @FXML private Tab bindingTab;
    @FXML private Tab laminationTab;

    @FXML private Label jobNumberLabel;
    @FXML private Label jobTitleLabel;
    @FXML private HBox breadcrumbContainer;

    /* =====================================================
       TAB CONTROLLERS
       ===================================================== */
    private PaperTabController paperTabController;
    private PrintingTabController printingTabController;
    private BindingTabController bindingTabController;
    private LaminationTabController laminationTabController;
    private CtpTabController ctpTabController;

    /* =====================================================
       LOAD FLAGS
       ===================================================== */
    private boolean paperLoaded;
    private boolean printingLoaded;
    private boolean bindingLoaded;
    private boolean laminationLoaded;
    private boolean ctpLoaded;

    /* =====================================================
       INITIALIZE
       ===================================================== */
    @FXML
    private void initialize() {
        utils.BreadcrumbUtil.populateBreadcrumbs(breadcrumbContainer, null, () -> {
            MainController mc = MainController.getInstance();
            if (mc != null) mc.handleBackUnscoped(null);
        });

        paperTab.setOnSelectionChanged(e -> {
            if (paperTab.isSelected() && currentJob != null && !paperLoaded) {
                loadPaperTab();
                paperLoaded = true;
            }
        });

        printingTab.setOnSelectionChanged(e -> {
            if (printingTab.isSelected() && currentJob != null && !printingLoaded) {
                loadPrintingTab();
                printingLoaded = true;
            }
        });

        bindingTab.setOnSelectionChanged(e -> {
            if (bindingTab.isSelected() && currentJob != null && !bindingLoaded) {
                loadBindingTab();
                bindingLoaded = true;
            }
        });

        laminationTab.setOnSelectionChanged(e -> {
            if (laminationTab.isSelected() && currentJob != null && !laminationLoaded) {
                loadLaminationTab();
                laminationLoaded = true;
            }
        });

        ctpTab.setOnSelectionChanged(e -> {
            if (ctpTab.isSelected() && currentJob != null && !ctpLoaded) {
                loadCtpTab();
                ctpLoaded = true;
            }
        });
    }

    /* =====================================================
       OPEN JOB
       ===================================================== */
    public void openForEdit(Job job) {
        paperLoaded = false;
        printingLoaded = false;
        bindingLoaded = false;
        laminationLoaded = false;
        ctpLoaded = false;

        Job fresh = job != null && job.hasUuid()
                ? new JobService().getJobByUuid(job.getUuid())
                : null;
        this.currentJob = fresh != null ? fresh : job;
        if (this.currentJob == null) {
            return;
        }

        jobNumberLabel.setText("Job No. " + (currentJob.getJobCode() != null ? currentJob.getJobCode() : currentJob.getUuid()));
        jobTitleLabel.setText(
                currentJob.getJobTitle() == null || currentJob.getJobTitle().isBlank()
                        ? "Untitled Job"
                        : currentJob.getJobTitle()
        );

        // General Tab
        if (currentJob.getRemarks() != null) jobRemarksArea.setText(currentJob.getRemarks());
        if (currentJob.getImagePath() != null && !currentJob.getImagePath().isBlank()) {
            File f = new File(currentJob.getImagePath());
            if (f.exists()) {
                jobImagePreview.setImage(new Image(f.toURI().toString()));
                jobImagePreview.setVisible(true); jobImagePreview.setManaged(true);
                filePlaceholder.setVisible(false); filePlaceholder.setManaged(false);
                setUploadOverlayVisible(false);
                setFileMetaVisible(true, f.getName());
            }
        }
        if (currentJob.getImagePath() == null || currentJob.getImagePath().isBlank()) {
            setUploadOverlayVisible(true);
            setFileMetaVisible(false, null);
        }
        
        applyInvoicedStateOnGeneralTab();
        preloadAllItemTabs();
    }

    /** Item tabs load lazily on first select; preload so saved lines appear without an extra click. */
    private void preloadAllItemTabs() {
        if (currentJob == null || !currentJob.hasUuid()) {
            return;
        }
        try {
            if (!printingLoaded) {
                loadPrintingTab();
                printingLoaded = true;
            }
            if (!paperLoaded) {
                loadPaperTab();
                paperLoaded = true;
            }
            if (!bindingLoaded) {
                loadBindingTab();
                bindingLoaded = true;
            }
            if (!laminationLoaded) {
                loadLaminationTab();
                laminationLoaded = true;
            }
            if (!ctpLoaded) {
                loadCtpTab();
                ctpLoaded = true;
            }
            if (jobTabPane != null && !new JobItemService().getJobItems(currentJob.getUuid()).isEmpty()) {
                jobTabPane.getSelectionModel().select(printingTab);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load job item tabs", e);
        }
    }

    private void applyInvoicedStateOnGeneralTab() {
        if (currentJob == null) return;
        boolean isInvoicedStatus = "invoiced".equalsIgnoreCase(currentJob.getStatus());
        String invStatus = (currentJob.getInvoiceStatus() != null) ? currentJob.getInvoiceStatus().trim().toLowerCase() : "";
        boolean isLocked = isInvoicedStatus && !(invStatus.equals("draft") || invStatus.equals("final"));

        jobRemarksArea.setDisable(isLocked);
        uploadJobImageBtn.setDisable(isLocked);
    }

    /* =====================================================
       LOAD TABS
       ===================================================== */
    private void loadPaperTab() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/edit_job_paper.fxml"));
            Parent content = loader.load();
            paperTabController = loader.getController();
            paperTab.setContent(content);
            paperTabController.loadForJob(currentJob);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Paper tab", e);
        }
    }

    private void loadPrintingTab() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/edit_job_printing.fxml"));
            Parent content = loader.load();
            printingTabController = loader.getController();
            printingTab.setContent(content);
            printingTabController.loadForJob(currentJob);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Printing tab", e);
        }
    }

    private void loadBindingTab() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/edit_job_binding.fxml"));
            Parent content = loader.load();
            bindingTabController = loader.getController();
            bindingTab.setContent(content);
            bindingTabController.loadForJob(currentJob);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Binding tab", e);
        }
    }

    private void loadLaminationTab() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/edit_job_lamination.fxml"));
            Parent content = loader.load();
            laminationTabController = loader.getController();
            laminationTab.setContent(content);
            laminationTabController.loadForJob(currentJob);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lamination tab", e);
        }
    }

    private void loadCtpTab() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/edit_job_ctp.fxml"));
            Parent content = loader.load();
            ctpTabController = loader.getController();
            ctpTab.setContent(content);
            ctpTabController.loadForJob(currentJob);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load CTP tab", e);
        }
    }

    /* =====================================================
       SAVE
       ===================================================== */
    @FXML
    private void handleSaveChanges() {

        if (currentJob == null) return;

        boolean anythingSaved = false;
        Connection con = null;
        
        try  {
        	
        	con = utils.DBConnection.getConnection();
            con.setAutoCommit(false);
            JobItemService jobItemService = new JobItemService(con);

            if (paperTabController != null) {
                paperTabController.commitEditor();
                anythingSaved |= savePaperItems(con, jobItemService);
            }

            if (printingTabController != null) {
                printingTabController.commitEditor();
                anythingSaved |= savePrintingItems(con, jobItemService);
            }

            if (bindingTabController != null) {
                bindingTabController.commitEditor();
                anythingSaved |= saveBindingItems(con,jobItemService);
            }

            if (laminationTabController != null) {
                laminationTabController.commitEditor();
                anythingSaved |= saveLaminationItems(con, jobItemService);
            }

            if (ctpTabController != null) {
                ctpTabController.commitEditor();
                anythingSaved |= saveCtpItems(con,jobItemService);
            }

            // Save Job Remarks and Image
            JobService js = new JobService();
            if (jobRemarksArea.getText() != null && !jobRemarksArea.getText().equals(currentJob.getRemarks())) {
                String userUuid = null;
                if (utils.SessionManager.getInstance().getCurrentUser() != null) {
                    userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
                }
                String updateQuery = """
                        UPDATE jobs SET remarks = ?, sync_status = 'PENDING',
                        updated_at = datetime('now'), sync_version = sync_version + 1,
                        updated_by_user_uuid = ?
                        WHERE uuid = ?
                        """;
                try (java.sql.PreparedStatement ps = con.prepareStatement(updateQuery)) {
                    ps.setString(1, jobRemarksArea.getText());
                    ps.setString(2, userUuid);
                    ps.setString(3, currentJob.getUuid());
                    ps.executeUpdate();
                }
                currentJob.setRemarks(jobRemarksArea.getText());
                anythingSaved = true;
            }

            if (selectedImageFile != null) {
                File dir = new File("Images");
                if (!dir.exists()) dir.mkdirs();
                String ext = "";
                String name = selectedImageFile.getName();
                int dotIndex = name.lastIndexOf('.');
                if (dotIndex > 0) ext = name.substring(dotIndex);
                String newFileName = "job_" + currentJob.getUuid().replace("-", "") + "_" + System.currentTimeMillis() + ext;
                File targetFile = new File(dir, newFileName);
                Files.copy(selectedImageFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                String relativePath = "Images/" + newFileName;
                
                String userUuid = null;
                if (utils.SessionManager.getInstance().getCurrentUser() != null) {
                    userUuid = utils.SessionManager.getInstance().getCurrentUser().getUuid();
                }
                String updateImgQuery = """
                        UPDATE jobs SET image_path = ?, sync_status = 'PENDING',
                        updated_at = datetime('now'), sync_version = sync_version + 1,
                        updated_by_user_uuid = ?
                        WHERE uuid = ?
                        """;
                try (java.sql.PreparedStatement ps = con.prepareStatement(updateImgQuery)) {
                    ps.setString(1, relativePath);
                    ps.setString(2, userUuid);
                    ps.setString(3, currentJob.getUuid());
                    ps.executeUpdate();
                }
                currentJob.setImagePath(relativePath);
                anythingSaved = true;
            }

            con.commit();

            Stage stage = (Stage) jobNumberLabel.getScene().getWindow();
            MainController.getInstance().handleBack(null);

            Toast.show(
                    stage,
                    anythingSaved ? "Changes saved ✅" : "No changes detected"
            );

        } catch (Exception e) {
        	try {
        		con.rollback();
            } catch (Exception ignored) {}
            e.printStackTrace();
            Toast.show(
                (Stage) jobNumberLabel.getScene().getWindow(),
                "Save failed ❌"
            );
        }finally {

            if (con != null) {
                try {
                    con.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /* =====================================================
       SAVE HELPERS (FIXED)
       ===================================================== */

    private boolean savePaperItems(Connection con, JobItemService service) {

        boolean changed = false;
        PaperItemRepository repo = new PaperItemRepository();
        JobItemRepository jobItemRepo = new JobItemRepository();

        for (Paper p : paperTabController.getItems()) {
            if (p.isNew() && p.isDeleted()) continue;

            if (!p.isNew() && p.isDeleted()) {
                repo.deleteByJobItemUuid(con, p.getJobItemUuid());
                changed = true;
                continue;
            }

            if (p.isNew()) {
                service.addJobItem(currentJob.getUuid(), p);
                p.captureOriginal();
                p.resetFlags();
                changed = true;
                continue;
            }

            if (p.isUpdated()) {
                if (!p.isSameAsOriginal()) {
                    repo.update(con, p);
                    jobItemRepo.updateBaseItem(con, p.getJobItemUuid(), service.buildPaperDescription(p), p.getAmount());
                    changed = true;
                }
                p.captureOriginal();
                p.resetFlags();
            }
        }

        return changed;
    }


    private boolean savePrintingItems(Connection con, JobItemService service) {

        boolean changed = false;
        PrintingItemRepository repo = new PrintingItemRepository();
        JobItemRepository jobItemRepo = new JobItemRepository();

        for (Printing p : printingTabController.getItems()) {
            if (p.isNew() && p.isDeleted()) continue;

            if (!p.isNew() && p.isDeleted()) {
                repo.deleteByJobItemUuid(con, p.getJobItemUuid());
                changed = true;
                continue;
            }

            if (p.isNew()) {
                service.addJobItem(currentJob.getUuid(), p);
                p.captureOriginal();
                p.resetFlags();
                changed = true;
                continue;
            }

            if (p.isUpdated()) {
                if (!p.isSameAsOriginal()) {
                    repo.update(con, p);
                    jobItemRepo.updateBaseItem(con, p.getJobItemUuid(), service.buildPrintingDescription(p), p.getAmount());
                    changed = true;
                }
                p.captureOriginal();
                p.resetFlags();
            }
        }

        return changed;
    }

    private boolean saveBindingItems(Connection con, JobItemService service) {

        boolean changed = false;
        BindingItemRepository repo = new BindingItemRepository();
        JobItemRepository jobItemRepo = new JobItemRepository();

        for (Binding b : bindingTabController.getItems()) {
            if (b.isNew() && b.isDeleted()) continue;

            if (!b.isNew() && b.isDeleted()) {
                repo.deleteByJobItemUuid(con, b.getJobItemUuid());
                changed = true;
                continue;
            }

            if (b.isNew()) {
                service.addJobItem(currentJob.getUuid(), b);
                b.captureOriginal();
                b.resetFlags();
                changed = true;
                continue;
            }

            if (b.isUpdated()) {
                if (!b.isSameAsOriginal()) {
                    repo.update(con, b);
                    jobItemRepo.updateBaseItem(con, b.getJobItemUuid(), service.buildBindingDescription(b), b.getAmount());
                    changed = true;
                }
                b.captureOriginal();
                b.resetFlags();
            }
        }

        return changed;
    }

    private boolean saveLaminationItems(Connection con, JobItemService service) {

        boolean changed = false;
        LaminationItemRepository repo = new LaminationItemRepository();
        JobItemRepository jobItemRepo = new JobItemRepository();

        for (Lamination l : laminationTabController.getItems()) {
            if (l.isNew() && l.isDeleted()) continue;

            if (!l.isNew() && l.isDeleted()) {
                repo.deleteByJobItemUuid(con, l.getJobItemUuid());
                changed = true;
                continue;
            }

            if (l.isNew()) {
                service.addJobItem(currentJob.getUuid(), l);
                l.captureOriginal();
                l.resetFlags();
                changed = true;
                continue;
            }

            if (l.isUpdated()) {
                if (!l.isSameAsOriginal()) {
                    repo.update(con, l);
                    jobItemRepo.updateBaseItem(con, l.getJobItemUuid(), service.buildLaminationDescription(l), l.getAmount());
                    changed = true;
                }
                l.captureOriginal();
                l.resetFlags();
            }
        }

        return changed;
    }

    private boolean saveCtpItems(Connection con, JobItemService service) {

        boolean changed = false;
        CtpItemRepository repo = new CtpItemRepository();
        JobItemRepository jobItemRepo = new JobItemRepository();

        for (CtpPlate c : ctpTabController.getItems()) {
            if (c.isNew() && c.isDeleted()) continue;

            if (!c.isNew() && c.isDeleted()) {
                repo.deleteByJobItemUuid(con, c.getJobItemUuid());
                changed = true;
                continue;
            }

            if (c.isNew()) {
                service.addJobItem(currentJob.getUuid(), c);
                c.captureOriginal();
                c.resetFlags();
                changed = true;
                continue;
            }

            if (c.isUpdated()) {
                if (!c.isSameAsOriginal()) {
                    repo.update(con, c);
                    jobItemRepo.updateBaseItem(con, c.getJobItemUuid(), service.buildCtpDescription(c), c.getAmount());
                    changed = true;
                }
                c.captureOriginal();
                c.resetFlags();
            }
        }

        return changed;
    }

    /* =====================================================
       DISCARD
       ===================================================== */
    @FXML
    private void handleDiscardChanges() {
        if (currentJob == null) return;
        MainController.getInstance().handleBack(null);
    }

    @FXML
    private void handleUploadFile() {
        try {
            FileChooser chooser = new FileChooser();
            utils.UniversalDownloadPath.prepareFileChooser(chooser);
            chooser.setTitle("Select Image / PDF");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images / PDF", "*.png", "*.jpg", "*.jpeg", "*.pdf"),
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
                    new FileChooser.ExtensionFilter("PDF", "*.pdf")
            );

            File file = chooser.showOpenDialog(null);
            if (file == null) return;
            this.selectedImageFile = file;

            boolean isPdf = file.getName().toLowerCase().endsWith(".pdf");
            if (!isPdf) {
                Image img = new Image(file.toURI().toString());
                jobImagePreview.setImage(img);
                jobImagePreview.setVisible(true);
                jobImagePreview.setManaged(true);
            } else {
                jobImagePreview.setImage(null);
                jobImagePreview.setVisible(false);
                jobImagePreview.setManaged(false);
            }
            filePlaceholder.setVisible(false);
            filePlaceholder.setManaged(false);
            setUploadOverlayVisible(false);
            setFileMetaVisible(true, file.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleRemoveFile() {
        selectedImageFile = null;
        if (jobImagePreview != null) {
            jobImagePreview.setImage(null);
            jobImagePreview.setVisible(false);
            jobImagePreview.setManaged(false);
        }
        setUploadOverlayVisible(true);
        setFileMetaVisible(false, null);
    }

    private void setUploadOverlayVisible(boolean visible) {
        if (previewContainer == null) return;
        Node overlay = previewContainer.lookup(".upload-overlay");
        if (overlay != null) {
            overlay.setVisible(visible);
            overlay.setManaged(visible);
        }
    }

    private void setFileMetaVisible(boolean visible, String fileName) {
        Node bar = previewContainer != null ? previewContainer.getParent().lookup(".file-meta-bar") : null;
        if (bar != null) {
            bar.setVisible(visible);
            bar.setManaged(visible);
        }
        if (fileName != null && previewContainer != null) {
            Node nameNode = previewContainer.getParent().lookup(".file-meta-name");
            if (nameNode instanceof Label lbl) {
                lbl.setText(fileName);
            }
        }
    }
}
