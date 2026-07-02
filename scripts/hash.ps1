# hash.ps1 - Phase 7 & 8: Hash Generation & Release Notes
param(
    [string]$Version,
    [string]$MsiPath,
    [string]$ZipPath,
    [string]$JarPath,
    [string]$ReleaseNotesPath = "release_notes.md",
    [string]$OutputDir
)

$ErrorActionPreference = "Stop"

function Log-Message($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Output "[$timestamp] [HASH] $msg"
}

# Helper to calculate hashes
function Get-FileHashString($path, $algorithm) {
    if (-not (Test-Path $path)) { return "" }
    $hashObj = Get-FileHash -Path $path -Algorithm $algorithm
    return $hashObj.Hash.ToLower()
}

# Helper to parse release notes
function Get-ReleaseNotesForVersion($filePath, $ver) {
    if (-not (Test-Path $filePath)) {
        return "No release notes available (file missing)."
    }
    
    $lines = Get-Content $filePath
    $notes = [System.Text.StringBuilder]::new()
    $recording = $false
    
    # Matches patterns like ## [1.0.1] or ## 1.0.1
    $headerPattern = "^##\s+\[?" + [regex]::Escape($ver) + "\]?.*$"
    
    foreach ($line in $lines) {
        if ($line -match $headerPattern) {
            $recording = $true
            continue
        }
        if ($recording) {
            # Stop if we hit the next version header (## [any version] or ## any version)
            if ($line -match "^##\s+.*$") {
                break
            }
            [void]$notes.AppendLine($line)
        }
    }
    
    $result = $notes.ToString().Trim()
    if ($result -eq "") {
        return "No release notes specified for version $ver in $filePath"
    }
    return $result
}

if (-not $Version) { throw "Version parameter is required." }
if (-not $OutputDir) { throw "OutputDir parameter is required." }

# Create output folder if it doesn't exist
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

Log-Message "Generating hashes and metadata for version $Version..."

# Retrieve git details
$gitCommit = "unknown"
try {
    $gitCommit = (git rev-parse HEAD 2>$null).Trim()
} catch {}

$buildDate = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$buildNumber = Get-Date -Format "yyyyMMdd.HHmmss"

# Calculate hashes and sizes
$msiSize = 0
$msiSha256 = ""
$msiMd5 = ""
if ($MsiPath -and (Test-Path $MsiPath)) {
    $msiSize = (Get-Item $MsiPath).Length
    $msiSha256 = Get-FileHashString $MsiPath "SHA256"
    $msiMd5 = Get-FileHashString $MsiPath "MD5"
    Log-Message "MSI Size: $msiSize bytes, SHA-256: $msiSha256"
} else {
    Log-Message "Warning: MSI path not found: $MsiPath"
}

$zipSize = 0
$zipSha256 = ""
$zipMd5 = ""
if ($ZipPath -and (Test-Path $ZipPath)) {
    $zipSize = (Get-Item $ZipPath).Length
    $zipSha256 = Get-FileHashString $ZipPath "SHA256"
    $zipMd5 = Get-FileHashString $ZipPath "MD5"
    Log-Message "ZIP Size: $zipSize bytes, SHA-256: $zipSha256"
} else {
    Log-Message "Warning: ZIP path not found: $ZipPath"
}

$jarSize = 0
$jarSha256 = ""
$jarMd5 = ""
if ($JarPath -and (Test-Path $JarPath)) {
    $jarSize = (Get-Item $JarPath).Length
    $jarSha256 = Get-FileHashString $JarPath "SHA256"
    $jarMd5 = Get-FileHashString $JarPath "MD5"
    Log-Message "JAR Size: $jarSize bytes, SHA-256: $jarSha256"
} else {
    Log-Message "Warning: JAR path not found: $JarPath"
}

# Parse release notes
$notesText = Get-ReleaseNotesForVersion $ReleaseNotesPath $Version
Log-Message "Extracted release notes successfully."

