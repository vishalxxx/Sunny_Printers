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

public class EditJobController {

    /* =====================================================
       CURRENT JOB
       ===================================================== */
    private Job currentJob;

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

        if (paperTab.isSelected() && !paperLoaded) {
            loadPaperTab();
            paperLoaded = true;
        }
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

            if (paperTabController != null)
                anythingSaved |= savePaperItems(con, jobItemService);

            if (printingTabController != null)
                anythingSaved |= savePrintingItems(con, jobItemService);

            if (bindingTabController != null)
                anythingSaved |= saveBindingItems(con,jobItemService);

            if (laminationTabController != null)
                anythingSaved |= saveLaminationItems(con, jobItemService);

            if (ctpTabController != null)
                anythingSaved |= saveCtpItems(con,jobItemService);

            con.commit();

            reloadAllTabs();

            Toast.show(
                    (Stage) jobNumberLabel.getScene().getWindow(),
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

        if (paperTabController != null) paperTabController.loadForJob(currentJob);
        if (printingTabController != null) printingTabController.loadForJob(currentJob);
        if (bindingTabController != null) bindingTabController.loadForJob(currentJob);
        if (laminationTabController != null) laminationTabController.loadForJob(currentJob);
        if (ctpTabController != null) ctpTabController.loadForJob(currentJob);

        Toast.show(
                (Stage) jobNumberLabel.getScene().getWindow(),
                "Unsaved changes discarded ↩"
        );
    }

    private void reloadAllTabs() {
        handleDiscardChanges();
    }
}
