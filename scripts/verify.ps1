# verify.ps1 - Phase 11: Release Verification
param(
    [string]$Version,
    [string]$MsiPath,
    [string]$ZipPath,
    [string]$ReleaseChannel = "stable",
    [string]$SupabaseUrl = "",
    [string]$SupabaseKey = "",
    [string]$SupabaseBucket = "updates"
)

$ErrorActionPreference = "Stop"

function Log-Message($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Output "[$timestamp] [VERIFY] $msg"
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

if (-not $SupabaseUrl -or -not $SupabaseKey) {
    throw "Supabase credentials are not configured. Cannot perform verification."
}

# Normalize URL
$SupabaseUrl = $SupabaseUrl.Trim()
if ($SupabaseUrl.EndsWith("/")) {
    $SupabaseUrl = $SupabaseUrl.Substring(0, $SupabaseUrl.Length - 1)
}

Log-Message "Starting verification for version $Version..."

# 2. Query DB Table app_updates for this version
Log-Message "Verifying database row in app_updates..."
$dbUrl = "$SupabaseUrl/rest/v1/app_updates?version=eq.$Version&release_channel=eq.$ReleaseChannel"
$headers = @{
    "Authorization" = "Bearer $SupabaseKey"
    "apikey"        = $SupabaseKey
}

$records = Invoke-RestMethod -Uri $dbUrl -Method Get -Headers $headers
if ($records.Count -eq 0 -or $records -eq $null) {
    throw "Verification FAILED: No record found in app_updates for version $Version."
}

$record = $records[0]
Log-Message "Database row exists. Verified Fields:"
Log-Message " - Version: $($record.version)"
Log-Message " - Release Channel: $($record.release_channel)"
Log-Message " - Published: $($record.published)"
Log-Message " - Storage Path: $($record.storage_path)"

# Check database fields match expected values
$localMsiSize = (Get-Item $MsiPath).Length
$localMsiSha256 = (Get-FileHash -Path $MsiPath -Algorithm "SHA256").Hash.ToLower()

if ($record.file_size -ne $localMsiSize) {
    throw "Verification FAILED: Database file size ($($record.file_size)) does not match local file size ($localMsiSize)."
}
if ($record.sha256.ToLower() -ne $localMsiSha256) {
    throw "Verification FAILED: Database SHA-256 ($($record.sha256)) does not match local SHA-256 ($localMsiSha256)."
}
Log-Message "Database file size and SHA-256 match local values perfectly."

# 3. Verify Storage File availability & download url
Log-Message "Verifying storage file download URL..."
$downloadUrl = "$SupabaseUrl/storage/v1/object/public/$SupabaseBucket/updates/SunnyPrintersERP-$Version.msi"
Log-Message "Download URL: $downloadUrl"

$tempDownloadFile = Join-Path $env:TEMP "sunny_printers_verify.msi"
if (Test-Path $tempDownloadFile) {
    Remove-Item $tempDownloadFile -Force
}

try {
    # Perform HTTP download
    Invoke-WebRequest -Uri $downloadUrl -OutFile $tempDownloadFile
    Log-Message "Artifact download completed successfully (HTTP 200)."
    
    # Calculate downloaded file hashes and sizes
    $downloadedSize = (Get-Item $tempDownloadFile).Length
    $downloadedSha256 = (Get-FileHash -Path $tempDownloadFile -Algorithm "SHA256").Hash.ToLower()
    
    if ($downloadedSize -ne $localMsiSize) {
        throw "Verification FAILED: Downloaded MSI size ($downloadedSize) does not match local MSI size ($localMsiSize)."
    }
    if ($downloadedSha256 -ne $localMsiSha256) {
        throw "Verification FAILED: Downloaded MSI SHA-256 ($downloadedSha256) does not match local MSI SHA-256 ($localMsiSha256)."
    }
    Log-Message "Downloaded MSI matches local MSI file size and SHA-256 perfectly."
} catch {
    throw "Storage file verification failed: $_"
} finally {
    if (Test-Path $tempDownloadFile) {
        Remove-Item $tempDownloadFile -Force
    }
}

Log-Message "Verification completed successfully. Release is valid and public."
return $true
