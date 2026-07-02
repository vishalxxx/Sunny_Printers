# release.ps1 - Master Orchestrator Script for Enterprise Release Manager
param(
    [string]$Increment = "none", # patch, minor, major, none
    [string]$ManualVersion = "",
    [string]$ReleaseChannel = "stable",
    [bool]$Mandatory = $false,
    [switch]$DryRun,
    [switch]$SkipTests, # Allows skipping tests for faster local development
    [switch]$Publish,
    [switch]$LiveTest
)

# Parse command line aliases
if ($args -contains "--publish" -or $args -contains "-publish") {
    $Publish = $true
}
if ($args -contains "--live-test" -or $args -contains "-live-test") {
    $LiveTest = $true
}

$ErrorActionPreference = "Stop"

# Create logs directory
$logDir = Join-Path $PSScriptRoot "..\Release\Build\Logs"
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
}

$releaseLogPath = Join-Path $logDir "release.log"
$buildLogPath = Join-Path $logDir "build.log"
$uploadLogPath = Join-Path $logDir "upload.log"
$verifyLogPath = Join-Path $logDir "verification.log"

# Clear logs on new run
"" | Set-Content $releaseLogPath
"" | Set-Content $buildLogPath
"" | Set-Content $uploadLogPath
"" | Set-Content $verifyLogPath

function Log-Global($msg, $level = "INFO") {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $formatted = "[$timestamp] [{0,-5}] {1}" -f $level, $msg
    Write-Output $formatted
    Add-Content -Path $releaseLogPath -Value $formatted
}

function Log-Build($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Add-Content -Path $buildLogPath -Value "[$timestamp] $msg"
}

Log-Global "======================================================"
Log-Global " Sunny Printers ERP Enterprise Release Manager"
Log-Global "======================================================"

# Load Secrets from release_secrets.env or Environment Variables
$supabaseUrl = ""
$supabaseKey = ""
$supabaseBucket = "updates"

$testSupabaseUrl = ""
$testSupabaseKey = ""
$testSupabaseBucket = "updates"

$githubToken = ""
$githubRepository = ""

$secretsPath = Join-Path $PSScriptRoot "..\release_secrets.env"
if (Test-Path $secretsPath) {
    Get-Content $secretsPath | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line -split "=", 2
            $key = $parts[0].Trim()
            $val = $parts[1].Trim()
            if ($val.StartsWith('"') -and $val.EndsWith('"')) { $val = $val.Substring(1, $val.Length - 2) }
            if ($val.StartsWith("'") -and $val.EndsWith("'")) { $val = $val.Substring(1, $val.Length - 2) }
            
            # Mappings for Production
            if ($key -eq "SUPABASE_URL") { $supabaseUrl = $val }
            if ($key -eq "SUPABASE_KEY") { $supabaseKey = $val }
            if ($key -eq "SUPABASE_BUCKET") { $supabaseBucket = $val }
            
            # Mappings for Testing
            if ($key -eq "TEST_SUPABASE_URL") { $testSupabaseUrl = $val }
            if ($key -eq "TEST_SUPABASE_KEY") { $testSupabaseKey = $val }
            if ($key -eq "TEST_SUPABASE_BUCKET") { $testSupabaseBucket = $val }

            # GitHub Release credentials
            if ($key -eq "GITHUB_TOKEN") { $githubToken = $val }
            if ($key -eq "GITHUB_REPOSITORY") { $githubRepository = $val }
        }
    }
}

# Fallbacks to Environment Variables
if (-not $supabaseUrl) { $supabaseUrl = $env:SUPABASE_URL }
if (-not $supabaseKey) { $supabaseKey = $env:SUPABASE_KEY }
if (-not $supabaseBucket) { $supabaseBucket = $env:SUPABASE_BUCKET }

if (-not $testSupabaseUrl) { $testSupabaseUrl = $env:TEST_SUPABASE_URL }
if (-not $testSupabaseKey) { $testSupabaseKey = $env:TEST_SUPABASE_KEY }
if (-not $testSupabaseBucket) { $testSupabaseBucket = $env:TEST_SUPABASE_BUCKET }

