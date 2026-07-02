# release.ps1 - Master Orchestrator Script for Enterprise Release Manager
param(
    [string]$Increment = "none", # patch, minor, major, none
    [string]$ManualVersion = "",
    [string]$ReleaseChannel = "stable",
    [bool]$Mandatory = $false,
    [switch]$DryRun,
    [switch]$SkipTests, # Allows skipping tests for faster local development
    [switch]$Publish
)

# Support --publish alias or passing via $args
if ($args -contains "--publish" -or $args -contains "-publish") {
    $Publish = $true
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
    $formatted = "[$timestamp] [$level] $msg"
    Write-Output $formatted
    Add-Content -Path $releaseLogPath -Value $formatted
}

function Log-Build($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Add-Content -Path $buildLogPath -Value "[$timestamp] $msg"
}

function Log-Upload($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Add-Content -Path $uploadLogPath -Value "[$timestamp] $msg"
}

function Log-Verify($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Add-Content -Path $verifyLogPath -Value "[$timestamp] $msg"
}

Log-Global "======================================================"
Log-Global " Sunny Printers ERP Enterprise Release Manager"
Log-Global "======================================================"

# Load Secrets for Validation (Defer credentials check to Publish phase)
$supabaseUrl = ""
$supabaseKey = ""
$supabaseBucket = "updates"
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
            if ($key -eq "SUPABASE_URL") { $supabaseUrl = $val }
            if ($key -eq "SUPABASE_KEY") { $supabaseKey = $val }
            if ($key -eq "SUPABASE_BUCKET") { $supabaseBucket = $val }
        }
    }
}
if (-not $supabaseUrl) { $supabaseUrl = $env:SUPABASE_URL }
if (-not $supabaseKey) { $supabaseKey = $env:SUPABASE_KEY }
if (-not $supabaseBucket) { $supabaseBucket = $env:SUPABASE_BUCKET }

# Phase 1: Project Validation
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
        Log-Global " - Warning: Java version might not be 21: $javaVer"
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

# Phase 2: Version Management
Log-Global "Phase 2: Version Management..."
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
    Log-Global "Dry-Run requested. Aborting build process before modifications."
    exit 0
}

# Phase 3: Automatic Cleanup
Log-Global "Phase 3: Automatic Cleanup..."
$cleanupScript = Join-Path $PSScriptRoot "cleanup.ps1"
try {
    & $cleanupScript
    Log-Global "Cleanup completed."
} catch {
    Log-Global "Cleanup failed: $_" "ERROR"
    exit 1
}

# Phase 4: Automated Testing
if ($SkipTests) {
    Log-Global "Phase 4: Automated Testing (SKIPPED by user request)."
} else {
    Log-Global "Phase 4: Automated Testing..."
    Log-Build "Starting automated tests: mvn clean test"
    try {
        $testOut = mvn clean test 2>&1
        $testOut | ForEach-Object { Log-Build $_ }
        
        # Check for build failures
        $hasFailure = $false
        foreach ($line in $testOut) {
            if ($line -match "BUILD FAILURE" -or $line -match "Failures: [1-9]" -or $line -match "Errors: [1-9]") {
                $hasFailure = $true
                break
            }
        }
        
        if ($hasFailure) {
            # Generate simple failure HTML
            $failHtml = "<html><body><h1>Tests Failed</h1><pre>$testOut</pre></body></html>"
            Set-Content -Path (Join-Path $logDir "test_summary.html") -Value $failHtml
            throw "Maven test suite failed. Check build.log."
        }
        
        Log-Global "Tests passed successfully!"
        
        # Generate success HTML summary
        $successHtml = "<html><body style='font-family: sans-serif; padding: 20px;'><h2>Test Summary: Success</h2><p>All tests executed and completed successfully.</p></body></html>"
        Set-Content -Path (Join-Path $logDir "test_summary.html") -Value $successHtml
    } catch {
        Log-Global "Testing failed: $_" "ERROR"
        # Version rollback
        Log-Global "Rolling back version changes in pom.xml..."
        git checkout -- pom.xml 2>$null
        git checkout -- src/main/resources/version.properties 2>$null
        exit 1
    }
}

# Phase 5 & 6: Packaging & Branding
Log-Global "Phase 5 & 6: Compilation, Packaging & Branding..."
$packageScript = Join-Path $PSScriptRoot "package.ps1"
$msiPath = ""
$zipPath = ""
try {
    $artifacts = & $packageScript -Version $newVersion
    $msiPath = $artifacts[0]
    $zipPath = $artifacts[1]
    Log-Global "Packaged MSI: $msiPath"
    Log-Global "Packaged ZIP: $zipPath"
} catch {
    Log-Global "Packaging failed: $_" "ERROR"
    exit 1
}

# Phase 7 & 8: Hash Generation & Release Notes
Log-Global "Phase 7 & 8: Hash Generation & Release Notes Parsing..."
$hashScript = Join-Path $PSScriptRoot "hash.ps1"
$metaDir = Join-Path $PSScriptRoot "..\target\metadata"
try {
    & $hashScript -Version $newVersion -MsiPath $msiPath -ZipPath $zipPath -ReleaseNotesPath $notesPath -OutputDir $metaDir
    Log-Global "Hash and Release Metadata generated."
} catch {
    Log-Global "Hash generation failed: $_" "ERROR"
    exit 1
}

# Extract parsed release notes
$notesText = "Release notes for version $newVersion"
$releaseMdPath = Join-Path $metaDir "release.md"
if (Test-Path $releaseMdPath) {
    $notesText = (Get-Content $releaseMdPath -Raw)
}

