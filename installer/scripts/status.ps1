#Requires -Version 5.1
<#
.SYNOPSIS
    Show installed version, running version and service health.
#>
[CmdletBinding()]
param(
    [string]$AppDir = ''
)
$ErrorActionPreference = 'SilentlyContinue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

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
$port = [int](Cfg 'CRM_SERVER_PORT' '8080')

$installed = 'unknown'
$vf = Join-Path $app 'version.json'
if (Test-Path -LiteralPath $vf) {
    try { $installed = (Get-Content -LiteralPath $vf -Raw | ConvertFrom-Json).version } catch { }
}
Write-Host "Installed version : v$installed"

$running = $false
try {
    $h = Invoke-WebRequest "http://127.0.0.1:$port/actuator/health/readiness" -UseBasicParsing -TimeoutSec 4
    $ct = $h.Content; if ($ct -is [byte[]]) { $ct = [System.Text.Encoding]::UTF8.GetString($ct) }
    $running = ($ct -match '"status"\s*:\s*"UP"')
} catch { }

if ($running) {
    Write-Host 'Service status    : RUNNING'
    try {
        $i = Invoke-WebRequest "http://127.0.0.1:$port/actuator/info" -UseBasicParsing -TimeoutSec 4
        $ic = $i.Content; if ($ic -is [byte[]]) { $ic = [System.Text.Encoding]::UTF8.GetString($ic) }
        $info = $ic | ConvertFrom-Json
        $rv = $info.app.version
        $bt = $info.app.'build-time'
        Write-Host "Running version   : v$rv"
        if ($bt) { Write-Host "Build time        : $bt" }
        if ($rv -and ($rv -ne $installed)) { Write-Host "NOTE: running version differs from installed manifest (restart may be needed)." }
    } catch { Write-Host 'Running version   : (info unavailable)' }
} else {
    Write-Host "Service status    : STOPPED (start with: bin\start.ps1)"
}
