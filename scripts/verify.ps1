param(
    [string]$GatewayUrl = "http://localhost:8080",
    [int]$StartupTimeoutSeconds = 120,
    [string]$AdminUsername = $env:OMNISTOCK_ADMIN_USERNAME,
    [string]$AdminPassword = $env:OMNISTOCK_ADMIN_PASSWORD
)

$ErrorActionPreference = "Stop"
$script:passed = 0
$script:failed = 0
$script:failures = @()
$GatewayUrl = $GatewayUrl.TrimEnd("/")

function Check {
    param([string]$Name, [bool]$Condition, [string]$Detail = "")

    if ($Condition) {
        $script:passed++
        Write-Host "  [PASS] $Name" -ForegroundColor Green
    } else {
        $script:failed++
        $script:failures += $Name
        $suffix = if ($Detail) { " -> $Detail" } else { "" }
        Write-Host "  [FAIL] $Name$suffix" -ForegroundColor Red
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token = ""
    )

    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    try {
        $response = Invoke-WebRequest `
            -Method $Method `
            -Uri "$GatewayUrl$Path" `
            -Headers $headers `
            -UseBasicParsing
        $text = $response.Content
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            Body   = $(if ($text) { $text | ConvertFrom-Json } else { $null })
            Raw    = $text
        }
    } catch {
        $webResponse = $_.Exception.Response
        if ($null -eq $webResponse) {
            throw
        }

        $reader = New-Object System.IO.StreamReader($webResponse.GetResponseStream())
        $text = $reader.ReadToEnd()
        $reader.Dispose()
        return [pscustomobject]@{
            Status = [int]$webResponse.StatusCode
            Body   = $(if ($text) { try { $text | ConvertFrom-Json } catch { $null } } else { $null })
            Raw    = $text
        }
    }
}

function Login {
    param([string]$Username, [string]$Password)

    $body = @{
        username = $Username
        password = $Password
    } | ConvertTo-Json -Compress

    $response = Invoke-WebRequest `
        -Method POST `
        -Uri "$GatewayUrl/auth/login" `
        -ContentType "application/json" `
        -Body $body `
        -UseBasicParsing
    return ($response.Content | ConvertFrom-Json)
}

if ([string]::IsNullOrWhiteSpace($AdminUsername)) {
    $AdminUsername = Read-Host "Administrator or manager username"
}
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    $securePassword = Read-Host "Password" -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $AdminPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
}

Write-Host ""
Write-Host "OmniStock read-only smoke test" -ForegroundColor Cyan
Write-Host "Gateway: $GatewayUrl"
Write-Host ""

$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
$auth = $null
while ((Get-Date) -lt $deadline) {
    try {
        $auth = Login -Username $AdminUsername -Password $AdminPassword
        break
    } catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) {
            break
        }
        Start-Sleep -Seconds 3
    }
}

if ($null -eq $auth) {
    Write-Host "Login failed or the Gateway is unavailable. Check the credentials and start the services." -ForegroundColor Red
    exit 1
}

Check "login returns a JWT" ([bool]$auth.token)
Check "account can manage the catalog" ($auth.user.role -in @("ADMIN", "MANAGER")) $auth.user.role
$token = $auth.token

if (-not $token) {
    Write-Host "The login response did not contain a token." -ForegroundColor Red
    exit 1
}

$profile = Invoke-Api -Method GET -Path "/auth/me" -Token $token
Check "profile matches the signed-in user" (
    $profile.Status -eq 200 -and
    $profile.Body.username -eq $auth.user.username
) $profile.Raw

$unauthenticated = Invoke-Api -Method GET -Path "/products"
Check "protected routes reject missing credentials" ($unauthenticated.Status -eq 401)

$invalidToken = Invoke-Api -Method GET -Path "/products" -Token "invalid-token"
Check "protected routes reject invalid credentials" ($invalidToken.Status -eq 401)

foreach ($path in @("/products", "/inventory", "/warehouses", "/orders", "/inventory/low-stock")) {
    $response = Invoke-Api -Method GET -Path $path -Token $token
    Check "$path responds with a list" (
        $response.Status -eq 200 -and
        $response.Raw.TrimStart().StartsWith("[")
    ) "status $($response.Status)"
}

$probePorts = @()
for ($i = 0; $i -lt 6; $i++) {
    $instance = Invoke-Api -Method GET -Path "/inventory/instance" -Token $token
    if ($instance.Status -eq 200) {
        $probePorts += [string]$instance.Body.instance
    }
}
$instances = @($probePorts | Sort-Object -Unique)
Check "load balancer returns an inventory instance" ($probePorts.Count -eq 6)

$secondInstanceRunning = Test-NetConnection `
    -ComputerName 127.0.0.1 `
    -Port 8093 `
    -InformationLevel Quiet `
    -WarningAction SilentlyContinue
if ($secondInstanceRunning) {
    Check "load balancer reaches both inventory instances" ($instances.Count -ge 2) ($instances -join ", ")
} else {
    Write-Host "  [SKIP] Second Inventory instance is not running." -ForegroundColor Yellow
}

Write-Host ""
Write-Host ("{0} passed, {1} failed" -f $script:passed, $script:failed) `
    -ForegroundColor $(if ($script:failed -eq 0) { "Green" } else { "Red" })

if ($script:failed -gt 0) {
    Write-Host "Failed checks:" -ForegroundColor Red
    $script:failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    exit 1
}

Write-Host "Smoke test complete; no PostgreSQL data was changed." -ForegroundColor Green
exit 0