# Construct release.json content
$jsonObj = @{
    version = $Version
    buildDate = $buildDate
    buildNumber = $buildNumber
    gitCommit = $gitCommit
    releaseNotes = $notesText
    artifacts = @{
        msi = @{
            fileName = if ($MsiPath) { Split-Path $MsiPath -Leaf } else { "" }
            fileSize = $msiSize
            sha256 = $msiSha256
            md5 = $msiMd5
        }
        zip = @{
            fileName = if ($ZipPath) { Split-Path $ZipPath -Leaf } else { "" }
            fileSize = $zipSize
            sha256 = $zipSha256
            md5 = $zipMd5
        }
        jar = @{
            fileName = if ($JarPath) { Split-Path $JarPath -Leaf } else { "" }
            fileSize = $jarSize
            sha256 = $jarSha256
            md5 = $jarMd5
        }
    }
}
$jsonString = ConvertTo-Json -InputObject $jsonObj -Depth 4
$jsonPath = Join-Path $OutputDir "release.json"
Set-Content -Path $jsonPath -Value $jsonString
Log-Message "Created $jsonPath"

# Construct release.xml content
$xmlContent = @"
<?xml version="1.0" encoding="UTF-8"?>
<release>
    <version>$Version</version>
    <buildDate>$buildDate</buildDate>
    <buildNumber>$buildNumber</buildNumber>
    <gitCommit>$gitCommit</gitCommit>
    <releaseNotes><![CDATA[$notesText]]></releaseNotes>
    <artifacts>
        <msi>
            <fileName>$(if ($MsiPath) { Split-Path $MsiPath -Leaf } else { "" })</fileName>
            <fileSize>$msiSize</fileSize>
            <sha256>$msiSha256</sha256>
            <md5>$msiMd5</md5>
        </msi>
        <zip>
            <fileName>$(if ($ZipPath) { Split-Path $ZipPath -Leaf } else { "" })</fileName>
            <fileSize>$zipSize</fileSize>
            <sha256>$zipSha256</sha256>
            <md5>$zipMd5</md5>
        </zip>
        <jar>
            <fileName>$(if ($JarPath) { Split-Path $JarPath -Leaf } else { "" })</fileName>
            <fileSize>$jarSize</fileSize>
            <sha256>$jarSha256</sha256>
            <md5>$jarMd5</md5>
        </jar>
    </artifacts>
</release>
"@
$xmlPath = Join-Path $OutputDir "release.xml"
Set-Content -Path $xmlPath -Value $xmlContent
Log-Message "Created $xmlPath"

# Construct release.md content
$mdContent = @"
# Release Metadata - Version $Version

- **Build Date:** $buildDate
- **Build Number:** $buildNumber
- **Git Commit:** $gitCommit

## Artifacts

### MSI Installer
- **File Name:** $(if ($MsiPath) { Split-Path $MsiPath -Leaf } else { "N/A" })
- **File Size:** $msiSize bytes
- **SHA-256:** `$msiSha256`
- **MD5:** `$msiMd5`

### Portable ZIP
- **File Name:** $(if ($ZipPath) { Split-Path $ZipPath -Leaf } else { "N/A" })
- **File Size:** $zipSize bytes
- **SHA-256:** `$zipSha256`
- **MD5:** `$zipMd5`

### Standalone JAR
- **File Name:** $(if ($JarPath) { Split-Path $JarPath -Leaf } else { "N/A" })
- **File Size:** $jarSize bytes
- **SHA-256:** `$jarSha256`
- **MD5:** `$jarMd5`

## Release Notes
$notesText
"@
$mdPath = Join-Path $OutputDir "release.md"
Set-Content -Path $mdPath -Value $mdContent
Log-Message "Created $mdPath"

# Write SHA-256 txt files
if ($msiSha256) {
    Set-Content -Path (Join-Path $OutputDir "SHA256_msi.txt") -Value $msiSha256
}
if ($zipSha256) {
    Set-Content -Path (Join-Path $OutputDir "SHA256_zip.txt") -Value $zipSha256
}
if ($jarSha256) {
    Set-Content -Path (Join-Path $OutputDir "SHA256_jar.txt") -Value $jarSha256
}

# Create a combined SHA256.txt file
$combinedContent = ""
if ($msiSha256) { $combinedContent += "$msiSha256  $(Split-Path $MsiPath -Leaf)`r`n" }
if ($zipSha256) { $combinedContent += "$zipSha256  $(Split-Path $ZipPath -Leaf)`r`n" }
if ($jarSha256) { $combinedContent += "$jarSha256  $(Split-Path $JarPath -Leaf)`r`n" }
Set-Content -Path (Join-Path $OutputDir "SHA256.txt") -Value $combinedContent -NoNewline
Log-Message "Created SHA256.txt combined checksum file."

Log-Message "Hash generation complete."
