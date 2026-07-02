# version.ps1 - Phase 2: Version Management
param(
    [string]$Increment = "none", # patch, minor, major, none
    [string]$ManualVersion = ""
)

$ErrorActionPreference = "Stop"

function Log-Message($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [VERSION] $msg"
}

$propPath = Join-Path $PSScriptRoot "..\src\main\resources\version.properties"
$pomPath = Join-Path $PSScriptRoot "..\pom.xml"

if (-not (Test-Path $propPath)) {
    throw "version.properties file not found at $propPath"
}

# 1. Read current version
$propContent = Get-Content $propPath -Raw
$versionLine = $propContent -split "`n" | Where-Object { $_ -like "version=*" }
if (-not $versionLine) {
    throw "Could not parse version from version.properties"
}

$currentVersion = ($versionLine -split "=")[1].Trim()
Log-Message "Current version: $currentVersion"

# 2. Determine new version
$newVersion = $currentVersion
if ($ManualVersion -ne "") {
    if ($ManualVersion -notmatch "^\d+\.\d+\.\d+$") {
        throw "Invalid manual version format '$ManualVersion'. Must be X.Y.Z"
    }
    $newVersion = $ManualVersion
    Log-Message "Using manual version override: $newVersion"
} elseif ($Increment -ne "none") {
    $parts = $currentVersion -split "\."
    if ($parts.Count -lt 3) {
        throw "Invalid current version format '$currentVersion'. Expected X.Y.Z"
    }
    
    [int]$major = $parts[0]
    [int]$minor = $parts[1]
    [int]$patch = $parts[2]

    switch ($Increment.ToLower()) {
        "major" {
            $major++
            $minor = 0
            $patch = 0
        }
        "minor" {
            $minor++
            $patch = 0
        }
        "patch" {
            $patch++
        }
        default {
            throw "Invalid increment type '$Increment'. Must be patch, minor, or major."
        }
    }
    $newVersion = "$major.$minor.$patch"
    Log-Message "Incremented version ($Increment): $currentVersion -> $newVersion"
} else {
    Log-Message "No version increment requested. Version remains: $newVersion"
}

# 3. Write back to version.properties
$newPropContent = "version=$newVersion`r`n"
Set-Content -Path $propPath -Value $newPropContent -NoNewline
Log-Message "Updated version.properties to version=$newVersion"

# 4. Synchronize pom.xml
if (Test-Path $pomPath) {
    [xml]$pom = Get-Content $pomPath
    $oldPomVersion = $pom.project.version
    $pom.project.version = $newVersion
    $pom.Save($pomPath)
    Log-Message "Updated pom.xml version from $oldPomVersion to $newVersion"
} else {
    Log-Message "Warning: pom.xml not found. Skipping Maven synchronization."
}

# Return the new version to caller
Write-Output $newVersion
