# Phase 12 — tears down the full Trading Capture System stack (infrastructure + services).
# Usage:
#   .\scripts\full-stack-down.ps1           # stop and remove containers, keep volumes
#   .\scripts\full-stack-down.ps1 -Wipe     # also drop named volumes (fresh DB/Keycloak/topics)
param([switch]$Wipe)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$fileArgs = @("-f", "docker-compose.yml", "-f", "docker-compose.full.yml")
$downArgs = @("down", "--remove-orphans")
if ($Wipe) { $downArgs += "-v" }

# --profile ui so a containerized admin-web (if started) is torn down too.
docker compose @fileArgs --profile ui @downArgs
if ($LASTEXITCODE -ne 0) { throw "docker compose down failed." }
Write-Host "Full stack stopped." -ForegroundColor Green