if (-not $githubToken) { $githubToken = $env:GITHUB_TOKEN }
if (-not $githubRepository) { $githubRepository = $env:GITHUB_REPOSITORY }

# Debug output WITHOUT exposing secret values
Log-Global "Environment Credentials Presence Check:"
Log-Global " - TEST_SUPABASE_URL present: $(if ($testSupabaseUrl) { "YES" } else { "NO" })"
Log-Global " - TEST_SUPABASE_KEY present: $(if ($testSupabaseKey) { "YES" } else { "NO" })"
Log-Global " - SUPABASE_URL present: $(if ($supabaseUrl) { "YES" } else { "NO" })"
Log-Global " - SUPABASE_KEY present: $(if ($supabaseKey) { "YES" } else { "NO" })"
Log-Global " - GITHUB_TOKEN present: $(if ($githubToken) { "YES" } else { "NO" })"

# Helper to execute Maven Commands
function Execute-Maven($cmdName, $mvnArgs) {
    Log-Global "Executing Maven: mvn $mvnArgs..."
    Log-Build "=== Starting ${cmdName}: mvn $mvnArgs ==="
    
    # Save original Environment Variables to prevent contamination
    $origUrl = $env:SUPABASE_URL
    $origKey = $env:SUPABASE_KEY
    $origBucket = $env:SUPABASE_BUCKET
    
    # Force tests to run against Testing Supabase if credentials are provided
    if ($testSupabaseUrl -and $testSupabaseKey) {
        $env:SUPABASE_URL = $testSupabaseUrl
        $env:SUPABASE_KEY = $testSupabaseKey
        $env:SUPABASE_BUCKET = $testSupabaseBucket
    } else {
        # Clear them to isolate SQLite tests from Prod
        $env:SUPABASE_URL = $null
        $env:SUPABASE_KEY = $null
        $env:SUPABASE_BUCKET = $null
    }

    try {
        $mvnOutput = Invoke-Expression "mvn $mvnArgs 2>&1"
        $mvnOutput | ForEach-Object { Log-Build $_ }
        
        $hasFailure = $false
        foreach ($line in $mvnOutput) {
            if ($line -match "BUILD FAILURE" -or $line -match "Failures: [1-9]" -or $line -match "Errors: [1-9]") {
                $hasFailure = $true
                break
            }
        }
        
        if ($hasFailure) {
            throw "Maven ${cmdName} failed. See build.log for details."
        }
        Log-Global " - ${cmdName}: OK"
    } finally {
        # Restore environment variables
        $env:SUPABASE_URL = $origUrl
        $env:SUPABASE_KEY = $origKey
        $env:SUPABASE_BUCKET = $origBucket
    }
}

# --- Phase 1: Project Validation ---
Log-Global "Phase 1: Project Validation..."
try {
    # 1. Check Maven
    $mvnVer = mvn -version 2>$null
    if (-not $mvnVer) { throw "Maven is not installed or not in PATH." }
    Log-Global " - Maven check: OK"
    
    # 2. Check Java 21
    $oldEAP = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $javaVer = java -version 2>&1 | Out-String
    $ErrorActionPreference = $oldEAP
    if ($javaVer -match "21") {
        Log-Global " - Java check: OK (Java 21 detected)"
    } else {
        Log-Global " - Warning: Java version might not be 21: $javaVer" "WARN"
    }

    # 3. Check jpackage
    $jpVer = jpackage --version 2>$null
    if (-not $jpVer) { throw "jpackage is not available in PATH." }
    Log-Global " - jpackage check: OK"

    # 4. Check git clean status
    $gitStatus = git status --porcelain 2>$null
    if ($gitStatus) {
        Log-Global " - Git check: WARNING: Git repository has uncommitted changes." "WARN"
    } else {
        Log-Global " - Git check: OK (repository is clean)"
    }

    # 5. Check version.properties & release_notes.md
    $propPath = Join-Path $PSScriptRoot "..\src\main\resources\version.properties"
    $notesPath = Join-Path $PSScriptRoot "..\release_notes.md"
    if (-not (Test-Path $propPath)) { throw "version.properties is missing at $propPath" }
    if (-not (Test-Path $notesPath)) { throw "release_notes.md is missing at $notesPath" }
    Log-Global " - Version and Release Notes files: OK"

    # 6. Check App Icon
    $iconPath = Join-Path $PSScriptRoot "..\src\main\resources\images\app_icon.ico"
    if (-not (Test-Path $iconPath)) { throw "App Icon is missing at $iconPath" }
    Log-Global " - App Icon file check: OK"
    
    Log-Global "Validation successful!"
} catch {
    Log-Global "Validation failed: $_" "ERROR"
    exit 1
}

