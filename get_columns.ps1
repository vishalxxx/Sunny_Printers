$secretsPath = "release_secrets.env"
$supabaseUrl = ""
$supabaseKey = ""

if (Test-Path $secretsPath) {
    Get-Content $secretsPath | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line -split "=", 2
            $key = $parts[0].Trim()
            $val = $parts[1].Trim()
            if ($val.StartsWith('"') -and $val.EndsWith('"')) { $val = $val.Substring(1, $val.Length - 2) }
            if ($val.StartsWith("'") -and $val.EndsWith("'")) { $val = $val.Substring(1, $val.Length - 2) }
            if ($key -eq "SUPABASE_URL") { $supabaseUrl = $val }
            if ($key -eq "SUPABASE_KEY") { $supabaseKey = $val }
        }
    }
}

if (-not $supabaseUrl) {
    $supabaseUrl = $env:SUPABASE_URL
    $supabaseKey = $env:SUPABASE_KEY
}

if ($supabaseUrl -and $supabaseKey) {
    $headers = @{
        "Authorization" = "Bearer $supabaseKey"
        "apikey"        = $supabaseKey
        "User-Agent"    = "SunnyPrinters-Release-Manager"
    }
    # Query with a SELECT * to see the columns returned
    $dbUrl = "$($supabaseUrl.TrimEnd('/'))/rest/v1/app_updates?limit=1"
    try {
        $res = Invoke-WebRequest -Uri $dbUrl -Headers $headers -Method Get -UseBasicParsing
        Write-Output "Status: $($res.StatusCode)"
        Write-Output "Body: $($res.Content)"
    } catch {
        Write-Output "Error querying: $_"
        if ($_.Exception.Response) {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            Write-Output "Error body: $($reader.ReadToEnd())"
        }
    }
} else {
    Write-Output "No credentials found."
}
