# upload.ps1 - Phase 9 & 10: Supabase Artifact Upload & DB Row Upsert
param(
    [string]$Version,
    [string]$MsiPath,
    [string]$ZipPath,
    [string]$ReleaseNotes = "",
    [string]$ReleaseChannel = "stable",
    [bool]$Mandatory = $false,
    [string]$SupabaseUrl = "",
    [string]$SupabaseKey = "",
    [string]$SupabaseBucket = "updates"
)

$ErrorActionPreference = "Stop"

function Log-Message($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Output "[$timestamp] [UPLOAD] $msg"
}

# 1. Load Secrets if not provided
$secretsPath = Join-Path $PSScriptRoot "..\release_secrets.env"
if (Test-Path $secretsPath) {
    Log-Message "Loading credentials from release_secrets.env..."
    Get-Content $secretsPath | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line -split "=", 2
            $key = $parts[0].Trim()
            $val = $parts[1].Trim()
            # Strip quotes if present
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

Log-Message "Supabase Credentials Debug in Upload script:"
Log-Message " - SupabaseUrl present: $(if ($SupabaseUrl) { "YES" } else { "NO" })"
Log-Message " - SupabaseKey present: $(if ($SupabaseKey) { "YES" } else { "NO" })"
Log-Message " - SupabaseBucket present: $(if ($SupabaseBucket) { "YES" } else { "NO" })"

if (-not $SupabaseUrl -or -not $SupabaseKey) {
    throw "Supabase credentials are not configured. Please set SUPABASE_URL and SUPABASE_KEY in environment variables or release_secrets.env."
}

# Normalize URL
$SupabaseUrl = $SupabaseUrl.Trim()
if ($SupabaseUrl.EndsWith("/")) {
    $SupabaseUrl = $SupabaseUrl.Substring(0, $SupabaseUrl.Length - 1)
}

# Check if artifacts exist
if (-not (Test-Path $MsiPath)) { throw "MSI artifact not found at $MsiPath" }
if (-not (Test-Path $ZipPath)) { throw "ZIP artifact not found at $ZipPath" }

# 2. Upload MSI to Supabase Storage
$msiFileName = "SunnyPrintersERP-$Version.msi"
$msiStoragePath = "updates/$msiFileName"
$msiUploadUrl = "$SupabaseUrl/storage/v1/object/$SupabaseBucket/$msiStoragePath"

Log-Message "Uploading MSI artifact to Supabase Storage ($SupabaseBucket/$msiStoragePath)..."

$headers = @{
    "Authorization" = "Bearer $SupabaseKey"
    "apikey"        = $SupabaseKey
    "x-upsert"      = "true"
}

try {
    $response = Invoke-RestMethod -Uri $msiUploadUrl -Method Post -Headers $headers -InFile $MsiPath -ContentType "application/octet-stream"
    Log-Message "MSI upload successful: $response"
} catch {
    throw "MSI upload failed: $_"
}

# 3. Upload ZIP to Supabase Storage
$zipFileName = "SunnyPrintersERP-$Version.zip"
$zipStoragePath = "updates/$zipFileName"
$zipUploadUrl = "$SupabaseUrl/storage/v1/object/$SupabaseBucket/$zipStoragePath"

Log-Message "Uploading ZIP artifact to Supabase Storage ($SupabaseBucket/$zipStoragePath)..."

try {
    $response = Invoke-RestMethod -Uri $zipUploadUrl -Method Post -Headers $headers -InFile $ZipPath -ContentType "application/octet-stream"
    Log-Message "ZIP upload successful: $response"
} catch {
    throw "ZIP upload failed: $_"
}

# 4. Update app_updates Table (UPSERT)
Log-Message "Upserting release entry to app_updates table..."
$msiSize = (Get-Item $MsiPath).Length
$msiSha256 = (Get-FileHash -Path $MsiPath -Algorithm "SHA256").Hash.ToLower()
$createdAt = (Get-Date -Date (Get-Date).ToUniversalTime() -Format "yyyy-MM-ddTHH:mm:ss.fffZ")

# The database record maps the MSI file path for actual installer downloads.
$dbStoragePath = "$SupabaseBucket/$msiStoragePath"

$updatePayload = @{
    version = $Version
    minimum_supported_version = $Version # Default to the current version as minimum
    storage_path = $dbStoragePath
    file_name = $msiFileName
    file_size = $msiSize
    sha256 = $msiSha256
    release_channel = $ReleaseChannel
    published = $true
    mandatory = $Mandatory
    release_notes = $ReleaseNotes
    created_at = $createdAt
}

$payloadJson = ConvertTo-Json -InputObject $updatePayload -Depth 4
$upsertHeaders = @{
    "Authorization" = "Bearer $SupabaseKey"
    "apikey"        = $SupabaseKey
    "Content-Type"  = "application/json"
    "Prefer"        = "resolution=merge-duplicates"
}

$dbUrl = "$SupabaseUrl/rest/v1/app_updates"
try {
    $dbResponse = Invoke-RestMethod -Uri $dbUrl -Method Post -Headers $upsertHeaders -Body $payloadJson
    Log-Message "Database upsert successful. Row updated."
} catch {
    throw "Database upsert failed: $_"
}

Log-Message "Upload process completed successfully."
