param(
    [switch]$SkipBuild,
    [switch]$SkipFrontend,
    [switch]$SingleInventoryInstance
)

# Starts the complete OmniStock stack for the review demo:
# Eureka, Auth, Gateway, Product, Inventory (2 instances) and Order.
# Logs and process ids are written to the logs folder.

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root "logs"
$pidFile = Join-Path $logDir "pids.json"

New-Item -ItemType Directory -Force -Path $logDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $logDir "archive") | Out-Null

# Stop a previous run if pids are still around
if (Test-Path $pidFile) {
    Write-Host "Existing pid file found - stopping previous processes first." -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot "stop-all.ps1")
    Start-Sleep -Seconds 2
}

if (-not $SkipBuild) {
    Write-Host "Building backend (tests skipped)..." -ForegroundColor Cyan
    & mvn -f (Join-Path $root "backend\pom.xml") -DskipTests package -B
    if ($LASTEXITCODE -ne 0) {
        throw "Backend build failed."
    }
}

function Get-ServiceJar([string]$project) {
    $jar = Get-ChildItem (Join-Path $root "backend\$project\target\*.jar") |
        Where-Object { $_.Name -notlike "*.original" } |
        Select-Object -First 1
    if (-not $jar) {
        throw "No jar found for $project. Build the backend first."
    }
    return $jar.FullName
}

$processes = @{}

function Start-ServiceJar {
    param(
        [string]$Name,
        [string]$Project,
        [string[]]$Arguments = @()
    )

    $jar = Get-ServiceJar $Project
    $argList = @("-jar", $jar) + $Arguments

    $process = Start-Process -FilePath "java" `
        -ArgumentList $argList `
        -RedirectStandardOutput (Join-Path $logDir "$Name.out.log") `
        -RedirectStandardError (Join-Path $logDir "$Name.err.log") `
        -WindowStyle Hidden `
        -PassThru

    $script:processes[$Name] = $process.Id
    Write-Host ("  started {0,-18} pid={1}" -f $Name, $process.Id) -ForegroundColor DarkGray
}

function Wait-Port {
    param([int]$Port, [int]$TimeoutSeconds = 90)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $client = New-Object System.Net.Sockets.TcpClient
        try {
            $client.Connect("127.0.0.1", $Port)
            $client.Close()
            return $true
        } catch {
            Start-Sleep -Milliseconds 800
        } finally {
            $client.Dispose()
        }
    }
    return $false
}

Write-Host ""
Write-Host "Starting service registry..." -ForegroundColor Cyan
Start-ServiceJar -Name "eureka-server" -Project "EUREKA-SERVER"
if (-not (Wait-Port -Port 8761 -TimeoutSeconds 90)) { throw "Eureka did not start on port 8761." }
Write-Host "  Eureka is ready at http://localhost:8761" -ForegroundColor Green
Start-Sleep -Seconds 2

Write-Host "Starting auth service and API gateway..." -ForegroundColor Cyan
Start-ServiceJar -Name "auth-service" -Project "AUTH-SERVICE"
Start-ServiceJar -Name "api-gateway" -Project "API-GATEWAY"
if (-not (Wait-Port -Port 8080 -TimeoutSeconds 90)) { throw "API Gateway did not start on port 8080." }

Write-Host "Starting domain services..." -ForegroundColor Cyan
Start-ServiceJar -Name "product-service" -Project "PRODUCT-SERVICE"
Start-ServiceJar -Name "inventory-service" -Project "INVENTORY-SERVICE"

if (-not $SingleInventoryInstance) {
    Start-ServiceJar -Name "inventory-service-2" -Project "INVENTORY-SERVICE" -Arguments @("--server.port=8093")
}

Start-ServiceJar -Name "order-service" -Project "ORDER-SERVICE"

if (-not (Wait-Port -Port 8082 -TimeoutSeconds 90)) { throw "Product service did not start on port 8082." }
if (-not (Wait-Port -Port 8083 -TimeoutSeconds 90)) { throw "Inventory service did not start on port 8083." }
if (-not (Wait-Port -Port 8084 -TimeoutSeconds 90)) { throw "Order service did not start on port 8084." }

if (-not $SkipFrontend) {
    $frontend = Join-Path $root "frontend"
    if (Test-Path (Join-Path $frontend "node_modules")) {
        Write-Host "Starting React frontend..." -ForegroundColor Cyan
        $npm = Start-Process -FilePath "cmd.exe" `
            -ArgumentList "/c", "npm run dev" `
            -WorkingDirectory $frontend `
            -RedirectStandardOutput (Join-Path $logDir "frontend.out.log") `
            -RedirectStandardError (Join-Path $logDir "frontend.err.log") `
            -WindowStyle Hidden `
            -PassThru
        $script:processes["frontend"] = $npm.Id
        Write-Host ("  started {0,-18} pid={1}" -f "frontend", $npm.Id) -ForegroundColor DarkGray
    } else {
        Write-Host "Frontend dependencies not installed - run 'npm install' in the frontend folder." -ForegroundColor Yellow
    }
}

$processes | ConvertTo-Json | Set-Content -Path $pidFile

Write-Host ""
Write-Host "OmniStock is running." -ForegroundColor Green
Write-Host "  Frontend        http://localhost:5173"
Write-Host "  API Gateway     http://localhost:8080"
Write-Host "  Eureka          http://localhost:8761"
Write-Host "  Auth login      POST http://localhost:8080/auth/login"
Write-Host ""
Write-Host "Register a STAFF account in the frontend. Assign ADMIN or MANAGER in the auth database when needed."
Write-Host "Run scripts\verify.ps1 for an end-to-end smoke test."
Write-Host "Logs: $logDir"
Write-Host "Stop with: scripts\stop-all.ps1"
