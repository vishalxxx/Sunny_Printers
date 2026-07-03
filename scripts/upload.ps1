# upload.ps1 - Phase 12 & 13: GitHub Release Upload & Production Supabase Metadata Registry
param(
    [string]$Version,
    [string]$MsiPath,
    [string]$ZipPath,
    [string]$JarPath,
    [string]$ShaPath = "",
    [string]$ReleaseNotes = "",
    [string]$ReleaseChannel = "stable",
    [bool]$Mandatory = $false,
    [string]$SupabaseUrl = "",
    [string]$SupabaseKey = "",
    [string]$SupabaseBucket = "updates",
    [string]$GithubToken = "",
    [string]$GithubRepository = ""
)

$ErrorActionPreference = "Stop"

function Log-Message($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Output "[$timestamp] [UPLOAD] $msg"
}

# Resolve target metadata SHA256.txt if not specified
if (-not $ShaPath) {
    $ShaPath = Join-Path $PSScriptRoot "..\target\metadata\SHA256.txt"
}

# Validate local artifacts exist
if (-not (Test-Path $MsiPath)) { throw "MSI artifact not found at $MsiPath" }
if (-not (Test-Path $ZipPath)) { throw "ZIP artifact not found at $ZipPath" }
if (-not (Test-Path $JarPath)) { throw "JAR artifact not found at $JarPath" }
if (-not (Test-Path $ShaPath)) { throw "SHA256 checksums file not found at $ShaPath" }

# 1. Resolve GitHub Token and Repository
if (-not $GithubToken) {
    $GithubToken = $env:GITHUB_TOKEN
}
if (-not $GithubToken) {
    # Check release_secrets.env if GITHUB_TOKEN is defined there
    $secretsPath = Join-Path $PSScriptRoot "..\release_secrets.env"
    if (Test-Path $secretsPath) {
        Get-Content $secretsPath | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
                $parts = $line -split "=", 2
                if ($parts[0].Trim() -eq "GITHUB_TOKEN") {
                    $GithubToken = $parts[1].Trim()
                    if ($GithubToken.StartsWith('"') -and $GithubToken.EndsWith('"')) { $GithubToken = $GithubToken.Substring(1, $GithubToken.Length - 2) }
                    if ($GithubToken.StartsWith("'") -and $GithubToken.EndsWith("'")) { $GithubToken = $GithubToken.Substring(1, $GithubToken.Length - 2) }
                }
            }
        }
    }
}

if (-not $GithubRepository) {
    $GithubRepository = $env:GITHUB_REPOSITORY
}
if (-not $GithubRepository) {
    # Try parsing from git remote
    $gitRemote = git remote get-url origin 2>$null
    if ($gitRemote) {
        if ($gitRemote -match "github\.com[:/]([^/]+)/([^/]+?)(?:\.git)?$") {
            $GithubRepository = "$($Matches[1])/$($Matches[2])"
        }
    }
}

if (-not $GithubToken) {
    throw "GITHUB_TOKEN is missing. Cannot upload release to GitHub."
}
if (-not $GithubRepository) {
    throw "GitHub Repository identifier (owner/repo) is missing."
}

Log-Message "GitHub Release configuration:"
Log-Message " - Repository: $GithubRepository"
Log-Message " - Token present: YES"

# 2. Interact with GitHub Release API
$githubApiUrl = "https://api.github.com"
$headers = @{
    "Authorization" = "Bearer $GithubToken"
    "Accept"        = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
    "User-Agent"    = "SunnyPrinters-Release-Manager"
}

$releaseUrl = "$githubApiUrl/repos/$GithubRepository/releases/tags/v$Version"
$release = $null

try {
    $release = Invoke-RestMethod -Uri $releaseUrl -Method Get -Headers $headers
    Log-Message "Found existing GitHub Release for tag v$Version."
} catch {
    # Create new Release
    Log-Message "Creating new GitHub Release for tag v$Version..."
    $createUrl = "$githubApiUrl/repos/$GithubRepository/releases"
    $body = @{
        tag_name = "v$Version"
        name = "v$Version"
        body = $ReleaseNotes
        draft = $false
        prerelease = ($ReleaseChannel -ne "stable")
    } | ConvertTo-Json -Depth 4
    
    try {
        $release = Invoke-RestMethod -Uri $createUrl -Method Post -Headers $headers -Body $body
        Log-Message "GitHub Release created successfully."
    } catch {
        throw "Failed to create GitHub Release: $_"
    }
}

