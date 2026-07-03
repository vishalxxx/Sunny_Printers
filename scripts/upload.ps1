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

# ---------------------------------------------------------------------------
# Streaming asset uploader using System.Net.HttpWebRequest
# Invoke-RestMethod -InFile buffers the entire file before sending, causing
# silent connection drops on 100+ MB files when the socket idle-timeout fires
# before the buffer is flushed. HttpWebRequest with AllowWriteStreamBuffering=$false
# opens the TCP connection immediately and streams bytes progressively.
# ---------------------------------------------------------------------------
function Invoke-StreamingAssetUpload {
    param(
        [string]$UploadUrl,
        [string]$FileName,
        [string]$FilePath,
        [string]$Token,
        [int]$MaxRetries    = 3,
        [int]$TimeoutSec    = 900,   # 15 minutes -- large MSI on GitHub Actions runner
        [int]$BufferSizeKB  = 1024   # 1 MB streaming buffer
    )

    $fileInfo = Get-Item $FilePath
    $fileSize = $fileInfo.Length
    Log-Message "  Streaming upload: '$FileName' ($fileSize bytes, timeout: ${TimeoutSec}s, retries: $MaxRetries)"

    $attempt   = 0
    $uploaded  = $false
    $downloadUrl = ""

    while ($attempt -lt $MaxRetries -and -not $uploaded) {
        $attempt++
        if ($attempt -gt 1) {
            # Exponential backoff: 10s, 20s, 40s ...
            $delaySec = [Math]::Pow(2, $attempt - 1) * 10
            Log-Message "  Retry attempt $attempt/$MaxRetries after ${delaySec}s backoff..."
            Start-Sleep -Seconds $delaySec
        }

        $fileStream    = $null
        $requestStream = $null
        $response      = $null

        try {
            # --- Build HttpWebRequest ---
            $request = [System.Net.HttpWebRequest]::Create($UploadUrl)
            $request.Method                    = "POST"
            $request.ContentType               = "application/octet-stream"
            $request.ContentLength             = $fileSize
            $request.AllowWriteStreamBuffering = $false   # KEY: do NOT buffer in memory
            $request.SendChunked               = $false   # explicit Content-Length, not chunked
            $request.Timeout                   = $TimeoutSec * 1000
            $request.ReadWriteTimeout          = $TimeoutSec * 1000
            $request.Headers.Add("Authorization", "Bearer $Token")
            $request.UserAgent = "SunnyPrinters-Release-Manager"
            # GitHub upload endpoint does not accept Accept header on uploads
            $request.Accept = "application/vnd.github+json"

            # --- Stream file bytes to the request body ---
            $requestStream = $request.GetRequestStream()
            $fileStream    = [System.IO.File]::OpenRead($FilePath)
            $buffer        = New-Object byte[] ($BufferSizeKB * 1024)
            $totalSent     = 0
            $bytesRead     = 0

            while (($bytesRead = $fileStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                $requestStream.Write($buffer, 0, $bytesRead)
                $totalSent += $bytesRead
            }
            $requestStream.Flush()
            $requestStream.Close()
            $fileStream.Close()
            Log-Message "  All $totalSent bytes written to request stream. Waiting for GitHub response..."

            # --- Read response ---
            $response       = $request.GetResponse()
            $respStream     = $response.GetResponseStream()
            $respReader     = New-Object System.IO.StreamReader($respStream)
            $responseBody   = $respReader.ReadToEnd()
            $respReader.Close()
            $response.Close()
            $statusCode = [int]([System.Net.HttpWebResponse]$response).StatusCode

            Log-Message "  HTTP Status: $statusCode"
            Log-Message "  Response Body: $responseBody"

            $responseObj = $responseBody | ConvertFrom-Json
            $downloadUrl = $responseObj.browser_download_url
            $uploaded    = $true

        } catch [System.Net.WebException] {
            $webEx = $_.Exception

            # --- Classify error type ---
            $errorClass = switch ($webEx.Status) {
                ([System.Net.WebExceptionStatus]::Timeout)              { "TIMEOUT (exceeded ${TimeoutSec}s)" }
                ([System.Net.WebExceptionStatus]::ConnectionClosed)     { "CONNECTION RESET by remote" }
                ([System.Net.WebExceptionStatus]::SecureChannelFailure) { "TLS/SSL handshake failure" }
                ([System.Net.WebExceptionStatus]::ProtocolError)        { "HTTP protocol error" }
                ([System.Net.WebExceptionStatus]::ConnectFailure)       { "Connection refused / DNS failure" }
                ([System.Net.WebExceptionStatus]::ReceiveFailure)       { "Receive failure (server dropped connection)" }
                ([System.Net.WebExceptionStatus]::SendFailure)          { "Send failure (upload interrupted)" }
                default                                                  { "WebException status: $($webEx.Status)" }
            }

            Log-Message "  Upload attempt $attempt FAILED: $errorClass"
            Log-Message "  Exception type:    $($webEx.GetType().FullName)"
            Log-Message "  Exception message: $($webEx.Message)"
            if ($webEx.InnerException) {
                Log-Message "  Inner exception:   $($webEx.InnerException.GetType().FullName): $($webEx.InnerException.Message)"
            }

            # Try to read HTTP error body if the server did respond
            $httpStatus = 0
            $httpBody   = ""
            if ($webEx.Response) {
                try {
                    $httpStatus = [int]([System.Net.HttpWebResponse]$webEx.Response).StatusCode
                    $errStream  = $webEx.Response.GetResponseStream()
                    $errReader  = New-Object System.IO.StreamReader($errStream)
                    $httpBody   = $errReader.ReadToEnd()
                    $errReader.Close()
                    $webEx.Response.Close()
                } catch { }
                Log-Message "  HTTP Status: $httpStatus"
                Log-Message "  HTTP Body:   $httpBody"

                # 4xx errors (except 422 Unprocessable) are not retryable
                if ($httpStatus -ge 400 -and $httpStatus -lt 500 -and $httpStatus -ne 422) {
                    throw "GitHub asset upload failed (HTTP $httpStatus, non-retryable): $httpBody"
                }
            } else {
                Log-Message "  (No HTTP response -- network-level failure, server did not reply)"
            }

            if ($attempt -ge $MaxRetries) {
                throw "GitHub asset upload '$FileName' failed after $MaxRetries attempts. Last error: [$errorClass] $($webEx.Message)"
            }

        } catch {
            Log-Message "  Unexpected exception on attempt ${attempt}: $($_.Exception.GetType().FullName)"
            Log-Message "  Message:     $($_.Exception.Message)"
            Log-Message "  Stack trace: $($_.ScriptStackTrace)"
            if ($attempt -ge $MaxRetries) {
                throw "GitHub asset upload '$FileName' failed after $MaxRetries attempts (unexpected): $($_.Exception.Message)"
            }
        } finally {
            if ($fileStream    -and $fileStream.CanRead)    { try { $fileStream.Close()    } catch {} }
            if ($requestStream -and $requestStream.CanWrite){ try { $requestStream.Close() } catch {} }
            if ($response)                                  { try { $response.Close()      } catch {} }
        }
    }

    return $downloadUrl
}

# ---------------------------------------------------------------------------
# 3. Upload assets to GitHub Release
# ---------------------------------------------------------------------------
$filesToUpload = @{
    "SunnyPrintersERP-$Version.msi" = $MsiPath
    "SunnyPrintersERP-$Version.zip" = $ZipPath
    "SunnyPrinters-$Version.jar"    = $JarPath
    "SHA256.txt"                    = $ShaPath
}

$githubUrls = @{}

# Strip RFC 6570 URI template suffix robustly using regex
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

    # Delete existing asset with the same name to prevent 422 conflict
    if ($release.assets) {
        foreach ($asset in $release.assets) {
            if ($asset.name -eq $fileName) {
                Log-Message "Deleting existing asset '$fileName' from GitHub release..."
                try {
                    Invoke-RestMethod -Uri $asset.url -Method Delete -Headers $headers
                } catch {
                    Log-Message "Warning: Failed to delete existing asset '$fileName': $_"
                }
            }
        }
    }

    $uploadUrl = "${uploadTemplate}?name=${fileName}"
    Log-Message "Uploading '$fileName' to GitHub Release..."
    Log-Message " - Upload URL: $uploadUrl"
    Log-Message " - File Path:  $filePath"
    Log-Message " - File Size:  $((Get-Item $filePath).Length) bytes"

    $downloadUrl = Invoke-StreamingAssetUpload `
        -UploadUrl   $uploadUrl `
        -FileName    $fileName `
        -FilePath    $filePath `
        -Token       $GithubToken `
        -MaxRetries  3 `
        -TimeoutSec  900

    $githubUrls[$fileName] = $downloadUrl
    Log-Message "Uploaded '$fileName' successfully. URL: $downloadUrl"
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
# The storage_path now stores the GitHub Release download URL as per new architecture
$updatePayload = @{
    version = $Version
    minimum_supported_version = $Version # Default to current version as minimum
    storage_path = $msiDownloadUrl
    release_notes = $ReleaseNotes
    sha256 = $msiSha256
    file_name = "SunnyPrintersERP-$Version.msi"
    file_size = $msiSize
    published = $true
    mandatory = $Mandatory
    release_channel = $ReleaseChannel
    release_date = $nowUtc
    download_count = 0
    created_at = $nowUtc
    updated_at = $nowUtc
}

$payloadJson = ConvertTo-Json -InputObject $updatePayload -Depth 4
$upsertHeaders = @{
    "Authorization" = "Bearer $SupabaseKey"
    "apikey"        = $SupabaseKey
    "Content-Type"  = "application/json"
    "Prefer"        = "resolution=merge-duplicates,return=representation"
    "User-Agent"    = "SunnyPrinters-Release-Manager"
}

$dbUrl = "$SupabaseUrl/rest/v1/app_updates"

# --- Pre-insert Diagnostics ---
Log-Message "========================================================"
Log-Message "Posting release metadata to Supabase:"
Log-Message " - URL:              $dbUrl"
Log-Message " - Version:          $($updatePayload.version)"
Log-Message " - GitHub Release:   $githubReleaseUrl"
Log-Message " - Download URL:     $msiDownloadUrl"
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
