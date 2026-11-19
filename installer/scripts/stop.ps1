#Requires -Version 5.1
<#
.SYNOPSIS
    Stop the Hospital CRM service (API first, then bundled PostgreSQL).
#>
[CmdletBinding()]
param(
    [string]$AppDir = ''
)
$ErrorActionPreference = 'SilentlyContinue'

# $PSScriptRoot is empty inside param() defaults, so resolve the install root here.
if (-not $AppDir) { $AppDir = Split-Path -Parent $PSScriptRoot }
$app = (Resolve-Path -LiteralPath $AppDir).Path
$envFile = Join-Path $app 'config\.env'
$cfg = @{}
if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z0-9_]+)\s*=\s*(.*)$') { $cfg[$matches[1]] = $matches[2].Trim() }
    }
}
function Cfg([string]$k, [string]$d) { if ($cfg.ContainsKey($k) -and $cfg[$k]) { $cfg[$k] } else { $d } }

$port     = [int](Cfg 'CRM_SERVER_PORT' '8080')
$dbPort   = [int](Cfg 'CRM_DB_PORT' '5432')
$internal = ((Cfg 'CRM_INTERNAL_DB' 'true') -eq 'true')
$pidFile  = Join-Path $app 'run\crm-api.pid'

# 1) API by pid file
if (Test-Path -LiteralPath $pidFile) {
    $raw = (Get-Content -LiteralPath $pidFile -Raw).Trim()
    if ($raw -match '^\d+$') {
        Stop-Process -Id ([int]$raw) -Force
        Write-Host "stopped crm-api pid=$raw"
    }
    Remove-Item -LiteralPath $pidFile -Force
}
# 2) API by port (fallback if pid file missing)
$conn = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) { Stop-Process -Id $conn.OwningProcess -Force; Write-Host "stopped process on port $port (pid=$($conn.OwningProcess))" }

# 3) bundled PostgreSQL
if ($internal) {
    $pgCtl = Join-Path $app 'pgsql\bin\pg_ctl.exe'
    $pgData = Join-Path $app 'pgdata'
    if ((Test-Path -LiteralPath $pgCtl) -and (Test-Path -LiteralPath (Join-Path $pgData 'postmaster.pid'))) {
        & $pgCtl stop -D $pgData -m fast -w | Out-Null
        Write-Host 'postgres stopped'
    } else {
        Write-Host 'postgres is not running'
    }
}
Write-Host 'Hospital CRM stopped.'
