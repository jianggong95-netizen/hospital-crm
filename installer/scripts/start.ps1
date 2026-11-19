#Requires -Version 5.1
<#
.SYNOPSIS
    Start the Hospital CRM single-process service (PostgreSQL + API that serves the admin console).
.DESCRIPTION
    Reads config\.env under the install directory, optionally starts the bundled portable
    PostgreSQL, then launches crm-api.jar with the Spring properties derived from the config.
    Processes are created detached (WMI) so they survive this console closing.
    Idempotent: already-running components are skipped.
#>
[CmdletBinding()]
param(
    [string]$AppDir = ''
)
$ErrorActionPreference = 'Stop'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

# $PSScriptRoot is empty inside param() defaults, so resolve the install root here.
if (-not $AppDir) { $AppDir = Split-Path -Parent $PSScriptRoot }
$app = (Resolve-Path -LiteralPath $AppDir).Path
$envFile = Join-Path $app 'config\.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw "config\.env not found: $envFile" }

$cfg = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z0-9_]+)\s*=\s*(.*)$') { $cfg[$matches[1]] = $matches[2].Trim() }
}
function Cfg([string]$k, [string]$d) { if ($cfg.ContainsKey($k) -and $cfg[$k]) { $cfg[$k] } else { $d } }

$port     = [int](Cfg 'CRM_SERVER_PORT' '8080')
$dbPort   = [int](Cfg 'CRM_DB_PORT' '5432')
$internal = ((Cfg 'CRM_INTERNAL_DB' 'true') -eq 'true')
$logDir   = Cfg 'CRM_LOG_PATH' (Join-Path $app 'logs')
$runDir   = Join-Path $app 'run'
$pidFile  = Join-Path $runDir 'crm-api.pid'
$healthUrl = "http://127.0.0.1:$port/actuator/health/readiness"
New-Item -ItemType Directory -Force -Path $logDir, $runDir | Out-Null

function Test-Port([int]$p) {
    $c = New-Object Net.Sockets.TcpClient
    try { $c.Connect('127.0.0.1', $p); return $true } catch { return $false } finally { $c.Close() }
}
function Start-Detached([string]$commandLine) {
    $r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $commandLine }
    if ($r.ReturnValue -ne 0) { throw "Win32_Process Create failed: $($r.ReturnValue)" }
    return $r.ProcessId
}
function Test-ApiUp {
    try {
        $h = Invoke-WebRequest $healthUrl -UseBasicParsing -TimeoutSec 4
        $ct = $h.Content
        if ($ct -is [byte[]]) { $ct = [System.Text.Encoding]::UTF8.GetString($ct) }
        return ($ct -match '"status"\s*:\s*"UP"')
    } catch { return $false }
}

# ---------- PostgreSQL (bundled, optional) ----------
if ($internal) {
    # postgres.exe refuses to run with an elevated (administrator) token.
    $elev = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if ($elev) { throw 'The bundled PostgreSQL cannot run elevated. Start the service from a normal (non-administrator) PowerShell window.' }
    $pgData = Join-Path $app 'pgdata'
    # Self-heal: a silent/elevated install cannot bootstrap PostgreSQL (it refuses the
    # administrator token), so initialize on the first normal-user start instead.
    if (-not (Test-Path -LiteralPath (Join-Path $pgData 'PG_VERSION'))) {
        Write-Host 'bundled database not initialized; initializing now ...'
        & (Join-Path $app 'bin\init-db.ps1') -AppDir $app `
            -DbName (Cfg 'CRM_DB_NAME' 'crm') -DbUser (Cfg 'CRM_DB_USERNAME' 'crm') `
            -DbPassword (Cfg 'CRM_DB_PASSWORD' '') -DbPort $dbPort
        if ($LASTEXITCODE -ne 0) { throw 'bundled database initialization failed' }
    }
    if (-not (Test-Port $dbPort)) {
        $pgBin  = Join-Path $app 'pgsql\bin'
        if (-not (Test-Path -LiteralPath (Join-Path $pgData 'PG_VERSION'))) { throw "bundled database not initialized: $pgData" }
        $cmd = '"' + (Join-Path $pgBin 'postgres.exe') + '" -D "' + $pgData + '" -p ' + $dbPort + ' -c listen_addresses=127.0.0.1'
        $pgPid = Start-Detached $cmd
        Write-Host "postgres starting (pid=$pgPid) on $dbPort ..."
        $ok = $false
        for ($i = 0; $i -lt 30; $i++) { Start-Sleep -Seconds 2; if (Test-Port $dbPort) { $ok = $true; break } }
        if (-not $ok) { throw "postgres failed to start on port $dbPort" }
        Write-Host 'postgres ready'
    } else {
        Write-Host "postgres already running on $dbPort"
    }
}

