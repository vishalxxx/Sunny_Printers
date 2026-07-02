package sunnyprinters;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReleaseManager extends Application {

    private String currentVersion = "1.0.0";
    private Process activeProcess = null;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // UI Controls
    private Label lblCurrentVersion;
    private ComboBox<String> cmbIncrement;
    private TextField txtManualVersion;
    private CheckBox chkMandatory;
    
    private Label lblBuildStatus;
    private Label lblPackageStatus;
    private Label lblUploadStatus;
    private Label lblVerifyStatus;
    
    private Circle cBuild;
    private Circle cPackage;
    private Circle cUpload;
    private Circle cVerify;

    private ProgressBar progressBar;
    private Label lblProgressText;
    private TextArea txtLogs;

    private Button btnRelease;
    private Button btnBuild;
    private Button btnPackage;
    private Button btnUpload;
    private Button btnVerify;
    private Button btnCancel;
    private Button btnOpenFolder;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        loadCurrentVersion();

        primaryStage.setTitle("Sunny Printers Release Manager");
        
        // Sleek Dark Theme Layout
        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: #1a1a1a; -fx-font-family: 'Inter', sans-serif;");

        // Header Panel
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Sunny Printers Release Manager");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 22px; -fx-font-weight: bold;");
        header.getChildren().add(title);

        // Settings / Configuration Panel (Card Grid style)
        GridPane settingsGrid = new GridPane();
        settingsGrid.setHgap(20);
        settingsGrid.setVgap(15);
        settingsGrid.setPadding(new Insets(15));
        settingsGrid.setStyle("-fx-background-color: #2b2b2b; -fx-background-radius: 8px; -fx-border-color: #404040; -fx-border-radius: 8px;");

        Label lblCur = new Label("Current Version:");
        lblCur.setStyle("-fx-text-fill: #b3b3b3; -fx-font-weight: bold;");
        lblCurrentVersion = new Label(currentVersion);
        lblCurrentVersion.setStyle("-fx-text-fill: #42f59b; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label lblInc = new Label("Increment Mode:");
        lblInc.setStyle("-fx-text-fill: #b3b3b3;");
        cmbIncrement = new ComboBox<>();
        cmbIncrement.getItems().addAll("patch", "minor", "major", "none");
        cmbIncrement.setValue("patch");
        cmbIncrement.setStyle("-fx-background-color: #3d3d3d; -fx-text-fill: white; -fx-control-inner-background: #3d3d3d;");

        Label lblMan = new Label("Manual Override:");
        lblMan.setStyle("-fx-text-fill: #b3b3b3;");
        txtManualVersion = new TextField();
        txtManualVersion.setPromptText("e.g. 1.0.2");
        txtManualVersion.setStyle("-fx-background-color: #3d3d3d; -fx-text-fill: white; -fx-border-color: #555; -fx-border-radius: 4px;");

        chkMandatory = new CheckBox("Mark Update as Mandatory");
        chkMandatory.setStyle("-fx-text-fill: #b3b3b3;");

        settingsGrid.add(lblCur, 0, 0);
        settingsGrid.add(lblCurrentVersion, 1, 0);
        settingsGrid.add(lblInc, 0, 1);
        settingsGrid.add(cmbIncrement, 1, 1);
        settingsGrid.add(lblMan, 0, 2);
        settingsGrid.add(txtManualVersion, 1, 2);
        settingsGrid.add(chkMandatory, 1, 3);

        // Pipeline Status Grid
        HBox statusBox = new HBox(30);
        statusBox.setPadding(new Insets(15));
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setStyle("-fx-background-color: #2b2b2b; -fx-background-radius: 8px; -fx-border-color: #404040; -fx-border-radius: 8px;");

        cBuild = createStatusDot();
        lblBuildStatus = new Label("Build: Pending");
        lblBuildStatus.setStyle("-fx-text-fill: #e0e0e0;");

        cPackage = createStatusDot();
        lblPackageStatus = new Label("Package: Pending");
        lblPackageStatus.setStyle("-fx-text-fill: #e0e0e0;");

        cUpload = createStatusDot();
        lblUploadStatus = new Label("Upload: Pending");
        lblUploadStatus.setStyle("-fx-text-fill: #e0e0e0;");

        cVerify = createStatusDot();
        lblVerifyStatus = new Label("Verify: Pending");
        lblVerifyStatus.setStyle("-fx-text-fill: #e0e0e0;");

        statusBox.getChildren().addAll(
                new HBox(8, cBuild, lblBuildStatus),
                new HBox(8, cPackage, lblPackageStatus),
                new HBox(8, cUpload, lblUploadStatus),
                new HBox(8, cVerify, lblVerifyStatus)
        );

        // Progress Bar
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setStyle("-fx-accent: #007acc;");
        lblProgressText = new Label("Ready to release.");
        lblProgressText.setStyle("-fx-text-fill: #b3b3b3;");

        // Log Console Area
        txtLogs = new TextArea();
        txtLogs.setEditable(false);
        txtLogs.setWrapText(true);
        txtLogs.setPrefHeight(250);
        txtLogs.setStyle("-fx-control-inner-background: #0d0d0d; -fx-text-fill: #39ff14; -fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");

        // Controls/Action Buttons Panel
        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_LEFT);

        btnRelease = new Button("Run Full Release");
        btnRelease.setStyle("-fx-background-color: #007acc; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4px;");
        btnRelease.setOnAction(e -> startFullRelease());

        btnBuild = new Button("Test & Compile");
        btnBuild.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: white; -fx-background-radius: 4px;");
        btnBuild.setOnAction(e -> runPhase("Build Only", "mvn clean compile"));

        btnPackage = new Button("Package (jpackage)");
        btnPackage.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: white; -fx-background-radius: 4px;");
        btnPackage.setOnAction(e -> runPackageOnly());

        btnUpload = new Button("Upload");
        btnUpload.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: white; -fx-background-radius: 4px;");
        btnUpload.setOnAction(e -> runUploadOnly());

        btnVerify = new Button("Verify");
        btnVerify.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: white; -fx-background-radius: 4px;");
        btnVerify.setOnAction(e -> runVerifyOnly());

        btnCancel = new Button("Cancel Process");
        btnCancel.setDisable(true);
        btnCancel.setStyle("-fx-background-color: #e81123; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4px;");
        btnCancel.setOnAction(e -> cancelProcess());

        btnOpenFolder = new Button("Open Release Folder");
        btnOpenFolder.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: #e0e0e0; -fx-border-color: #555; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        btnOpenFolder.setOnAction(e -> openReleaseFolder());

        buttonBar.getChildren().addAll(btnRelease, btnBuild, btnPackage, btnUpload, btnVerify, btnCancel, btnOpenFolder);

        root.getChildren().addAll(header, settingsGrid, statusBox, progressBar, lblProgressText, txtLogs, buttonBar);

        Scene scene = new Scene(root, 960, 680);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private Circle createStatusDot() {
        Circle dot = new Circle(6);
        dot.setFill(Color.GRAY);
        return dot;
    }

    private void loadCurrentVersion() {
        Properties props = new Properties();
        Path propFile = Paths.get("src/main/resources/version.properties");
        if (Files.exists(propFile)) {
            try (InputStream in = Files.newInputStream(propFile)) {
                props.load(in);
                String ver = props.getProperty("version");
                if (ver != null) {
                    currentVersion = ver.trim();
                }
            } catch (IOException ignored) {}
        }
    }

    private void updateDots(String build, String pack, String upload, String verify) {
        Platform.runLater(() -> {
            updateDotColor(cBuild, lblBuildStatus, "Build", build);
            updateDotColor(cPackage, lblPackageStatus, "Package", pack);
            updateDotColor(cUpload, lblUploadStatus, "Upload", upload);
            updateDotColor(cVerify, lblVerifyStatus, "Verify", verify);
        });
    }

    private void updateDotColor(Circle dot, Label label, String prefix, String status) {
        if ("Success".equalsIgnoreCase(status)) {
            dot.setFill(Color.web("#42f59b"));
            label.setText(prefix + ": Success");
        } else if ("Running".equalsIgnoreCase(status)) {
            dot.setFill(Color.web("#ffcc00"));
            label.setText(prefix + ": Running");
        } else if ("Failed".equalsIgnoreCase(status)) {
            dot.setFill(Color.web("#ff3333"));
            label.setText(prefix + ": Failed");
        } else {
            dot.setFill(Color.GRAY);
            label.setText(prefix + ": Pending");
        }
    }

    private void logText(String text) {
        Platform.runLater(() -> {
            txtLogs.appendText(text + "\n");
        });
    }

    private void setUIBusy(boolean busy) {
        Platform.runLater(() -> {
            btnRelease.setDisable(busy);
            btnBuild.setDisable(busy);
            btnPackage.setDisable(busy);
            btnUpload.setDisable(busy);
            btnVerify.setDisable(busy);
            btnCancel.setDisable(!busy);
            cmbIncrement.setDisable(busy);
            txtManualVersion.setDisable(busy);
            chkMandatory.setDisable(busy);
        });
    }

    private void startFullRelease() {
        setUIBusy(true);
        txtLogs.clear();
        progressBar.setProgress(-1);
        lblProgressText.setText("Initializing Release Pipeline...");
        
        String incMode = cmbIncrement.getValue();
        String manVer = txtManualVersion.getText().trim();
        boolean mandatory = chkMandatory.isSelected();

        updateDots("Pending", "Pending", "Pending", "Pending");

        executorService.submit(() -> {
            try {
                // Determine release version first
                logText("Resolving release version...");
                String verArgs = "-Increment " + incMode;
                if (!manVer.isEmpty()) {
                    verArgs += " -ManualVersion " + manVer;
                }
                
                String resolvedVersion = runScriptProcess("version.ps1", verArgs).trim();
                logText("Target Release Version resolved: " + resolvedVersion);
                
                Platform.runLater(() -> {
                    lblProgressText.setText("Running Build and Test suite...");
                    progressBar.setProgress(0.2);
                });

                // Phase 1: Build & Test
                updateDots("Running", "Pending", "Pending", "Pending");
                logText("Starting Maven Test and Build...");
                runProcess("mvn clean test", false);
                updateDots("Success", "Pending", "Pending", "Pending");

                Platform.runLater(() -> {
                    lblProgressText.setText("Packaging application (App Image, MSI, ZIP)...");
                    progressBar.setProgress(0.5);
                });

                // Phase 2: Package
                updateDots("Success", "Running", "Pending", "Pending");
                logText("Starting Packaging Script...");
                runScriptProcess("package.ps1", "-Version " + resolvedVersion);
                updateDots("Success", "Success", "Pending", "Pending");

                Platform.runLater(() -> {
                    lblProgressText.setText("Publishing updates to Supabase...");
                    progressBar.setProgress(0.7);
                });

                // Phase 3: Hash, Notes & Upload
                updateDots("Success", "Success", "Running", "Pending");
                logText("Generating release notes, calculating hashes & uploading to Supabase...");
                
                // Invoke release.ps1 specifically with Upload/Verify stages
                String releaseCmd = "-Increment " + incMode + " -SkipTests -Publish";
                if (!manVer.isEmpty()) {
                    releaseCmd += " -ManualVersion " + manVer;
                }
                if (mandatory) {
                    releaseCmd += " -Mandatory";
                }
                
                runScriptProcess("release.ps1", releaseCmd);
                updateDots("Success", "Success", "Success", "Success");

                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    lblProgressText.setText("Release successfully published!");
                    loadCurrentVersion();
                    lblCurrentVersion.setText(currentVersion);
                });
                
                logText("Enterprise release completed successfully!");

            } catch (Exception ex) {
                logText("\n[ERROR] Pipeline aborted: " + ex.getMessage());
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    lblProgressText.setText("Release failed.");
                });
                
                // Identify where it failed to color dot red
                if (lblBuildStatus.getText().contains("Running")) {
                    updateDots("Failed", "Pending", "Pending", "Pending");
                } else if (lblPackageStatus.getText().contains("Running")) {
                    updateDots("Success", "Failed", "Pending", "Pending");
                } else if (lblUploadStatus.getText().contains("Running")) {
                    updateDots("Success", "Success", "Failed", "Pending");
                } else if (lblVerifyStatus.getText().contains("Running")) {
                    updateDots("Success", "Success", "Success", "Failed");
                }
            } finally {
                setUIBusy(false);
            }
        });
    }

    private void runPhase(String phaseName, String command) {
        setUIBusy(true);
        txtLogs.clear();
        progressBar.setProgress(-1);
        lblProgressText.setText("Executing: " + phaseName);
        
        executorService.submit(() -> {
            try {
                runProcess(command, true);
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    lblProgressText.setText(phaseName + " finished successfully.");
                });
            } catch (Exception ex) {
                logText("\n[ERROR] " + phaseName + " failed: " + ex.getMessage());
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    lblProgressText.setText(phaseName + " failed.");
                });
            } finally {
                setUIBusy(false);
            }
        });
    }

    private void runPackageOnly() {
        setUIBusy(true);
        txtLogs.clear();
        progressBar.setProgress(-1);
        lblProgressText.setText("Packaging application...");
        
        executorService.submit(() -> {
            try {
                loadCurrentVersion();
                runScriptProcess("package.ps1", "-Version " + currentVersion);
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    lblProgressText.setText("Packaging finished successfully.");
                });
            } catch (Exception ex) {
                logText("\n[ERROR] Packaging failed: " + ex.getMessage());
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    lblProgressText.setText("Packaging failed.");
                });
            } finally {
                setUIBusy(false);
            }
        });
    }

    private void runUploadOnly() {
        setUIBusy(true);
        txtLogs.clear();
        progressBar.setProgress(-1);
        lblProgressText.setText("Uploading to Supabase...");
        
        executorService.submit(() -> {
            try {
                loadCurrentVersion();
                String msi = "target/dist/Sunny Printers ERP-" + currentVersion + ".msi";
                String zip = "target/dist/SunnyPrintersERP-" + currentVersion + ".zip";
                
                runScriptProcess("upload.ps1", "-Version " + currentVersion + " -MsiPath \"" + msi + "\" -ZipPath \"" + zip + "\"");
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    lblProgressText.setText("Upload finished successfully.");
                });
            } catch (Exception ex) {
                logText("\n[ERROR] Upload failed: " + ex.getMessage());
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    lblProgressText.setText("Upload failed.");
                });
            } finally {
                setUIBusy(false);
            }
        });
    }

    private void runVerifyOnly() {
        setUIBusy(true);
        txtLogs.clear();
        progressBar.setProgress(-1);
        lblProgressText.setText("Verifying upload...");
        
        executorService.submit(() -> {
            try {
                loadCurrentVersion();
                String msi = "target/dist/Sunny Printers ERP-" + currentVersion + ".msi";
                String zip = "target/dist/SunnyPrintersERP-" + currentVersion + ".zip";
                
                runScriptProcess("verify.ps1", "-Version " + currentVersion + " -MsiPath \"" + msi + "\" -ZipPath \"" + zip + "\"");
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    lblProgressText.setText("Verification PASSED.");
                });
            } catch (Exception ex) {
                logText("\n[ERROR] Verification failed: " + ex.getMessage());
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    lblProgressText.setText("Verification FAILED.");
                });
            } finally {
                setUIBusy(false);
            }
        });
    }

    private void cancelProcess() {
        if (activeProcess != null) {
            logText("\n[INFO] Cancelling active process...");
            activeProcess.destroyForcibly();
            activeProcess = null;
        }
    }

    private void openReleaseFolder() {
        try {
            loadCurrentVersion();
            Path releasePath = Paths.get("Release", currentVersion).toAbsolutePath();
            if (Files.exists(releasePath)) {
                new ProcessBuilder("explorer.exe", releasePath.toString()).start();
            } else {
                new ProcessBuilder("explorer.exe", Paths.get("Release").toAbsolutePath().toString()).start();
            }
        } catch (Exception ex) {
            logText("Failed to open release folder explorer: " + ex.getMessage());
        }
    }

    private String runScriptProcess(String scriptName, String args) throws Exception {
        String scriptPath = Paths.get("scripts", scriptName).toAbsolutePath().toString();
        String cmd = "powershell.exe -ExecutionPolicy Bypass -File \"" + scriptPath + "\" " + args;
        return runProcess(cmd, true);
    }

    private String runProcess(String fullCommand, boolean logOutput) throws Exception {
        ProcessBuilder builder;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            builder = new ProcessBuilder("cmd.exe", "/c", fullCommand);
        } else {
            builder = new ProcessBuilder("sh", "-c", fullCommand);
        }
        builder.redirectErrorStream(true);
        
        activeProcess = builder.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (logOutput) {
                    logText(line);
                }
            }
        }
        
        int exitCode = activeProcess.waitFor();
        activeProcess = null;
        
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode);
        }
        
        return output.toString();
    }
}
