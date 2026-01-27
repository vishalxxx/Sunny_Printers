package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import model.Job;

public class EditJobController {

    /* =====================================================
       CURRENT JOB STATE
       ===================================================== */
    private Job currentJob;

    /* =====================================================
       TABS (Injected from edit_job.fxml)
       ===================================================== */
    @FXML private Tab paperTab;
    @FXML private Tab printingTab;
    @FXML private Tab ctpTab;
    @FXML private Tab bindingTab;
    @FXML private Tab laminationTab;
    @FXML
    private Label jobNumberLabel;

    @FXML
    private Label jobTitleLabel;

    /* =====================================================
       TAB CONTROLLERS
       ===================================================== */
    private PaperTabController paperTabController;
    private PrintingTabController printingTabController;
    // later:
    // private CtpTabController ctpTabController;
    // private BindingTabController bindingTabController;
    // private LaminationTabController laminationTabController;

    /* =====================================================
       LOAD FLAGS (ensure one-time UI load)
       ===================================================== */
    private boolean paperLoaded = false;
    private boolean printingLoaded = false;
    private boolean bindingLoaded = false;
    private boolean laminationLoaded = false;

    /* =====================================================
       INITIALIZE (ONLY UI WIRING)
       ===================================================== */
    @FXML
    private void initialize() {

    	

        
        
        /* ================= PAPER TAB ================= */
        paperTab.setOnSelectionChanged(e -> {
            if (!paperTab.isSelected() || currentJob == null) return;

            if (!paperLoaded) {
                loadPaperTab();
                paperLoaded = true;
            } else {
                paperTabController.loadForJob(currentJob);
            }
        });

        /* ================= PRINTING TAB ================= */
        printingTab.setOnSelectionChanged(e -> {
            if (printingTab.isSelected() && currentJob != null) {

                if (!printingLoaded) {
                    loadPrintingTab();
                    printingLoaded = true;
                } else {
                    printingTabController.loadForJob(currentJob);
                }
            }
        });
        
        /* ================= Lamination TAB ================= */

        laminationTab.setOnSelectionChanged(e -> {
            if (laminationTab.isSelected() && currentJob != null) {
                if (!laminationLoaded) {
                    loadLaminationTab();
                    laminationLoaded = true;
                } else {
                    laminationTabController.loadForJob(currentJob);
                }
            }
        });
        
        /* ================= Binding TAB ================= */

        bindingTab.setOnSelectionChanged(e -> {
            if (bindingTab.isSelected() && currentJob != null) {
                if (!bindingLoaded) {
                    loadBindingTab();
                    bindingLoaded = true;
                } else {
                    bindingTabController.loadForJob(currentJob);
                }
            }
        });

        /* ================= CTPPlate TAB ================= */

        
        
        ctpTab.setOnSelectionChanged(e -> {
            if (ctpTab.isSelected() && currentJob != null) {
                if (!ctpLoaded) {
                    loadCtpTab();
                    ctpLoaded = true;
                } else {
                    ctpTabController.loadForJob(currentJob);
                }
            }
        });




        /* ====== (ENABLE LATER USING SAME PATTERN) ======
        ctpTab.setOnSelectionChanged(...)
        bindingTab.setOnSelectionChanged(...)
        laminationTab.setOnSelectionChanged(...)
        */
    }

    /* =====================================================
       ENTRY POINT FROM VIEW JOBS
       ===================================================== */
    public void openForEdit(Job job) {
        this.currentJob = job;
        // Header
        jobNumberLabel.setText("Edit Job #" + job.getId());
        jobTitleLabel.setText(job.getJobTitle()); // or job.getJobName()
        System.out.println("✏ Editing Job ID: " + job.getId());

        // Handle case where Paper tab is already selected
        if (paperTab.isSelected()) {
            if (!paperLoaded) {
                loadPaperTab();
                paperLoaded = true;
            }
            paperTabController.loadForJob(job);
        }
    }

    /* =====================================================
       LOAD PAPER TAB
       ===================================================== */
    private void loadPaperTab() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/edit_job_paper.fxml")
            );

            Parent content = loader.load();
            paperTabController = loader.getController();

            paperTab.setContent(content);

            paperTabController.loadForJob(currentJob);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load Paper tab", e);
        }
    }

    /* =====================================================
       LOAD PRINTING TAB
       ===================================================== */

    private void loadPrintingTab() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/edit_job_printing.fxml")
            );

            Parent content = loader.load();
            printingTabController = loader.getController();

            printingTab.setContent(content);

            if (currentJob != null) {
                printingTabController.loadForJob(currentJob);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load Printing tab", e);
        }
    }


    /* =====================================================
       FUTURE TABS (STUBS)
       ===================================================== */

    /*
    private void loadCtpTab() { }
    private void loadBindingTab() { }
    private void loadLaminationTab() { }
    */
    
    
    

    private LaminationTabController laminationTabController;

    private void loadLaminationTab() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/edit_job_lamination.fxml")
            );

            Parent content = loader.load();
            laminationTabController = loader.getController();

            laminationTab.setContent(content);
            laminationTabController.loadForJob(currentJob);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lamination tab", e);
        }
    }
    
    private BindingTabController bindingTabController;
    private void loadBindingTab() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/edit_job_binding.fxml")
            );
            Parent content = loader.load();
            bindingTabController = loader.getController();
            bindingTab.setContent(content);
            bindingTabController.loadForJob(currentJob);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load Binding tab", e);
        }
    }
    
    private boolean ctpLoaded = false;
    private CtpTabController ctpTabController;

    private void loadCtpTab() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/edit_job_ctp.fxml")
            );
            Parent content = loader.load();
            ctpTabController = loader.getController();
            ctpTab.setContent(content);
            ctpTabController.loadForJob(currentJob);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load CTP tab", e);
        }
    }



}