# Phase 9 & 10: Supabase Upload
if ($Publish) {
    Log-Global "Phase 9 & 10: Uploading artifacts to Supabase Storage & DB Registry..."
    
    # Debug output WITHOUT exposing secret values
    Log-Global "Supabase Environment Debug:"
    Log-Global " - SUPABASE_URL present: $(if ($supabaseUrl) { "YES" } else { "NO" })"
    Log-Global " - SUPABASE_KEY present: $(if ($supabaseKey) { "YES" } else { "NO" })"
    Log-Global " - SUPABASE_BUCKET present: $(if ($supabaseBucket) { "YES" } else { "NO" })"
    
    $hasSecrets = $true
    if (-not $supabaseUrl -or -not $supabaseKey) {
        $hasSecrets = $false
        Log-Global "Publishing failed because Supabase credentials are missing." "WARN"
    } else {
        # Check if Supabase app_updates table is reachable
        $headers = @{ "Authorization" = "Bearer $supabaseKey"; "apikey" = $supabaseKey }
        $dbCheckUrl = "$($supabaseUrl.TrimEnd('/'))/rest/v1/app_updates?limit=1"
        try {
            $res = Invoke-RestMethod -Uri $dbCheckUrl -Method Get -Headers $headers -TimeoutSec 10
            Log-Global " - Supabase Endpoint check: OK (app_updates table reachable)"
        } catch {
            $hasSecrets = $false
            Log-Global "Publishing failed because Supabase 'app_updates' table is unreachable: $_" "WARN"
        }
    }
    
    if ($hasSecrets) {
        $uploadScript = Join-Path $PSScriptRoot "upload.ps1"
        try {
            Log-Upload "Uploading release $newVersion..."
            & $uploadScript -Version $newVersion -MsiPath $msiPath -ZipPath $zipPath -ReleaseNotes $notesText -ReleaseChannel $ReleaseChannel -Mandatory $Mandatory -SupabaseUrl $supabaseUrl -SupabaseKey $supabaseKey -SupabaseBucket $supabaseBucket
            Log-Global "Artifacts successfully published to Supabase!"
            
            # Phase 11: Verification
            Log-Global "Phase 11: Verification..."
            $verifyScript = Join-Path $PSScriptRoot "verify.ps1"
            try {
                Log-Verify "Verifying published release $newVersion..."
                & $verifyScript -Version $newVersion -MsiPath $msiPath -ZipPath $zipPath -ReleaseChannel $ReleaseChannel -SupabaseUrl $supabaseUrl -SupabaseKey $supabaseKey -SupabaseBucket $supabaseBucket
                Log-Global "Verification complete. Release check PASSED!"
            } catch {
                Log-Global "Verification failed: $_. Initiating Rollback..." "ERROR"
                Log-Verify "Verification failed: $_. Rolling back..."
                
                # Trigger Phase 13 Rollback
                $rollbackScript = Join-Path $PSScriptRoot "rollback.ps1"
                & $rollbackScript -Version $newVersion -ReleaseChannel $ReleaseChannel
                exit 1
            }
        } catch {
            Log-Global "Upload failed: $_. Initiating Rollback..." "ERROR"
            Log-Upload "Upload failed: $_. Rolling back..."
            
            # Trigger Phase 13 Rollback
            $rollbackScript = Join-Path $PSScriptRoot "rollback.ps1"
            & $rollbackScript -Version $newVersion -ReleaseChannel $ReleaseChannel
            exit 1
        }
    } else {
        Log-Global "======================================================"
        Log-Global " Packaging completed successfully." "INFO"
        Log-Global " Publishing failed because Supabase credentials are missing." "WARN"
        Log-Global "======================================================"
    }
} else {
    Log-Global "Publishing skipped (no --publish flag specified)."
}

# Phase 12: Release Folder Setup
Log-Global "Phase 12: Finalizing Release folder..."
$finalReleaseDir = Join-Path $PSScriptRoot "..\Release\$newVersion"
if (Test-Path $finalReleaseDir) {
    Remove-Item $finalReleaseDir -Recurse -Force
}
New-Item -ItemType Directory -Path $finalReleaseDir -Force | Out-Null

# 1. Installers folder
$installerDir = Join-Path $finalReleaseDir "Installer"
New-Item -ItemType Directory -Path $installerDir -Force | Out-Null
Copy-Item $msiPath $installerDir
Copy-Item $zipPath $installerDir

# 2. Hashes folder
$hashesDir = Join-Path $finalReleaseDir "Hashes"
New-Item -ItemType Directory -Path $hashesDir -Force | Out-Null
Copy-Item (Join-Path $metaDir "SHA256_msi.txt") $hashesDir
Copy-Item (Join-Path $metaDir "SHA256_zip.txt") $hashesDir

# 3. Metadata folder
$metadataDir = Join-Path $finalReleaseDir "Metadata"
New-Item -ItemType Directory -Path $metadataDir -Force | Out-Null
Copy-Item (Join-Path $metaDir "release.json") $metadataDir
Copy-Item (Join-Path $metaDir "release.xml") $metadataDir
Copy-Item (Join-Path $metaDir "release.md") $metadataDir

# 4. Logs folder (move current log snapshots)
$destLogsDir = Join-Path $finalReleaseDir "Logs"
New-Item -ItemType Directory -Path $destLogsDir -Force | Out-Null
Copy-Item $releaseLogPath $destLogsDir
Copy-Item $buildLogPath $destLogsDir
Copy-Item $uploadLogPath $destLogsDir
Copy-Item $verifyLogPath $destLogsDir

Log-Global "Release folder finalized at: $finalReleaseDir"
Log-Global "======================================================"
Log-Global " Sunny Printers ERP Release Manager: SUCCESS!"
Log-Global " Version: $newVersion published to channel '$ReleaseChannel'"
Log-Global "======================================================"
