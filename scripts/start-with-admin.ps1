param([Parameter(Mandatory)][string]$Username)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (Test-Path .local/env.ps1) { . ./.local/env.ps1 }
if (!(Test-Path target/pc-mall-0.1.0.jar)) { throw 'Build with scripts/package.ps1 first.' }
$secret = Read-Host 'Initial administrator password (8-64 characters, at most 72 UTF-8 bytes)' -AsSecureString
$previousUsername = $env:ADMIN_BOOTSTRAP_USERNAME
$previousPassword = $env:ADMIN_BOOTSTRAP_PASSWORD
try {
    $env:ADMIN_BOOTSTRAP_USERNAME = $Username
    $env:ADMIN_BOOTSTRAP_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
    & "$env:JAVA_HOME\bin\java.exe" -jar target/pc-mall-0.1.0.jar
    if ($LASTEXITCODE -ne 0) { throw 'Application startup failed; check the local database and initialization settings.' }
} finally {
    $env:ADMIN_BOOTSTRAP_USERNAME = $previousUsername
    $env:ADMIN_BOOTSTRAP_PASSWORD = $previousPassword
    $secret.Dispose()
}
