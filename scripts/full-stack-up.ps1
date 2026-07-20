# Phase 12 — brings up the FULL Trading Capture System stack (infrastructure + the four
# containerized services) from a single command, then waits until every container is healthy.
#
# Usage:
#   .\scripts\full-stack-up.ps1              # build images if needed + up + wait
#   .\scripts\full-stack-up.ps1 -Build       # force a rebuild of the service images
#   .\scripts\full-stack-up.ps1 -TimeoutSeconds 420
#
# Seeded demo data is applied automatically: each service's Flyway locations include
# classpath:db/seed (deterministic dev/demo rows). allocation-engine is Kafka-driven and has
# no seed rows; demo allocations are produced by the scenario harness.
#
# admin-web runs on the host for the UI E2E (Playwright webServer). To include a fully
# containerized admin-web demo, add:  --profile ui   (see docker-compose.full.yml).
param(
    [switch]$Build,
    [int]$TimeoutSeconds = 420
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$composeFiles = @("docker-compose.yml", "docker-compose.full.yml")
$fileArgs = @()
foreach ($f in $composeFiles) { $fileArgs += @("-f", $f) }

$upArgs = @("up", "-d")
if ($Build) { $upArgs += "--build" }

docker compose @fileArgs @upArgs
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed (is Docker Desktop running? did the images build?)" }

& (Join-Path $PSScriptRoot "wait-for-healthy.ps1") -ComposeFiles $composeFiles -TimeoutSeconds $TimeoutSeconds

Write-Host ""
Write-Host "Full stack is up. Service endpoints:" -ForegroundColor Green
Write-Host "  trading-service     http://localhost:8081"
Write-Host "  account-service     http://localhost:8082"
Write-Host "  enrichment-service  http://localhost:8083"
Write-Host "  allocation-engine   http://localhost:8084 (actuator only; Kafka-driven)"
Write-Host "  Keycloak            http://localhost:8180 (realm: tcs)"
Write-Host "  Prometheus          http://localhost:9090   Grafana http://localhost:3001"
Write-Host ""
Write-Host "Next: run the scenarios — .\scripts\run-scenarios.ps1" -ForegroundColor Cyan
