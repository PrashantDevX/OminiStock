param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbUser = $env:DB_USER
)

$ErrorActionPreference = "Stop"

if (-not $DbUser) {
    $DbUser = "postgres"
}
if ($env:DB_PASSWORD) {
    $env:PGPASSWORD = $env:DB_PASSWORD
}

$psql = (Get-Command psql.exe -ErrorAction SilentlyContinue | Select-Object -First 1).Source
if (-not $psql) {
    $psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
}
if (-not (Test-Path $psql)) {
    throw "psql not found. Install PostgreSQL or add psql.exe to PATH."
}

$databases = @(
    "omnistock_auth",
    "omnistock_product",
    "omnistock_inventory",
    "omnistock_order"
)

foreach ($database in $databases) {
    $exists = [string](& $psql -h $DbHost -p $DbPort -U $DbUser -d postgres -tA `
        -v ON_ERROR_STOP=1 -c "SELECT 1 FROM pg_database WHERE datname = '$database';"
    )
    if ($LASTEXITCODE -ne 0) {
        throw "Could not check PostgreSQL database '$database'. Check PostgreSQL and your credentials."
    }

    if ([string]::IsNullOrWhiteSpace($exists)) {
        Write-Host "Creating database $database..." -ForegroundColor Cyan
        & $psql -h $DbHost -p $DbPort -U $DbUser -d postgres `
            -v ON_ERROR_STOP=1 -c "CREATE DATABASE $database;"
        if ($LASTEXITCODE -ne 0) {
            throw "Could not create PostgreSQL database '$database'."
        }
    } else {
        Write-Host "Database $database already exists." -ForegroundColor DarkGray
    }
}

$seedFile = Join-Path $PSScriptRoot "database\seed.sql"
Write-Host "Creating missing tables and inserting the review sample data..." -ForegroundColor Cyan
& $psql -h $DbHost -p $DbPort -U $DbUser -d postgres `
    -v ON_ERROR_STOP=1 -f $seedFile
if ($LASTEXITCODE -ne 0) {
    throw "Database setup failed while applying '$seedFile'."
}

Write-Host "OmniStock databases and sample data are ready." -ForegroundColor Green
Write-Host "Existing product, warehouse, inventory, and user records were preserved."