# --- Phase 2: Version Management & Compile ---
Log-Global "Phase 2: Version Management & Compilation..."
$versionScript = Join-Path $PSScriptRoot "version.ps1"
$newVersion = ""
try {
    $newVersion = (& $versionScript -Increment $Increment -ManualVersion $ManualVersion).ToString().Trim()
    Log-Global "Release Version set to: $newVersion"
} catch {
    Log-Global "Version management failed: $_" "ERROR"
    exit 1
}

if ($DryRun) {
    Log-Global "Dry-Run requested. Aborting build process before code modification."
    exit 0
}

# Compile code to verify compilation
try {
    Log-Global "Compiling codebase..."
    Execute-Maven "Compilation" "clean compile -DskipTests"
    Log-Global " - Compilation: OK"
} catch {
    Log-Global "Compilation failed: $_" "ERROR"
    exit 1
}

# Skip standard testing suite if running purely in LiveTest mode or SkipTests is set
$runStandardTests = (-not $SkipTests) -and (-not $LiveTest)

if ($runStandardTests) {
    # --- Phase 3: Unit Tests ---
    Log-Global "Phase 3: Run Unit Tests..."
    try { Execute-Maven "Unit Tests" "test -Punit" } catch { Log-Global "Unit testing failed: $_" "ERROR"; exit 1 }

    # --- Phase 4: Run Sanity Tests ---
    Log-Global "Phase 4: Run Sanity Tests..."
    try { Execute-Maven "Sanity Tests" "test -Psanity" } catch { Log-Global "Sanity testing failed: $_" "ERROR"; exit 1 }

    # --- Phase 5: Run Integration Tests ---
    Log-Global "Phase 5: Run Integration Tests (SQLite only)..."
    try { Execute-Maven "Integration Tests" "verify -Pintegration" } catch { Log-Global "Integration testing failed: $_" "ERROR"; exit 1 }

    # --- Phase 6: Run Performance Tests ---
    Log-Global "Phase 6: Run Performance Tests (SQLite only)..."
    try { Execute-Maven "Performance Tests" "verify -Pperformance" } catch { Log-Global "Performance testing failed: $_" "ERROR"; exit 1 }

    # --- Phase 7: Run Chaos Tests ---
    Log-Global "Phase 7: Run Chaos Tests (SQLite only)..."
    try { Execute-Maven "Chaos Tests" "verify -Pchaos" } catch { Log-Global "Chaos testing failed: $_" "ERROR"; exit 1 }

    # --- Phase 8: Run Release Verification Tests ---
    Log-Global "Phase 8: Run Release Verification Tests..."
    try { Execute-Maven "Release Verification Tests" "verify -Prelease" } catch { Log-Global "Release verification testing failed: $_" "ERROR"; exit 1 }
} else {
    Log-Global "Phases 3 to 8 (Standard testing suite) skipped."
}

# --- Phase 9: Package Application ---
# Package Application is skipped if we only requested LiveTest
$msiPath = ""
$zipPath = ""
$jarPath = ""

