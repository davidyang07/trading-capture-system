# Phase 12 — runs the 16 full-system scenarios against the running full stack.
#   Pipeline scenarios: the full-system-tests JUnit module (FULL_STACK=1) over REST + Kafka.
#   UI scenarios:       the Playwright *.full.spec.ts specs (E2E_FULL_STACK=1) + admin-web.
#
# Prerequisite: the stack is up — .\scripts\full-stack-up.ps1 -Build
# Usage:
#   .\scripts\run-scenarios.ps1              # pipeline + UI
#   .\scripts\run-scenarios.ps1 -PipelineOnly
#   .\scripts\run-scenarios.ps1 -UiOnly
param(
    [switch]$PipelineOnly,
    [switch]$UiOnly
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$failures = @()

if (-not $UiOnly) {
    Write-Host "== Pipeline scenarios (full-system-tests, REST + Kafka) ==" -ForegroundColor Cyan
    $env:FULL_STACK = "1"
    & .\mvnw.cmd -B -ntp -pl tests/full-system-tests test
    if ($LASTEXITCODE -ne 0) { $failures += "pipeline (full-system-tests)" }
    Remove-Item Env:\FULL_STACK
}

if (-not $PipelineOnly) {
    Write-Host "== UI scenarios (Playwright *.full.spec.ts + admin-web) ==" -ForegroundColor Cyan
    $env:E2E_FULL_STACK = "1"
    Push-Location apps/admin-web
    corepack pnpm exec playwright test
    if ($LASTEXITCODE -ne 0) { $failures += "ui (Playwright)" }
    Pop-Location
    Remove-Item Env:\E2E_FULL_STACK
}

if ($failures.Count -gt 0) {
    throw "Scenario failures in: $($failures -join ', ')"
}
Write-Host "All requested scenarios passed." -ForegroundColor Green
