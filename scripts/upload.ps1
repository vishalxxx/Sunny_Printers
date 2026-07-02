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
$uploadTemplate = $release.upload_url.Replace("{?name,label}", "")

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
    
    # Upload the asset
    Log-Message "Uploading '$fileName' to GitHub Release..."
    $uploadUrl = "$uploadTemplate?name=$fileName"
    
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
        throw "Failed to upload asset '$fileName' to GitHub Release: $_"
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
    "Prefer"        = "resolution=merge-duplicates"
}

$dbUrl = "$SupabaseUrl/rest/v1/app_updates"
try {
    $dbResponse = Invoke-RestMethod -Uri $dbUrl -Method Post -Headers $upsertHeaders -Body $payloadJson
    Log-Message "Database upsert successful. Update metadata row registered in Production app_updates table."
} catch {
    throw "Database upsert failed: $_"
}

Log-Message "GitHub Release and metadata publishing complete."