# 3. Upload assets to GitHub Release
$filesToUpload = @{
    "SunnyPrintersERP-$Version.msi" = $MsiPath
    "SunnyPrintersERP-$Version.zip" = $ZipPath
    "SunnyPrinters-$Version.jar" = $JarPath
    "SHA256.txt" = $ShaPath
}

$githubUrls = @{}

# Strip RFC 6570 URI template suffix robustly using regex (handles any {?...} variation)
$rawUploadUrl = $release.upload_url
Log-Message "Raw GitHub upload_url from API: $rawUploadUrl"
$uploadTemplate = $rawUploadUrl -replace '\{[^}]*\}.*$', ''
Log-Message "Resolved upload base URL: $uploadTemplate"

if (-not $uploadTemplate -or -not $uploadTemplate.StartsWith("http")) {
    throw "GitHub Release upload_url is invalid or missing. Raw value: '$rawUploadUrl'"
}

foreach ($entry in $filesToUpload.GetEnumerator()) {
    $fileName = $entry.Key
    $filePath = $entry.Value
    
    # Check if asset already exists and delete it to prevent conflict
    if ($release.assets) {
        foreach ($asset in $release.assets) {
            if ($asset.name -eq $fileName) {
                Log-Message "Deleting existing asset '$fileName' from GitHub release..."
                try {
                    Invoke-RestMethod -Uri $asset.url -Method Delete -Headers $headers
                } catch {
                    Log-Message "Warning: Failed to delete asset: $_"
                }
            }
        }
    }
    
    # Build and log the final upload URL before sending
    $uploadUrl = "${uploadTemplate}?name=${fileName}"
    Log-Message "Uploading '$fileName' to GitHub Release..."
    Log-Message " - Upload URL: $uploadUrl"
    Log-Message " - File Path:  $filePath"
    Log-Message " - File Size:  $((Get-Item $filePath).Length) bytes"
    
    $uploadHeaders = @{
        "Authorization" = "Bearer $GithubToken"
        "Content-Type"  = "application/octet-stream"
        "User-Agent"    = "SunnyPrinters-Release-Manager"
    }
    
    try {
        $uploadResponse = Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $uploadHeaders -InFile $filePath -TimeoutSec 300
        $githubUrls[$fileName] = $uploadResponse.browser_download_url
        Log-Message "Uploaded '$fileName' successfully. URL: $($uploadResponse.browser_download_url)"
    } catch {
        $uploadStatusCode = 0
        $uploadErrorBody = ""
        try {
            $uploadStatusCode = $_.Exception.Response.StatusCode.Value__
            $uploadStream = $_.Exception.Response.GetResponseStream()
            $uploadReader = New-Object System.IO.StreamReader($uploadStream)
            $uploadErrorBody = $uploadReader.ReadToEnd()
            $uploadReader.Close()
        } catch { }
        Log-Message "Asset upload failed for '$fileName'."
        Log-Message " - HTTP Status: $uploadStatusCode"
        Log-Message " - Error Body:  $uploadErrorBody"
        throw "Failed to upload asset '$fileName' to GitHub Release (HTTP $uploadStatusCode): $uploadErrorBody"
    }
}

$msiDownloadUrl = $githubUrls["SunnyPrintersERP-$Version.msi"]
$githubReleaseUrl = $release.html_url

# 4. Update app_updates Table in Production Supabase
Log-Message "Registering release metadata on Production Supabase..."

# Load Production Secrets if not provided
if (-not $SupabaseUrl -or -not $SupabaseKey) {
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
}

if (-not $SupabaseUrl) { $SupabaseUrl = $env:SUPABASE_URL }
if (-not $SupabaseKey) { $SupabaseKey = $env:SUPABASE_KEY }
if (-not $SupabaseBucket) { $SupabaseBucket = $env:SUPABASE_BUCKET }

Log-Message "Supabase Credentials Debug in Upload script:"
Log-Message " - SupabaseUrl present: $(if ($SupabaseUrl) { "YES" } else { "NO" })"
Log-Message " - SupabaseKey present: $(if ($SupabaseKey) { "YES" } else { "NO" })"

