$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (Test-Path .local/env.ps1) { . ./.local/env.ps1 }
foreach ($name in @('TEST_DB_URL', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name is required; tests never fall back to demo or in-memory databases."
    }
}
& ./mvnw.cmd test
if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed.' }
Push-Location frontend
try {
    & npm.cmd run typecheck
    if ($LASTEXITCODE -ne 0) { throw 'Frontend typecheck failed.' }
    & npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw 'Browser tests failed. Start the same-origin demo jar before running this script.' }
} finally { Pop-Location }
