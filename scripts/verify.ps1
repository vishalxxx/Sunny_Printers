# verify.ps1 - Phase 13: Release Verification (Verify Production Metadata and GitHub Release Download)
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

# 1. Load Production Secrets if not provided
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

Log-Message "Supabase Credentials Debug in Verify script:"
Log-Message " - SupabaseUrl present: $(if ($SupabaseUrl) { "YES" } else { "NO" })"
Log-Message " - SupabaseKey present: $(if ($SupabaseKey) { "YES" } else { "NO" })"

if (-not $SupabaseUrl -or -not $SupabaseKey) {
    throw "Production Supabase credentials are not configured. Cannot perform verification."
}

# Normalize URL
$SupabaseUrl = $SupabaseUrl.Trim()
if ($SupabaseUrl.EndsWith("/")) {
    $SupabaseUrl = $SupabaseUrl.Substring(0, $SupabaseUrl.Length - 1)
}

Log-Message "Starting verification for version $Version..."

# 2. Query DB Table app_updates for this version
Log-Message "Verifying database row in app_updates table on Production Supabase..."
$dbUrl = "$SupabaseUrl/rest/v1/app_updates?version=eq.$Version&release_channel=eq.$ReleaseChannel"
$headers = @{
    "Authorization" = "Bearer $SupabaseKey"
    "apikey"        = $SupabaseKey
    "User-Agent"    = "SunnyPrinters-Release-Manager"
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
Log-Message " - Download URL (GitHub): $($record.download_url)"
Log-Message " - GitHub Release Tag: $($record.github_release_tag)"
Log-Message " - GitHub Release URL: $($record.github_release_url)"

$downloadUrl = $record.download_url
if (-not $downloadUrl) {
    throw "Verification FAILED: download_url in database row is empty."
}

# Check database fields match expected local values
$localMsiSize = (Get-Item $MsiPath).Length
$localMsiSha256 = (Get-FileHash -Path $MsiPath -Algorithm "SHA256").Hash.ToLower()

if ($record.file_size -ne $localMsiSize) {
    throw "Verification FAILED: Database file size ($($record.file_size)) does not match local file size ($localMsiSize)."
}
if ($record.sha256.ToLower() -ne $localMsiSha256) {
    throw "Verification FAILED: Database SHA-256 ($($record.sha256)) does not match local SHA-256 ($localMsiSha256)."
}
Log-Message "Database file size and SHA-256 match local values perfectly."

# 3. Verify Download from GitHub Release URL with Retry
Log-Message "Verifying GitHub Release download availability..."
$tempDownloadFile = Join-Path $env:TEMP "sunny_printers_verify.msi"
if (Test-Path $tempDownloadFile) {
    Remove-Item $tempDownloadFile -Force
}

$maxRetries = 5
$retryDelay = 5
$downloadSuccess = $false

# User-Agent header is required for downloading from GitHub sometimes
for ($i = 1; $i -le $maxRetries; $i++) {
    try {
        Log-Message "Downloading from GitHub Releases: $downloadUrl (Attempt $i/$maxRetries)..."
        [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $downloadUrl -OutFile $tempDownloadFile -UserAgent "SunnyPrinters-Verifier" -TimeoutSec 45
        $downloadSuccess = $true
        break
    } catch {
        Log-Message "Download attempt $i failed: $_. Retrying in $retryDelay seconds..."
        Start-Sleep -Seconds $retryDelay
    }
}

if (-not $downloadSuccess) {
    throw "Verification FAILED: Could not download the asset from $downloadUrl after $maxRetries attempts."
}

try {
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
} finally {
    if (Test-Path $tempDownloadFile) {
        Remove-Item $tempDownloadFile -Force | Out-Null
    }
}

Log-Message "Verification completed successfully. Release update is active and public."
return $true
