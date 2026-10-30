$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (Test-Path .local/env.ps1) { . ./.local/env.ps1 }
& ./mvnw.cmd spring-boot:run
if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed.' }
