# cleanup.ps1 - Phase 3: Automatic Cleanup
param(
    [switch]$VerboseLogging
)

$ErrorActionPreference = "Stop"

function Log-Message($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Output "[$timestamp] [CLEANUP] $msg"
}

Log-Message "Starting cleanup process..."

# 1. Clean Maven target folder
$targetPath = Join-Path $PSScriptRoot "..\target"
if (Test-Path $targetPath) {
    Log-Message "Cleaning maven target directory at: $targetPath"
    try {
        Remove-Item -Path $targetPath -Recurse -Force
        Log-Message "Successfully deleted target/ directory."
    } catch {
        Log-Message "Warning: Could not completely delete target/ directory. Some files might be locked: $_"
    }
} else {
    Log-Message "target/ directory does not exist. Skipping."
}

# 2. Clean temporary packager build logs & hashes
$tempBuildLogs = Join-Path $PSScriptRoot "..\build.log"
$tempErrorLogs = Join-Path $PSScriptRoot "..\build_error.txt"
$tempUtfLogs = Join-Path $PSScriptRoot "..\build_utf8.log"

$logsToDelete = @($tempBuildLogs, $tempErrorLogs, $tempUtfLogs)
foreach ($logFile in $logsToDelete) {
    if (Test-Path $logFile) {
        Remove-Item -Path $logFile -Force
        Log-Message "Deleted temporary log: $logFile"
    }
}

Log-Message "Cleanup process completed successfully."
