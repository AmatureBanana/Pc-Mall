$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
Push-Location frontend
try {
    & npm.cmd ci --no-audit --no-fund
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed. Stop any running Vite process first.' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }
& ./mvnw.cmd -Pdemo -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Backend packaging failed.' }
Write-Host 'Built target/pc-mall-0.1.0.jar. Tests are run separately by scripts/test.ps1.'
