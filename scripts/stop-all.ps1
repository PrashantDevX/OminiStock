$ErrorActionPreference = "SilentlyContinue"

$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $root "logs\pids.json"

if (-not (Test-Path $pidFile)) {
    Write-Host "No pids.json found - nothing recorded to stop." -ForegroundColor Yellow
    return
}

$processes = Get-Content $pidFile -Raw | ConvertFrom-Json

foreach ($property in $processes.PSObject.Properties) {
    $name = $property.Name
    $processId = [int]$property.Value
    $existing = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "Stopping $name (pid $processId)..." -ForegroundColor Cyan
        & taskkill /PID $processId /T /F | Out-Null
    } else {
        Write-Host "$name (pid $processId) is not running." -ForegroundColor DarkGray
    }
}

Remove-Item $pidFile -Force
Write-Host "All recorded OmniStock processes stopped." -ForegroundColor Green