if (-not $LiveTest) {
    Log-Global "Phase 9: Package Application (Generating MSI, ZIP, JAR)..."
    $packageScript = Join-Path $PSScriptRoot "package.ps1"
    try {
        $artifacts = & $packageScript -Version $newVersion
        $msiPath = $artifacts[0]
        $zipPath = $artifacts[1]
        $jarPath = $artifacts[2]
        Log-Global " - Package OK"
        Log-Global "   MSI: $msiPath"
        Log-Global "   ZIP: $zipPath"
        Log-Global "   JAR: $jarPath"
    } catch {
        Log-Global "Packaging failed: $_" "ERROR"
        exit 1
    }

    # --- Phase 10: Generate SHA256 ---
    Log-Global "Phase 10: Generate SHA256 Checksums..."
    $hashScript = Join-Path $PSScriptRoot "hash.ps1"
    $metaDir = Join-Path $PSScriptRoot "..\target\metadata"
    try {
        & $hashScript -Version $newVersion -MsiPath $msiPath -ZipPath $zipPath -JarPath $jarPath -ReleaseNotesPath $notesPath -OutputDir $metaDir
        Log-Global " - Hashes OK"
    } catch {
        Log-Global "Hash generation failed: $_" "ERROR"
        exit 1
    }
} else {
    Log-Global "Phases 9 & 10 (Packaging & Hashing) skipped for pure LiveTest execution."
}

# Extract parsed release notes
$notesText = "Release notes for version $newVersion"
$releaseMdPath = Join-Path $PSScriptRoot "..\target\metadata\release.md"
if (Test-Path $releaseMdPath) {
    $notesText = (Get-Content $releaseMdPath -Raw)
}

# --- Phase 11: Run Live Integration Tests ---
$liveTestsExecuted = $false
$liveTestsPassed = $true

if ($testSupabaseUrl -and $testSupabaseKey) {
    Log-Global "Phase 11: Run Live Integration Tests (against Testing Supabase)..."
    try {
        Log-Global " - Testing Supabase Connected"
        Execute-Maven "Live Integration Tests" "verify -Plive"
        $liveTestsExecuted = $true
        Log-Global "Live Integration Tests passed successfully!"
    } catch {
        $liveTestsPassed = $false
        Log-Global "Live Testing failed: $_" "WARN"
        
        # Rollback local version if it was not checked out (if needed)
        if (-not $LiveTest) {
            Log-Global "Rolling back version changes in pom.xml..."
            git checkout -- pom.xml 2>$null
            git checkout -- src/main/resources/version.properties 2>$null
        }
        
        Log-Global "======================================================"
        Log-Global " Packaging completed successfully." "INFO"
        Log-Global " Live Testing failed." "ERROR"
        Log-Global " Publishing skipped." "WARN"
        Log-Global "======================================================"
        exit 1
    }
} else {
    Log-Global "Phase 11: Run Live Integration Tests (SKIPPED because Testing Supabase credentials are missing)." "WARN"
}

# If we were running ONLY live tests, we are done
if ($LiveTest) {
    if ($liveTestsExecuted -and $liveTestsPassed) {
        Log-Global "Live Test execution complete: SUCCESS."
        exit 0
    } else {
        Log-Global "Live Test execution complete: FAILED."
        exit 1
    }
}

