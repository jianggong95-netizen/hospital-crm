#Requires -Version 5.1
<#
.SYNOPSIS
    One-time bootstrap of the bundled portable PostgreSQL (initdb + create database).
.DESCRIPTION
    Runs during installation when the user chose "use bundled database".
    Idempotent: skipped if pgdata is already initialized. Flyway creates all tables on first API start.
#>
[CmdletBinding()]
param(
    [string]$AppDir = '',
    [string]$DbName = 'crm',
    [string]$DbUser = 'crm',
    [string]$DbPassword = '',
    [int]$DbPort = 5432
)
$ErrorActionPreference = 'Stop'

# $PSScriptRoot is empty inside param() defaults, so resolve the install root here.
if (-not $AppDir) { $AppDir = Split-Path -Parent $PSScriptRoot }
$app = (Resolve-Path -LiteralPath $AppDir).Path
$pgBin  = Join-Path $app 'pgsql\bin'
$pgData = Join-Path $app 'pgdata'
$logDir = Join-Path $app 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if (Test-Path -LiteralPath (Join-Path $pgData 'PG_VERSION')) {
    Write-Host "database already initialized at $pgData; skipping."
    exit 0
}

# postgres.exe refuses to run with an elevated (administrator) token, so this bootstrap
# must run as a normal user. The installer invokes it with runasoriginaluser.
$elev = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if ($elev) { throw 'The bundled PostgreSQL cannot run elevated. Launch Setup.exe as a normal user (accept the UAC prompt) instead of from an elevated console.' }
if (-not (Test-Path -LiteralPath (Join-Path $pgBin 'initdb.exe'))) { throw "bundled postgres not found under $pgBin" }
if (-not $DbPassword) { throw 'DbPassword is required to initialize the bundled database.' }

New-Item -ItemType Directory -Force -Path $pgData | Out-Null
$pwFile = Join-Path $env:TEMP ("crm_pw_{0}.tmp" -f (Get-Random))
[System.IO.File]::WriteAllText($pwFile, $DbPassword, (New-Object System.Text.UTF8Encoding($false)))

try {
    Write-Host 'Running initdb ...'
    # --locale=C is required on non-English Windows: the OS ANSI locale (e.g. Chinese
    # (Simplified)_China.936) has no matching text-search configuration and initdb aborts.
    & (Join-Path $pgBin 'initdb.exe') -D $pgData -U $DbUser --pwfile=$pwFile -A scram-sha-256 `
        --encoding=UTF8 --locale=C | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "initdb failed (exit=$LASTEXITCODE)" }

    Write-Host 'Starting postgres for bootstrap ...'
    $logFile = Join-Path $logDir 'bootstrap-postgres.log'
    $proc = Start-Process -FilePath (Join-Path $pgBin 'postgres.exe') `
        -ArgumentList @('-D', ('"' + $pgData + '"'), '-p', [string]$DbPort, '-c', 'listen_addresses=127.0.0.1') `
        -RedirectStandardOutput $logFile -RedirectStandardError (Join-Path $logDir 'bootstrap-postgres.err.log') `
        -WindowStyle Hidden -PassThru

    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        $c = New-Object Net.Sockets.TcpClient
        try { $c.Connect('127.0.0.1', $DbPort); $ready = $true } catch { } finally { $c.Close() }
        if ($ready) { break }
    }
    if (-not $ready) { throw 'postgres did not become ready during bootstrap' }

    Write-Host "Creating database '$DbName' ..."
    $env:PGPASSWORD = $DbPassword
    & (Join-Path $pgBin 'psql.exe') -h 127.0.0.1 -p $DbPort -U $DbUser -d postgres -v ON_ERROR_STOP=1 `
        -c "CREATE DATABASE $DbName"
    if ($LASTEXITCODE -ne 0) { throw "CREATE DATABASE $DbName failed (exit=$LASTEXITCODE)" }
    & (Join-Path $pgBin 'psql.exe') -h 127.0.0.1 -p $DbPort -U $DbUser -d postgres -v ON_ERROR_STOP=1 `
        -c "ALTER DATABASE $DbName OWNER TO $DbUser"
    if ($LASTEXITCODE -ne 0) { throw "ALTER DATABASE $DbName failed (exit=$LASTEXITCODE)" }
    $env:PGPASSWORD = $null

    & (Join-Path $pgBin 'pg_ctl.exe') stop -D $pgData -m fast -w | Out-Null
    Write-Host 'Bundled database initialized.'
} finally {
    Remove-Item -LiteralPath $pwFile -Force -ErrorAction SilentlyContinue
}
