# package.ps1 - Phase 5 & 6: Compilation, WiX Bootstrapping & jpackage Packaging
param(
    [string]$Version
)

$ErrorActionPreference = "Stop"

function Log-Message($msg) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [PACKAGE] $msg"
}

if (-not $Version) {
    throw "Version parameter is required."
}

Log-Message "Executing decoupled local compilation and packaging for version $Version..."

# 1. WiX Toolset Bootstrapper
$wixDir = Join-Path $env:USERPROFILE ".wix"
$candlePath = Join-Path $wixDir "candle.exe"

if (-not (Get-Command candle -ErrorAction SilentlyContinue)) {
    if (Test-Path $candlePath) {
        Log-Message "Detected WiX Toolset at local path: $wixDir"
        $env:PATH = "$wixDir;" + $env:PATH
    } else {
        Log-Message "WiX Toolset not found in PATH or $wixDir. Bootstrapping WiX v3 binaries..."
        
        # Ensure directory exists
        if (-not (Test-Path $wixDir)) {
            New-Item -ItemType Directory -Path $wixDir -Force | Out-Null
        }
        
        # Download WiX v3 binaries zip
        $wixZipPath = Join-Path $env:TEMP "wix311-binaries.zip"
        if (-not (Test-Path $wixZipPath)) {
            $wixUrl = "https://github.com/wixtoolset/wix3/releases/download/wix3112rtm/wix311-binaries.zip"
            Log-Message "Downloading WiX binaries from $wixUrl to $wixZipPath..."
            [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -Uri $wixUrl -OutFile $wixZipPath
        }
        
        Log-Message "Extracting WiX binaries to $wixDir..."
        Expand-Archive -Path $wixZipPath -DestinationPath $wixDir -Force
        
        # Add to PATH
        $env:PATH = "$wixDir;" + $env:PATH
        Log-Message "WiX Toolset bootstrapped and added to process PATH."
    }
} else {
    Log-Message "WiX Toolset is already available in the global PATH."
}

# Double check that WiX is now available
if (-not (Get-Command candle -ErrorAction SilentlyContinue)) {
    throw "WiX Toolset verification failed. candle.exe could not be resolved."
}

# 2. Build the project using Maven
Log-Message "Building and compiling jar using Maven..."
$mavenCmd = "mvn clean package -DskipTests"
Invoke-Expression $mavenCmd | Out-Host

# 3. Create a clean packaging input directory to exclude source code, test classes, database, logs, etc.
$packageInput = Join-Path $PSScriptRoot "..\target\package-input"
if (Test-Path $packageInput) {
    Remove-Item -Path $packageInput -Recurse -Force | Out-Null
}
New-Item -ItemType Directory -Path $packageInput -Force | Out-Null

# Copy main built jar
$jarName = "Sunny_Printers-$Version.jar"
$builtJarPath = Join-Path $PSScriptRoot "..\target\$jarName"
if (-not (Test-Path $builtJarPath)) {
    throw "Built JAR not found at $builtJarPath. Ensure pom.xml version matches $Version"
}
Copy-Item -Path $builtJarPath -Destination (Join-Path $packageInput $jarName)

# Copy libs folder
$libsSource = Join-Path $PSScriptRoot "..\target\libs"
$libsDest = Join-Path $packageInput "libs"
if (Test-Path $libsSource) {
    Copy-Item -Path $libsSource -Destination $libsDest -Recurse
} else {
    Log-Message "Warning: target/libs directory not found."
}

# 4. Create App Image and MSI Installer using jpackage
Log-Message "Packaging App Image and MSI Installer using jpackage..."
$iconPath = Join-Path $PSScriptRoot "..\src\main\resources\images\app_icon.ico"
$distDir = Join-Path $PSScriptRoot "..\target\dist"

if (-not (Test-Path $distDir)) {
    New-Item -ItemType Directory -Path $distDir -Force | Out-Null
}

# Build App Image
Log-Message "Building App Image..."
jpackage --type app-image `
         --dest $distDir `
         --name "Sunny Printers ERP" `
         --input $packageInput `
         --main-jar $jarName `
         --main-class sunnyprinters.Launcher `
         --app-version $Version `
         --vendor "Sunny Printers" `
         --icon $iconPath | Out-Host

# Build MSI Installer
# Upgrade UUID fixed to prevent duplicate installations on Windows
$upgradeUuid = "e3c1a8d0-23a5-48b1-a068-07d0f1b2c3d4"
Log-Message "Building MSI Installer..."
jpackage --type msi `
         --dest $distDir `
         --name "Sunny Printers ERP" `
         --input $packageInput `
         --main-jar $jarName `
         --main-class sunnyprinters.Launcher `
         --app-version $Version `
         --vendor "Sunny Printers" `
         --icon $iconPath `
         --win-upgrade-uuid $upgradeUuid `
         --win-shortcut `
         --win-menu `
         --win-menu-group "Sunny Printers" `
         --win-dir-chooser | Out-Host

# 5. Compress App Image into Portable ZIP
$appImageFolder = Join-Path $distDir "Sunny Printers ERP"
$zipOutPath = Join-Path $distDir "SunnyPrintersERP-$Version.zip"

if (Test-Path $appImageFolder) {
    Log-Message "Creating portable ZIP archive..."
    Compress-Archive -Path $appImageFolder -DestinationPath $zipOutPath -Force | Out-Host
    Log-Message "Created portable ZIP: $zipOutPath"
} else {
    throw "App Image folder not found at $appImageFolder"
}

$msiFile = Join-Path $distDir "Sunny Printers ERP-$Version.msi"
if (-not (Test-Path $msiFile)) {
    # Sometimes jpackage names the msi slightly differently, let's find it.
    $msiSearch = Get-ChildItem $distDir -Filter "*.msi" | Select-Object -First 1
    if ($msiSearch) {
        $msiFile = $msiSearch.FullName
    } else {
        throw "MSI Installer was not created by jpackage."
    }
}

# Copy standalone JAR to dist directory
$jarFileName = "SunnyPrinters-$Version.jar"
$jarDestPath = Join-Path $distDir $jarFileName
Log-Message "Copying standalone JAR to dist folder: $jarDestPath"
Copy-Item -Path $builtJarPath -Destination $jarDestPath -Force

Log-Message "Packaging complete. MSI: $msiFile, ZIP: $zipOutPath, JAR: $jarDestPath"

# Return the paths of the packaged artifacts
return @($msiFile, $zipOutPath, $jarDestPath)