# --- Phase 12 & 13: Create GitHub Release & Publish Update Metadata ---
if ($Publish) {
    if (-not $liveTestsPassed) {
        Log-Global "Publishing aborted because Live Tests did not pass." "ERROR"
        exit 1
    }
    
    Log-Global "Phase 12 & 13: Creating GitHub Release & Publishing Update Metadata..."
    $uploadScript = Join-Path $PSScriptRoot "upload.ps1"
    
    $hasSecrets = $true
    if (-not $githubToken -or -not $supabaseUrl -or -not $supabaseKey) {
        $hasSecrets = $false
        Log-Global "Publishing skipped because required credentials (GITHUB_TOKEN or Production SUPABASE_URL/KEY) are missing." "WARN"
    }
    
    if ($hasSecrets) {
        try {
            Log-Global "Executing upload script..."
            & $uploadScript -Version $newVersion -MsiPath $msiPath -ZipPath $zipPath -JarPath $jarPath -ReleaseNotes $notesText -ReleaseChannel $ReleaseChannel -Mandatory $Mandatory -SupabaseUrl $supabaseUrl -SupabaseKey $supabaseKey -SupabaseBucket $supabaseBucket -GithubToken $githubToken -GithubRepository $githubRepository
            Log-Global " - GitHub Release Created"
            Log-Global " - Production Metadata Updated"
            
            # Verification Step
            Log-Global "Phase 13 (Verify): Verification of published release..."
            $verifyScript = Join-Path $PSScriptRoot "verify.ps1"
            try {
                & $verifyScript -Version $newVersion -MsiPath $msiPath -ZipPath $zipPath -ReleaseChannel $ReleaseChannel -SupabaseUrl $supabaseUrl -SupabaseKey $supabaseKey -SupabaseBucket $supabaseBucket
                Log-Global "Verification complete. Release check PASSED!"
            } catch {
                Log-Global "Verification failed: $_. Initiating Rollback..." "ERROR"
                $rollbackScript = Join-Path $PSScriptRoot "rollback.ps1"
                & $rollbackScript -Version $newVersion -ReleaseChannel $ReleaseChannel -SupabaseUrl $supabaseUrl -SupabaseKey $supabaseKey -SupabaseBucket $supabaseBucket
                exit 1
            }
        } catch {
            Log-Global "Upload or publishing failed: $_. Initiating Rollback..." "ERROR"
            $rollbackScript = Join-Path $PSScriptRoot "rollback.ps1"
            & $rollbackScript -Version $newVersion -ReleaseChannel $ReleaseChannel -SupabaseUrl $supabaseUrl -SupabaseKey $supabaseKey -SupabaseBucket $supabaseBucket
            exit 1
        }
    } else {
        Log-Global "======================================================"
        Log-Global " Packaging completed successfully." "INFO"
        Log-Global " Publishing skipped because Production upload credentials are missing." "WARN"
        Log-Global "======================================================"
    }
} else {
    Log-Global "Publishing skipped (no --publish flag specified)."
}

# Finalize local Release Directory Setup
Log-Global "Finalizing local Release folder..."
$finalReleaseDir = Join-Path $PSScriptRoot "..\Release\$newVersion"
if (Test-Path $finalReleaseDir) {
    Remove-Item $finalReleaseDir -Recurse -Force | Out-Null
}
New-Item -ItemType Directory -Path $finalReleaseDir -Force | Out-Null

# 1. Installers folder
$installerDir = Join-Path $finalReleaseDir "Installer"
New-Item -ItemType Directory -Path $installerDir -Force | Out-Null
Copy-Item $msiPath $installerDir
Copy-Item $zipPath $installerDir
Copy-Item $jarPath $installerDir

# 2. Hashes folder
$hashesDir = Join-Path $finalReleaseDir "Hashes"
New-Item -ItemType Directory -Path $hashesDir -Force | Out-Null
Copy-Item (Join-Path $metaDir "SHA256_msi.txt") $hashesDir
Copy-Item (Join-Path $metaDir "SHA256_zip.txt") $hashesDir
Copy-Item (Join-Path $metaDir "SHA256_jar.txt") $hashesDir
Copy-Item (Join-Path $metaDir "SHA256.txt") $hashesDir

# 3. Metadata folder
$metadataDir = Join-Path $finalReleaseDir "Metadata"
New-Item -ItemType Directory -Path $metadataDir -Force | Out-Null
Copy-Item (Join-Path $metaDir "release.json") $metadataDir
Copy-Item (Join-Path $metaDir "release.xml") $metadataDir
Copy-Item (Join-Path $metaDir "release.md") $metadataDir

# 4. Logs folder
$destLogsDir = Join-Path $finalReleaseDir "Logs"
New-Item -ItemType Directory -Path $destLogsDir -Force | Out-Null
Copy-Item $releaseLogPath $destLogsDir
Copy-Item $buildLogPath $destLogsDir
Copy-Item $uploadLogPath $destLogsDir
Copy-Item $verifyLogPath $destLogsDir

Log-Global "Release folder finalized at: $finalReleaseDir"
Log-Global "======================================================"
Log-Global " Sunny Printers ERP Release Manager: SUCCESS!"
Log-Global " Version: $newVersion processed for channel '$ReleaseChannel'"
Log-Global "======================================================"
