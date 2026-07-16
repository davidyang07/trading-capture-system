# Starts the local infrastructure stack and waits until every container reports healthy.
# Usage: .\scripts\dev-up.ps1
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

docker compose up -d
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed (is Docker Desktop running?)" }

& (Join-Path $PSScriptRoot "wait-for-healthy.ps1")
