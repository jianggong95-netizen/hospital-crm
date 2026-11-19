#Requires -Version 5.1
<#
.SYNOPSIS
    Hot-upgrade the installed Hospital CRM to a new crm-api.jar without re-running the wizard.
.DESCRIPTION
    stop -> database backup -> keep previous jar/manifest -> replace -> start (Flyway auto-migrate)
    -> readiness probe -> on failure auto rollback to .prev.
.PARAMETER NewJar
    Path to the new crm-api.jar (required).
.PARAMETER NewVersionJson
    Optional version.json shipped with the new jar; copied alongside so status.ps1 stays accurate.
.EXAMPLE
    .\upgrade.ps1 -NewJar D:\downloads\crm-api-1.0.2.jar -NewVersionJson D:\downloads\version.json
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$NewJar,
    [string]$NewVersionJson = '',
    [string]$AppDir = '',
    [int]$TimeoutSeconds = 240
)
$ErrorActionPreference = 'Stop'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

# $PSScriptRoot is empty inside param() defaults, so resolve the install root here.
if (-not $AppDir) { $AppDir = Split-Path -Parent $PSScriptRoot }
$app = (Resolve-Path -LiteralPath $AppDir).Path
$bin     = Join-Path $app 'bin'
$jar     = Join-Path $app 'app\crm-api.jar'
$prevJar = Join-Path $app 'app\crm-api.jar.prev'
$vf      = Join-Path $app 'version.json'
$prevVf  = Join-Path $app 'version.json.prev'
$backups = Join-Path $app 'backups'
$stamp   = (Get-Date).ToString('yyyyMMdd_HHmmss')
$upLog   = Join-Path $app ("upgrade-{0}.log" -f $stamp)
$envFile = Join-Path $app 'config\.env'

$NewJar = (Resolve-Path -LiteralPath $NewJar).Path
if (-not (Test-Path -LiteralPath $backups)) { New-Item -ItemType Directory -Force -Path $backups | Out-Null }

function Log([string]$m) { $line = "[{0}] {1}" -f (Get-Date).ToString('yyyy-MM-dd HH:mm:ss'), $m; Write-Host $line; [System.IO.File]::AppendAllText($upLog, $line + "`n", (New-Object System.Text.UTF8Encoding($false))) }

$cfg = @{}
if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile | ForEach-Object { if ($_ -match '^\s*([A-Za-z0-9_]+)\s*=\s*(.*)$') { $cfg[$matches[1]] = $matches[2].Trim() } }
}
function Cfg([string]$k, [string]$d) { if ($cfg.ContainsKey($k) -and $cfg[$k]) { $cfg[$k] } else { $d } }
$port     = [int](Cfg 'CRM_SERVER_PORT' '8080')
$dbPort   = [int](Cfg 'CRM_DB_PORT' '5432')
$dbName   = Cfg 'CRM_DB_NAME' 'crm'
$dbUser   = Cfg 'CRM_DB_USERNAME' 'crm'
$dbPass   = Cfg 'CRM_DB_PASSWORD' ''
$internal = ((Cfg 'CRM_INTERNAL_DB' 'true') -eq 'true')
$healthUrl = "http://127.0.0.1:$port/actuator/health/readiness"

function Test-ApiUp {
    try { $h = Invoke-WebRequest $healthUrl -UseBasicParsing -TimeoutSec 4; $ct = $h.Content; if ($ct -is [byte[]]) { $ct = [System.Text.Encoding]::UTF8.GetString($ct) }; return ($ct -match '"status"\s*:\s*"UP"') } catch { return $false }
}

Log "===== UPGRADE START ===== app=$app newJar=$NewJar"

# 0) version compare
$curVer = 'unknown'
if (Test-Path -LiteralPath $vf) { try { $curVer = (Get-Content -LiteralPath $vf -Raw | ConvertFrom-Json).version } catch { } }
$newVer = ''
if ($NewVersionJson -and (Test-Path -LiteralPath $NewVersionJson)) { try { $newVer = (Get-Content -LiteralPath $NewVersionJson -Raw | ConvertFrom-Json).version } catch { } }
if ($newVer -and ($newVer -eq $curVer)) { Log "Target version v$newVer equals running manifest; nothing to do."; exit 0 }
Log "current=v$curVer target=$(if($newVer){"v$newVer"}else{'(unknown)'})"

