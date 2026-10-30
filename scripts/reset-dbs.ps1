param(
    [string]$DbUser = "postgres",
    [string]$DbPassword = "root",
    [string]$DbHost = "localhost"
)

# Drops and recreates the four OmniStock databases so demo data is seeded fresh.
# Stop all services first (scripts\stop-all.ps1).

$ErrorActionPreference = "Stop"

$psql = (Get-Command psql -ErrorAction SilentlyContinue | Select-Object -First 1).Source
if (-not $psql) {
    $psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
}
if (-not (Test-Path $psql)) {
    throw "psql not found. Install PostgreSQL or pass a valid path in this script."
}

$env:PGPASSWORD = $DbPassword
$databases = @("omnistock_auth", "omnistock_product", "omnistock_inventory", "omnistock_order")

foreach ($db in $databases) {
    Write-Host "Recreating $db ..." -ForegroundColor Cyan

    & $psql -h $DbHost -U $DbUser -d postgres -v ON_ERROR_STOP=1 -c `
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$db' AND pid <> pg_backend_pid();" | Out-Null

    & $psql -h $DbHost -U $DbUser -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS $db;" | Out-Null
    & $psql -h $DbHost -U $DbUser -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $db;" | Out-Null
}

Write-Host ""
Write-Host "Databases recreated. Start the services to seed demo data." -ForegroundColor Green
