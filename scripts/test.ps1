param(
    [string]$MySqlHome = 'D:\MySQL\MySQL Server 8.0',
    [ValidateRange(1024, 65535)][int]$BrowserPort = 18081
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (Test-Path .local/env.ps1) { . ./.local/env.ps1 }
foreach ($name in @('TEST_DB_URL', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name is required; tests never fall back to demo or in-memory databases."
    }
}
$mysql = Join-Path $MySqlHome 'bin/mysql.exe'
if (!(Test-Path $mysql)) { throw 'MySQL CLI is required to remove only the ephemeral browser administrator.' }
$database = [Uri]$env:TEST_DB_URL.Substring(5)
if ($database.AbsolutePath -ne '/pc_mall_test' -or $env:TEST_DB_USERNAME -ne 'pc_mall_test') {
    throw 'Browser tests require the explicitly isolated pc_mall_test schema and account.'
}
if (Get-NetTCPConnection -State Listen -LocalPort $BrowserPort -ErrorAction SilentlyContinue) {
    throw "Browser test port $BrowserPort is occupied; refusing to reuse another service."
}
$savedEnvironment = @{}
foreach ($name in @('ADMIN_BOOTSTRAP_USERNAME', 'ADMIN_BOOTSTRAP_PASSWORD', 'BROWSER_ADMIN_USERNAME', 'BROWSER_ADMIN_PASSWORD', 'BROWSER_BASE_URL', 'MYSQL_PWD')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name)
}
$browserServer = $null
$administrator = $null
try {
    $env:ADMIN_BOOTSTRAP_USERNAME = $null
    $env:ADMIN_BOOTSTRAP_PASSWORD = $null
    & ./mvnw.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed.' }
    Push-Location frontend
    try {
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend typecheck/build failed.' }
    } finally { Pop-Location }
    & ./mvnw.cmd -Pdemo -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Browser test artifact packaging failed. Stop any process holding the target JAR.' }
    Copy-Item -LiteralPath target/pc-mall-0.1.0.jar -Destination .local/browser-test.jar -Force
    $administrator = 'webadm_' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $env:ADMIN_BOOTSTRAP_USERNAME = $env:BROWSER_ADMIN_USERNAME = $administrator
    $env:ADMIN_BOOTSTRAP_PASSWORD = $env:BROWSER_ADMIN_PASSWORD = [Guid]::NewGuid().ToString('N')
    $env:BROWSER_BASE_URL = "http://127.0.0.1:$BrowserPort"
    $browserServer = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList @(
        '-jar', '.local/browser-test.jar', '--spring.profiles.active=test', "--server.port=$BrowserPort"
    ) -WorkingDirectory $PWD -WindowStyle Hidden -RedirectStandardOutput .local/browser-test-server.log -RedirectStandardError .local/browser-test-server.stderr.log -PassThru
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($browserServer.HasExited) { throw 'Browser test server exited; inspect .local/browser-test-server.log.' }
        try {
            $response = Invoke-WebRequest "$env:BROWSER_BASE_URL/api/products" -TimeoutSec 1 -SkipHttpErrorCheck
            if ($response.StatusCode -eq 200) { $ready = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready) { throw 'Browser test server did not become ready.' }
    Push-Location frontend
    try {
        & npm.cmd test
        if ($LASTEXITCODE -ne 0) { throw 'Browser tests failed.' }
    } finally { Pop-Location }
} finally {
    try {
        if ($browserServer -and !$browserServer.HasExited) { Stop-Process -Id $browserServer.Id }
        if ($administrator) {
            $env:MYSQL_PWD = $env:TEST_DB_PASSWORD
            $databasePort = if ($database.Port -gt 0) { $database.Port } else { 3306 }
            & $mysql --connect-timeout=3 --protocol=TCP "--host=$($database.Host)" "--port=$databasePort" --user=pc_mall_test --database=pc_mall_test "--execute=DELETE FROM account WHERE username = '$administrator' AND role = 'ADMIN'"
            if ($LASTEXITCODE -ne 0) { throw 'Ephemeral administrator cleanup failed; inspect the test database before rerunning.' }
        }
    } finally {
        foreach ($name in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name]) }
    }
}
