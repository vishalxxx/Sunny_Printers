# rollback.ps1 - Phase 13: Release Rollback
param(
    [string]$Version,
    [string]$ReleaseChannel = "stable",
    [string]$SupabaseUrl = "",
    [string]$SupabaseKey = "",
    [string]$SupabaseBucket = "updates"
)

$ErrorActionPreference = "Continue" # Continue on rollback step failures to clean up as much as possible

function Log-Message($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Output "[$timestamp] [ROLLBACK] $msg"
}

# 1. Load Secrets if not provided
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
            
            if ($key -eq "SUPABASE_URL" -and -not $SupabaseUrl) { $SupabaseUrl = $val }
            if ($key -eq "SUPABASE_KEY" -and -not $SupabaseKey) { $SupabaseKey = $val }
            if ($key -eq "SUPABASE_BUCKET" -and -not $SupabaseBucket) { $SupabaseBucket = $val }
        }
    }
}

# Fallback to Environment Variables
if (-not $SupabaseUrl) { $SupabaseUrl = $env:SUPABASE_URL }
if (-not $SupabaseKey) { $SupabaseKey = $env:SUPABASE_KEY }
if (-not $SupabaseBucket) { $SupabaseBucket = $env:SUPABASE_BUCKET }

Log-Message "Initiating rollback for version $Version..."

if (-not $Version) {
    Log-Message "Error: Version parameter is missing. Rollback aborted."
    return
}

$headers = @{
    "Authorization" = "Bearer $SupabaseKey"
    "apikey"        = $SupabaseKey
}

if ($SupabaseUrl -and $SupabaseKey) {
    $SupabaseUrl = $SupabaseUrl.Trim()
    if ($SupabaseUrl.EndsWith("/")) {
        $SupabaseUrl = $SupabaseUrl.Substring(0, $SupabaseUrl.Length - 1)
    }

    # 2. Delete DB Row in app_updates
    Log-Message "Deleting database row for version $Version..."
    $dbUrl = "$SupabaseUrl/rest/v1/app_updates?version=eq.$Version&release_channel=eq.$ReleaseChannel"
    try {
        $res = Invoke-RestMethod -Uri $dbUrl -Method Delete -Headers $headers
        Log-Message "Database row deletion completed."
    } catch {
        Log-Message "Warning: Database row deletion failed or row did not exist: $_"
    }

    # 3. Delete MSI in storage
    $msiFileName = "SunnyPrintersERP-$Version.msi"
    $msiStoragePath = "updates/$msiFileName"
    $msiDeleteUrl = "$SupabaseUrl/storage/v1/object/$SupabaseBucket/$msiStoragePath"
    Log-Message "Deleting storage MSI file: $msiStoragePath..."
    try {
        $res = Invoke-RestMethod -Uri $msiDeleteUrl -Method Delete -Headers $headers
        Log-Message "MSI storage file deletion completed."
    } catch {
        Log-Message "Warning: MSI storage file deletion failed: $_"
    }

    # 4. Delete ZIP in storage
    $zipFileName = "SunnyPrintersERP-$Version.zip"
    $zipStoragePath = "updates/$zipFileName"
    $zipDeleteUrl = "$SupabaseUrl/storage/v1/object/$SupabaseBucket/$zipStoragePath"
    Log-Message "Deleting storage ZIP file: $zipStoragePath..."
    try {
        $res = Invoke-RestMethod -Uri $zipDeleteUrl -Method Delete -Headers $headers
        Log-Message "ZIP storage file deletion completed."
    } catch {
        Log-Message "Warning: ZIP storage file deletion failed: $_"
    }
} else {
    Log-Message "Warning: Supabase credentials not found. Skipping remote rollback steps."
}

# 5. Local cleanup
Log-Message "Cleaning up local build artifacts for version $Version..."
$distDir = Join-Path $PSScriptRoot "..\target\dist"
if (Test-Path $distDir) {
    try {
        Remove-Item -Path $distDir -Recurse -Force
        Log-Message "Successfully deleted target/dist directory."
    } catch {
        Log-Message "Warning: Could not completely delete target/dist directory: $_"
    }
}

Log-Message "Rollback process finished."