# 1) database backup (before stopping: pg_dump needs a live server and takes a consistent snapshot)
if ($internal) {
    $dumpFile = Join-Path $backups ("crm_{0}.sql" -f $stamp)
    $pgDump = Join-Path $app 'pgsql\bin\pg_dump.exe'
    if (Test-Path -LiteralPath $pgDump) {
        Log "Backing up database to $dumpFile ..."
        $env:PGPASSWORD = $dbPass
        & $pgDump -h 127.0.0.1 -p $dbPort -U $dbUser -d $dbName -Fp -f $dumpFile 2>&1 | ForEach-Object { Log ("  pg_dump: " + $_) }
        $env:PGPASSWORD = $null
        if (-not (Test-Path -LiteralPath $dumpFile)) { throw "Database backup failed; aborting upgrade. See $upLog" }
    } else { Log 'WARN: pg_dump not found; skipping automatic DB backup.' }
} else {
    Log 'External database: ensure you have a backup before proceeding (Flyway migrations are forward-only).'
}

# 2) stop
Log 'Stopping service ...'
& (Join-Path $bin 'stop.ps1') -AppDir $app

# 3) keep previous
if (Test-Path -LiteralPath $jar) { Copy-Item -LiteralPath $jar -Destination $prevJar -Force }
if (Test-Path -LiteralPath $vf)  { Copy-Item -LiteralPath $vf  -Destination $prevVf  -Force }

# 4) replace
Log 'Installing new jar ...'
Copy-Item -LiteralPath $NewJar -Destination $jar -Force
if ($NewVersionJson -and (Test-Path -LiteralPath $NewVersionJson)) { Copy-Item -LiteralPath $NewVersionJson -Destination $vf -Force }

# 5) start (Flyway migrates during boot) + probe
Log 'Starting service (Flyway migrations run automatically) ...'
try {
    & (Join-Path $bin 'start.ps1') -AppDir $app
} catch { Log ("start.ps1 error: " + $_.Exception.Message) }

$ok = $false
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline) { if (Test-ApiUp) { $ok = $true; break }; Start-Sleep -Seconds 3 }

if ($ok) {
    try {
        $i = Invoke-WebRequest "http://127.0.0.1:$port/actuator/info" -UseBasicParsing -TimeoutSec 5
        $ic = $i.Content; if ($ic -is [byte[]]) { $ic = [System.Text.Encoding]::UTF8.GetString($ic) }
        Log ("Running version now: v" + (($ic | ConvertFrom-Json).app.version))
    } catch { }
    Log 'UPGRADE SUCCEEDED.'
    Write-Host ''
    Write-Host "Upgrade complete. Backup: $backups  Log: $upLog"
    exit 0
}

# 6) auto rollback
Log 'Readiness probe failed; rolling back to previous jar ...'
& (Join-Path $bin 'stop.ps1') -AppDir $app
Copy-Item -LiteralPath $prevJar -Destination $jar -Force
if (Test-Path -LiteralPath $prevVf) { Copy-Item -LiteralPath $prevVf -Destination $vf -Force }
try { & (Join-Path $bin 'start.ps1') -AppDir $app } catch { }
$rolled = $false
$rd = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $rd) { if (Test-ApiUp) { $rolled = $true; break }; Start-Sleep -Seconds 3 }
if ($rolled) { Log 'ROLLBACK SUCCEEDED; previous version is running again. New jar kept at .failed for inspection.' }
else { Log 'CRITICAL: rollback health check failed. Manual intervention required. DB backup at backups.' }
Copy-Item -LiteralPath $NewJar -Destination (Join-Path $app 'app\crm-api.jar.failed') -Force -ErrorAction SilentlyContinue
throw "Upgrade failed; rolled back. See $upLog"
