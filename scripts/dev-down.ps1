# Stops the local infrastructure stack. Data volumes are preserved;
# add -Wipe to remove them (fresh schemas/realm on next start).
# Usage: .\scripts\dev-down.ps1 [-Wipe]
param([switch]$Wipe)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

if ($Wipe) {
    Write-Host "Stopping stack and REMOVING data volumes (mysql/redis/keycloak state will be lost)..."
    docker compose down -v
} else {
    docker compose down
}
