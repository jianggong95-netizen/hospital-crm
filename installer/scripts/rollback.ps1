#Requires -Version 5.1
<#
.SYNOPSIS
    Roll the installed app back to the previous jar kept by upgrade.ps1 (crm-api.jar.prev).
#>
[CmdletBinding()]
param(
    [string]$AppDir = ''
)
$ErrorActionPreference = 'Stop'

# $PSScriptRoot is empty inside param() defaults, so resolve the install root here.
if (-not $AppDir) { $AppDir = Split-Path -Parent $PSScriptRoot }
$app = (Resolve-Path -LiteralPath $AppDir).Path
$jar     = Join-Path $app 'app\crm-api.jar'
$prevJar = Join-Path $app 'app\crm-api.jar.prev'
$vf      = Join-Path $app 'version.json'
$prevVf  = Join-Path $app 'version.json.prev'
$bin     = Join-Path $app 'bin'

if (-not (Test-Path -LiteralPath $prevJar)) { throw "No previous jar to roll back to: $prevJar" }

Write-Host 'Stopping service ...'
& (Join-Path $bin 'stop.ps1') -AppDir $app

Write-Host 'Restoring previous jar ...'
Copy-Item -LiteralPath $prevJar -Destination $jar -Force
if ((Test-Path -LiteralPath $prevVf)) { Copy-Item -LiteralPath $prevVf -Destination $vf -Force }

Write-Host 'Starting service with previous version ...'
& (Join-Path $bin 'start.ps1') -AppDir $app
Write-Host 'Rollback complete.'