if (-not $SupabaseUrl -or -not $SupabaseKey) {
    throw "Production Supabase credentials are not configured. Cannot publish update metadata."
}

# Normalize URL
$SupabaseUrl = $SupabaseUrl.Trim()
if ($SupabaseUrl.EndsWith("/")) {
    $SupabaseUrl = $SupabaseUrl.Substring(0, $SupabaseUrl.Length - 1)
}

$msiSize = (Get-Item $MsiPath).Length
$msiSha256 = (Get-FileHash -Path $MsiPath -Algorithm "SHA256").Hash.ToLower()
$nowUtc = (Get-Date -Date (Get-Date).ToUniversalTime() -Format "yyyy-MM-ddTHH:mm:ss.fffZ")

# Mappings for app_updates table
# We keep storage_path populated with a compatibility path, but set download_url to GitHub Releases download URL
$dbStoragePath = "$SupabaseBucket/updates/SunnyPrintersERP-$Version.msi"

$updatePayload = @{
    version = $Version
    minimum_supported_version = $Version # Default to the current version as minimum
    storage_path = $dbStoragePath        # Compatibility column
    download_url = $msiDownloadUrl
    github_release_tag = "v$Version"
    github_release_url = $githubReleaseUrl
    release_notes = $ReleaseNotes
    sha256 = $msiSha256
    checksum_algorithm = "SHA-256"
    installer_type = "MSI"
    file_name = "SunnyPrintersERP-$Version.msi"
    file_size = $msiSize
    published = $true
    mandatory = $Mandatory
    release_channel = $ReleaseChannel
    created_at = $nowUtc
    updated_at = $nowUtc
    published_at = $nowUtc
}

$payloadJson = ConvertTo-Json -InputObject $updatePayload -Depth 4
$upsertHeaders = @{
    "Authorization" = "Bearer $SupabaseKey"
    "apikey"        = $SupabaseKey
    "Content-Type"  = "application/json"
    "Prefer"        = "resolution=merge-duplicates,return=representation"
}

$dbUrl = "$SupabaseUrl/rest/v1/app_updates"

# --- Pre-insert Diagnostics ---
Log-Message "========================================================"
Log-Message "Posting release metadata to Supabase:"
Log-Message " - URL:              $dbUrl"
Log-Message " - Version:          $($updatePayload.version)"
Log-Message " - GitHub Release:   $($updatePayload.github_release_url)"
Log-Message " - Download URL:     $($updatePayload.download_url)"
Log-Message " - SHA256:           $($updatePayload.sha256)"
Log-Message " - File Size:        $($updatePayload.file_size) bytes"
Log-Message " - Release Channel:  $($updatePayload.release_channel)"
Log-Message " - Mandatory:        $($updatePayload.mandatory)"
Log-Message "Full JSON Payload:"
Log-Message $payloadJson
Log-Message "========================================================"

# --- Execute Upsert with full status capture ---
try {
    $response = Invoke-WebRequest -Uri $dbUrl -Method Post -Headers $upsertHeaders -Body $payloadJson -UseBasicParsing
    $statusCode = $response.StatusCode
    $responseBody = $response.Content

    Log-Message "HTTP Status: $statusCode"
    Log-Message "Response Body: $responseBody"

    if ($statusCode -eq 200 -or $statusCode -eq 201) {
        Log-Message "Insert Successful. app_updates row registered in Production Supabase."
    } else {
        throw "Unexpected HTTP status $statusCode from Supabase. Body: $responseBody"
    }
} catch {
    # Capture status code and body from error response (PowerShell 5.1 pattern)
    $statusCode = 0
    $errorBody = ""
    try {
        $statusCode = $_.Exception.Response.StatusCode.Value__
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $errorBody = $reader.ReadToEnd()
        $reader.Close()
    } catch { }

    Log-Message "HTTP Status: $statusCode"
    Log-Message "Error Body: $errorBody"
    Log-Message "Insert Failed."
    throw "Database upsert failed (HTTP $statusCode): $errorBody - Original error: $_"
}

Log-Message "GitHub Release and metadata publishing complete."
