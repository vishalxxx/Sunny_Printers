# rollback.ps1 - Phase 13: Release Rollback (Clean up Production app_updates table and local artifacts)
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

Log-Message "Initiating rollback for version $Version..."

if (-not $Version) {
    Log-Message "Error: Version parameter is missing. Rollback aborted."
    return
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

# Delete DB Row in Production app_updates table
if ($SupabaseUrl -and $SupabaseKey) {
    $SupabaseUrl = $SupabaseUrl.Trim()
    if ($SupabaseUrl.EndsWith("/")) {
        $SupabaseUrl = $SupabaseUrl.Substring(0, $SupabaseUrl.Length - 1)
    }

    Log-Message "Deleting database row for version $Version in app_updates table on Production Supabase..."
    $dbUrl = "$SupabaseUrl/rest/v1/app_updates?version=eq.$Version&release_channel=eq.$ReleaseChannel"
    Log-Message " - Rollback DELETE URL: $dbUrl"
    $headers = @{
        "Authorization" = "Bearer $SupabaseKey"
        "apikey"        = $SupabaseKey
        "Content-Type"  = "application/json"
        "Prefer"        = "return=representation"
    }
    
    try {
        $rollbackResponse = Invoke-WebRequest -Uri $dbUrl -Method Delete -Headers $headers -UseBasicParsing
        Log-Message "Rollback HTTP Status: $($rollbackResponse.StatusCode)"
        Log-Message "Rollback Response Body: $($rollbackResponse.Content)"
        Log-Message "Database row deletion completed."
    } catch {
        $rbStatusCode = 0
        $rbErrorBody = ""
        try {
            $rbStatusCode = $_.Exception.Response.StatusCode.Value__
            $rbStream = $_.Exception.Response.GetResponseStream()
            $rbReader = New-Object System.IO.StreamReader($rbStream)
            $rbErrorBody = $rbReader.ReadToEnd()
            $rbReader.Close()
        } catch { }
        Log-Message "Warning: Database row deletion failed (HTTP $rbStatusCode): $rbErrorBody"
    }
} else {
    Log-Message "Warning: Supabase credentials not found. Skipping remote database rollback step."
}

# 2. Local cleanup
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
