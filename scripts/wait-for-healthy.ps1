# Polls `docker compose ps` until all services with a healthcheck report healthy.
# Fails after the timeout so CI and humans get an honest answer instead of a hang.
# Usage: .\scripts\wait-for-healthy.ps1 [-TimeoutSeconds 180]
param([int]$TimeoutSeconds = 180)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ($true) {
    $lines = docker compose ps --format json 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $lines) { throw "docker compose ps failed - is the stack up?" }
    $services = $lines | ForEach-Object { $_ | ConvertFrom-Json }
    $pending = @($services | Where-Object { $_.Health -and $_.Health -ne "healthy" })
    # One-shot init services (kafka-init) exit 0 by design - only a non-zero exit is broken.
    $broken  = @($services | Where-Object {
        ($_.State -eq "exited" -and $_.ExitCode -ne 0) -or $_.Health -eq "unhealthy"
    })
    if ($broken.Count -gt 0) {
        $names = ($broken | ForEach-Object { $_.Service }) -join ", "
        throw "Containers failed: $names. Inspect with: docker compose logs $names"
    }
    if ($pending.Count -eq 0) {
        Write-Host "All containers healthy:" -ForegroundColor Green
        $services | ForEach-Object { Write-Host ("  {0,-12} {1}" -f $_.Service, $_.Status) }
        break
    }
    if ((Get-Date) -gt $deadline) {
        $names = ($pending | ForEach-Object { $_.Service }) -join ", "
        throw "Timed out after ${TimeoutSeconds}s waiting for: $names"
    }
    Start-Sleep -Seconds 3
}
