param(
    [ValidateSet('start', 'stop')][string]$Action = 'start',
    [string]$MySqlHome = 'D:\MySQL\MySQL Server 8.0',
    [int]$Port = 33307
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$local = Join-Path $workspace '.local'
$data = Join-Path $local 'mysql-data'
$config = Join-Path $local 'mysql.ini'
$envFile = Join-Path $local 'env.ps1'
$readyFile = Join-Path $local 'mysql-ready'
$mysql = Join-Path $MySqlHome 'bin/mysql.exe'
$mysqld = Join-Path $MySqlHome 'bin/mysqld.exe'
if (!(Test-Path $mysqld)) { throw "MySQL 8 executable not found: $mysqld" }
if ($Action -eq 'start' -and (Test-Path $envFile)) {
    if (!(Test-Path $readyFile) -or !(Test-Path $config) -or !(Test-Path (Join-Path $data 'auto.cnf'))) {
        throw 'Incomplete MySQL initialization; refusing to start a partial instance. Inspect .local/mysql-error.log and the local files before recovery.'
    }
}
if ($Action -eq 'stop') {
    if (!(Test-Path $envFile)) { throw 'Run local-db.ps1 start first.' }
    . $envFile
    $env:MYSQL_PWD = $env:LOCAL_DB_ROOT_PASSWORD
    try {
        & (Join-Path $MySqlHome 'bin/mysqladmin.exe') --connect-timeout=3 --protocol=TCP --host=127.0.0.1 "--port=$env:LOCAL_DB_PORT" --user=root shutdown
        if ($LASTEXITCODE -ne 0) { throw 'MySQL shutdown failed.' }
    } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    exit
}
New-Item -ItemType Directory -Force $local | Out-Null
if (!(Test-Path $envFile)) {
    if (Test-Path $data) { throw 'Existing data directory without credentials; refusing to reinitialize.' }
    $demoPassword = [Guid]::NewGuid().ToString('N')
    $testPassword = [Guid]::NewGuid().ToString('N')
    $rootPassword = [Guid]::NewGuid().ToString('N')
    @"
`$env:DB_URL = 'jdbc:mysql://127.0.0.1:$Port/pc_mall_demo?characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai'
`$env:DB_USERNAME = 'pc_mall_demo'
`$env:DB_PASSWORD = '$demoPassword'
`$env:TEST_DB_URL = 'jdbc:mysql://127.0.0.1:$Port/pc_mall_test?characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai'
`$env:TEST_DB_USERNAME = 'pc_mall_test'
`$env:TEST_DB_PASSWORD = '$testPassword'
`$env:LOCAL_DB_ROOT_PASSWORD = '$rootPassword'
`$env:LOCAL_DB_PORT = '$Port'
"@ | Set-Content -LiteralPath $envFile -Encoding utf8
    $basePath = $MySqlHome.Replace('\', '/')
    $dataPath = $data.Replace('\', '/')
    @"
[mysqld]
basedir="$basePath"
datadir="$dataPath"
port=$Port
bind-address=127.0.0.1
mysqlx=OFF
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
log-error="$($local.Replace('\', '/'))/mysql-error.log"
"@ | Set-Content -LiteralPath $config -Encoding ascii
    & $mysqld "--defaults-file=$config" --initialize-insecure
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed; inspect .local/mysql-error.log.' }
    @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';
CREATE DATABASE pc_mall_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE pc_mall_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'pc_mall_demo'@'localhost' IDENTIFIED BY '$demoPassword';
CREATE USER 'pc_mall_test'@'localhost' IDENTIFIED BY '$testPassword';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON pc_mall_demo.* TO 'pc_mall_demo'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON pc_mall_test.* TO 'pc_mall_test'@'localhost';
"@ | Set-Content -LiteralPath (Join-Path $local 'bootstrap.sql') -Encoding ascii
}
. $envFile
function Test-ProjectDatabases {
    foreach ($account in @(
        @{ Name = 'pc_mall_demo'; Password = $env:DB_PASSWORD },
        @{ Name = 'pc_mall_test'; Password = $env:TEST_DB_PASSWORD }
    )) {
        $env:MYSQL_PWD = $account.Password
        & $mysql --connect-timeout=3 --protocol=TCP --host=127.0.0.1 "--port=$env:LOCAL_DB_PORT" "--user=$($account.Name)" "--database=$($account.Name)" --execute='SELECT 1' *> $null
        if ($LASTEXITCODE -ne 0) { return $false }
    }
    return $true
}
try {
    if (Test-ProjectDatabases) { Write-Host "Project MySQL already available on 127.0.0.1:$env:LOCAL_DB_PORT"; exit }
    $arguments = @("`"--defaults-file=$config`"")
    $bootstrap = Join-Path $local 'bootstrap.sql'
    if (Test-Path $bootstrap) { $arguments += "`"--init-file=$bootstrap`"" }
    $process = Start-Process -FilePath $mysqld -ArgumentList $arguments -WindowStyle Hidden -PassThru
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($process.HasExited) { throw 'MySQL exited; inspect .local/mysql-error.log.' }
        Start-Sleep -Milliseconds 500
        if (Test-ProjectDatabases) {
            if (Test-Path $bootstrap) { Remove-Item -LiteralPath $bootstrap }
            Set-Content -LiteralPath $readyFile -Value 'initialized-and-verified' -Encoding ascii
            Write-Host "Project MySQL ready on 127.0.0.1:$env:LOCAL_DB_PORT; credentials: .local/env.ps1 (ignored)"
            exit
        }
    }
    throw 'MySQL startup timed out; inspect .local/mysql-error.log.'
} finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