# ---------- API ----------
if (Test-ApiUp) {
    Write-Host "crm-api already ready on $port"
} else {
    $java = Join-Path $app 'jre\bin\java.exe'
    if (-not (Test-Path -LiteralPath $java)) {
        $jc = Get-Command java -ErrorAction SilentlyContinue
        if (-not $jc) { throw 'No bundled JRE (jre\bin\java.exe) and java is not in PATH' }
        $java = $jc.Source
    }
    $jar = Join-Path $app 'app\crm-api.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw "crm-api.jar not found: $jar" }

    $cors = Cfg 'CRM_CORS_ALLOWED_ORIGINS' ("http://localhost:$port,http://127.0.0.1:$port")
    $dbUrl = Cfg 'CRM_DB_URL' ("jdbc:postgresql://127.0.0.1:$dbPort/" + (Cfg 'CRM_DB_NAME' 'crm'))

    $javaArgs = @(
        '-XX:MaxRAMPercentage=50', '-XX:+UseG1GC', '-Dfile.encoding=UTF-8',
        ('-DCRM_LOG_PATH=' + $logDir),
        '-jar', ('"' + $jar + '"'),
        ('--spring.datasource.url=' + $dbUrl),
        ('--spring.datasource.username=' + (Cfg 'CRM_DB_USERNAME' 'crm')),
        ('--spring.datasource.password=' + (Cfg 'CRM_DB_PASSWORD' '')),
        ('--server.port=' + $port),
        ('--crm.security.jwt.signing-key=' + (Cfg 'CRM_JWT_SIGNING_KEY' '')),
        ('--crm.security.bootstrap-admin.enabled=' + (Cfg 'CRM_BOOTSTRAP_ADMIN_ENABLED' 'true')),
        ('--crm.security.bootstrap-admin.username=' + (Cfg 'CRM_BOOTSTRAP_ADMIN_USERNAME' 'admin')),
        ('--crm.security.bootstrap-admin.password=' + (Cfg 'CRM_BOOTSTRAP_ADMIN_PASSWORD' '')),
        ('--crm.security.data-encryption.key=' + (Cfg 'CRM_DATA_ENCRYPTION_KEY' '')),
        ('--crm.cors.allowed-origins=' + $cors),
        ('--management.endpoints.web.cors.allowed-origins=' + $cors),
        ('--crm.ops.metrics.endpoint-public=' + (Cfg 'CRM_OPS_METRICS_PUBLIC' 'true'))
    )
    if ((Cfg 'CRM_HIS_ENABLED' 'false') -eq 'true') {
        $javaArgs += '--crm.his.enabled=true'
        if ($cfg['CRM_HIS_URL'])     { $javaArgs += ('--crm.his.url=' + $cfg['CRM_HIS_URL']) }
        if ($cfg['CRM_HIS_USERNAME']){ $javaArgs += ('--crm.his.username=' + $cfg['CRM_HIS_USERNAME']) }
        if ($cfg['CRM_HIS_PASSWORD']){ $javaArgs += ('--crm.his.password=' + $cfg['CRM_HIS_PASSWORD']) }
    } else {
        $javaArgs += '--crm.his.enabled=false'
    }

    $apiPid = Start-Detached ('"' + $java + '" ' + ($javaArgs -join ' '))
    Set-Content -LiteralPath $pidFile -Value ([string]$apiPid) -Encoding ASCII
    Write-Host "crm-api starting (pid=$apiPid) ..."

    $ready = $false
    for ($i = 0; $i -lt 60; $i++) { Start-Sleep -Seconds 3; if (Test-ApiUp) { $ready = $true; break } }
    if (-not $ready) { throw "crm-api failed to become ready; check $logDir\crm-api.log" }
}

Write-Host ''
Write-Host 'Hospital CRM is up (single process):'
Write-Host "  Admin console : http://localhost:$port"
Write-Host "  CRM API       : http://localhost:$port/api/v1"
Write-Host "  Swagger UI    : http://localhost:$port/swagger-ui.html"
Write-Host ('  Login         : ' + (Cfg 'CRM_BOOTSTRAP_ADMIN_USERNAME' 'admin') + ' / <the admin password you set>')
