package controller;

import java.sql.Connection;
import java.util.List;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.stage.Stage;

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

public class EditJobController {

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

    /* =====================================================
       TABS
       ===================================================== */
    @FXML private Tab paperTab;
    @FXML private Tab printingTab;
    @FXML private Tab ctpTab;
    @FXML private Tab bindingTab;
    @FXML private Tab laminationTab;

    @FXML private Label jobNumberLabel;
    @FXML private Label jobTitleLabel;

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
        this.currentJob = job;

        jobNumberLabel.setText("Edit Job #" + job.getId());
        jobTitleLabel.setText(
                job.getJobTitle() == null || job.getJobTitle().isBlank()
                        ? "Untitled Job"
                        : job.getJobTitle()
        );

        // General Tab
        if (job.getRemarks() != null) jobRemarksArea.setText(job.getRemarks());
        if (job.getImagePath() != null && !job.getImagePath().isBlank()) {
            File f = new File(job.getImagePath());
            if (f.exists()) {
                jobImagePreview.setImage(new Image(f.toURI().toString()));
                jobImagePreview.setVisible(true); jobImagePreview.setManaged(true);
                filePlaceholder.setVisible(false); filePlaceholder.setManaged(false);
            }
        }
        
        applyInvoicedStateOnGeneralTab();

        if (paperTab.isSelected() && !paperLoaded) {
            loadPaperTab();
            paperLoaded = true;
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
                String updateQuery = "UPDATE jobs SET remarks = ? WHERE id = ?";
                try (java.sql.PreparedStatement ps = con.prepareStatement(updateQuery)) {
                    ps.setString(1, jobRemarksArea.getText());
                    ps.setInt(2, currentJob.getId());
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
                String newFileName = "job_" + currentJob.getId() + "_" + System.currentTimeMillis() + ext;
                File targetFile = new File(dir, newFileName);
                Files.copy(selectedImageFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                String relativePath = "Images/" + newFileName;
                
                String updateImgQuery = "UPDATE jobs SET image_path = ? WHERE id = ?";
                try (java.sql.PreparedStatement ps = con.prepareStatement(updateImgQuery)) {
                    ps.setString(1, relativePath);
                    ps.setInt(2, currentJob.getId());
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

            // 1️⃣ New → Deleted before save → ignore completely
            if (p.isNew() && p.isDeleted()) {
                continue;
            }

            // 2️⃣ Deleted existing item
            if (!p.isNew() && p.isDeleted()) {
                repo.deleteByJobItemId(con, p.getJobItemId());
                changed = true;
                continue;
            }

            // 3️⃣ Insert new item
            if (p.isNew()) {
                service.addJobItem(currentJob.getId(), p);
                p.captureOriginal();   // 🔥 NEW
                p.resetFlags();
                changed = true;
                continue;
            }

            // 4️⃣ Update existing item only if actually changed
            if (p.isUpdated()) {
                if (!p.isSameAsOriginal()) {
                    repo.update(con, p);
                    jobItemRepo.updateBaseItem(con, p.getJobItemId(), service.buildPaperDescription(p), p.getAmount());
                    changed = true;
                }
                p.captureOriginal();   // 🔥 NEW
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

            // 1️⃣ New → Deleted before save → ignore completely
            if (p.isNew() && p.isDeleted()) {
                continue;
            }

            // 2️⃣ Deleted existing item
            if (!p.isNew() && p.isDeleted()) {
                repo.deleteByJobItemId(con, p.getJobItemId());
                changed = true;
                continue;
            }

            // 3️⃣ Insert new item
            if (p.isNew()) {
                service.addJobItem(currentJob.getId(), p);
                p.captureOriginal();   // 🔥 IMPORTANT
                p.resetFlags();
                changed = true;
                continue;
            }

            // 4️⃣ Update existing item only if actually changed
            if (p.isUpdated()) {
                if (!p.isSameAsOriginal()) {
                    repo.update(con, p);
                    jobItemRepo.updateBaseItem(con, p.getJobItemId(), service.buildPrintingDescription(p), p.getAmount());
                    changed = true;
                }
                p.captureOriginal();   // 🔥 IMPORTANT
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

            // 1️⃣ New → Deleted before save → ignore completely
            if (b.isNew() && b.isDeleted()) {
                continue;
            }

            // 2️⃣ Deleted existing item
            if (!b.isNew() && b.isDeleted()) {
                repo.deleteByJobItemId(con, b.getJobItemId());
                changed = true;
                continue;
            }

            // 3️⃣ Insert new item
            if (b.isNew()) {
                service.addJobItem(currentJob.getId(), b);
                b.captureOriginal();   // 🔥 VERY IMPORTANT
                b.resetFlags();
                changed = true;
                continue;
            }

            // 4️⃣ Update existing item only if real change
            if (b.isUpdated()) {
                if (!b.isSameAsOriginal()) {
                    repo.update(con, b);
                    jobItemRepo.updateBaseItem(con, b.getJobItemId(), service.buildBindingDescription(b), b.getAmount());
                    changed = true;
                }
                b.captureOriginal();   // 🔥 VERY IMPORTANT
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

            // 1️⃣ New + Deleted before save → ignore completely
            if (l.isNew() && l.isDeleted()) {
                continue;
            }

            // 2️⃣ Deleted existing record
            if (!l.isNew() && l.isDeleted()) {
                repo.deleteByJobItemId(con, l.getJobItemId());
                changed = true;
                continue;
            }

            // 3️⃣ Insert new record
            if (l.isNew()) {
                service.addJobItem(currentJob.getId(), l);
                l.captureOriginal();   // 🔥 baseline reset
                l.resetFlags();
                changed = true;
                continue;
            }

            // 4️⃣ Update existing record only if really changed
            if (l.isUpdated()) {
                if (!l.isSameAsOriginal()) {
                    repo.update(con, l);
                    jobItemRepo.updateBaseItem(con, l.getJobItemId(), service.buildLaminationDescription(l), l.getAmount());
                    changed = true;
                }
                l.captureOriginal();   // 🔥 baseline reset
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

            // 1️⃣ New + Deleted before save → ignore completely
            if (c.isNew() && c.isDeleted()) {
                continue;
            }

            // 2️⃣ Deleted existing record
            if (!c.isNew() && c.isDeleted()) {
                repo.deleteByJobItemId(con, c.getJobItemId());
                changed = true;
                continue;
            }

            // 3️⃣ Insert new record
            if (c.isNew()) {
                service.addJobItem(currentJob.getId(), c);
                c.captureOriginal();   // 🔥 reset baseline
                c.resetFlags();
                changed = true;
                continue;
            }

            // 4️⃣ Update existing record only if actually changed
            if (c.isUpdated()) {
                if (!c.isSameAsOriginal()) {
                    repo.update(con, c);
                    jobItemRepo.updateBaseItem(con, c.getJobItemId(), service.buildCtpDescription(c), c.getAmount());
                    changed = true;
                }
                c.captureOriginal();   // 🔥 reset baseline
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
            chooser.setTitle("Select Image");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
            );

            File file = chooser.showOpenDialog(null);
            if (file == null) return;
            this.selectedImageFile = file;

            Image img = new Image(file.toURI().toString());
            jobImagePreview.setImage(img);
            jobImagePreview.setVisible(true);
            jobImagePreview.setManaged(true);
            filePlaceholder.setVisible(false);
            filePlaceholder.setManaged(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
