#Requires -Version 5.1
<#
.SYNOPSIS
    Roll back the Hospital CRM API to the previous jar.

.DESCRIPTION
    Stops the managed process, optionally restores a database backup, swaps
    crm-api.jar.prev into place (the failed jar is kept as
    crm-api.jar.failed-<timestamp>), starts the application and waits for the
    readiness endpoint.

.PARAMETER AppDir
    Runtime directory containing crm-api.jar / crm-api.jar.prev / crm-api.pid.

.PARAMETER Port
    Port used for process awareness (informational).

.PARAMETER HealthUrl
    Readiness endpoint. Default http://127.0.0.1:8080/actuator/health/readiness

.PARAMETER RestoreBackupFile
    Optional .dump file. When supplied, ops/pg/restore.ps1 is called with -Force
    to restore it into CRM_DB_NAME before the previous jar is started.

.PARAMETER TimeoutSeconds
    Health check timeout in seconds. Default 120

.EXAMPLE
    .\rollback.ps1 -AppDir ..\..\run

.EXAMPLE
    .\rollback.ps1 -AppDir D:\crm-run -Port 18080 -HealthUrl http://127.0.0.1:18080/actuator/health/readiness -RestoreBackupFile D:\crm-backups\crm_full_20260101_020000.dump
#>
[CmdletBinding()]
param(
    [string]$AppDir = "./run",
    [int]$Port = 8080,
    [string]$HealthUrl = "http://127.0.0.1:8080/actuator/health/readiness",
    [string]$RestoreBackupFile = "",
    [int]$TimeoutSeconds = 180,
    [string]$JavaOpts = "-XX:MaxRAMPercentage=50 -XX:+UseG1GC"
)

$ErrorActionPreference = "Stop"

$script:RollbackLog = ""

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $line = "[{0}] [{1}] {2}" -f $ts, $Level, $Message
    Write-Host $line
    if ($script:RollbackLog) {
        try {
            $encoding = New-Object System.Text.UTF8Encoding($false)
            [System.IO.File]::AppendAllText($script:RollbackLog, $line + "`n", $encoding)
        }
        catch { }
    }
}

function Get-ManagedPid {
    if (-not (Test-Path $script:PidFile)) { return 0 }
    $raw = ""
    try { $raw = (Get-Content -LiteralPath $script:PidFile -Raw).Trim() } catch { return 0 }
    $value = 0
    if ([int]::TryParse($raw, [ref]$value)) { return $value }
    return 0
}

function Stop-CrmApi {
    $managedPid = Get-ManagedPid
    if ($managedPid -gt 0) {
        $process = Get-Process -Id $managedPid -ErrorAction SilentlyContinue
        if ($process) {
            Write-Log "INFO" ("Stopping CRM API process, pid={0}" -f $managedPid)
            try { Stop-Process -Id $managedPid -ErrorAction Stop } catch { Write-Log "WARN" ("Stop-Process failed: {0}" -f $_.Exception.Message) }
            $deadline = (Get-Date).AddSeconds(30)
            while ((Get-Date) -lt $deadline) {
                if (-not (Get-Process -Id $managedPid -ErrorAction SilentlyContinue)) { break }
                Start-Sleep -Milliseconds 500
            }
            if (Get-Process -Id $managedPid -ErrorAction SilentlyContinue) {
                Write-Log "WARN" "Graceful stop timed out; forcing process termination"
                try { Stop-Process -Id $managedPid -Force -ErrorAction Stop } catch { }
            }
        }
    }
    if (Test-Path $script:PidFile) {
        try { [System.IO.File]::Delete($script:PidFile) } catch { }
    }
}

function Start-CrmApi {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCommand) { throw "java was not found in PATH" }
    $stdout = Join-Path $script:AppDirFull "crm-api.log"
    $stderr = Join-Path $script:AppDirFull "crm-api-stderr.log"
    $javaArgs = @($JavaOpts -split '\s+' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) + @("-jar", $script:CurrentJar)
    Write-Log "INFO" ("Starting application: java {0}" -f ($javaArgs -join " "))
    $process = Start-Process -FilePath $javaCommand.Source -ArgumentList $javaArgs -WorkingDirectory $script:AppDirFull `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($script:PidFile, [string]$process.Id + "`n", $encoding)
    Write-Log "INFO" ("Application started, pid={0}" -f $process.Id)
    return $process
}

function Wait-Health {
    param([System.Diagnostics.Process]$Process, [int]$Timeout)
    Write-Log "INFO" ("Waiting for readiness: {0} (timeout {1}s)" -f $HealthUrl, $Timeout)
    $deadline = (Get-Date).AddSeconds($Timeout)
    while ((Get-Date) -lt $deadline) {
        if ($Process -and $Process.HasExited) { return "PROCESS_EXITED" }
        try {
            $response = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 5
            $content = $response.Content
            # PowerShell 5 在响应缺少 charset 时返回 byte[]，需显式按 UTF-8 解码后再匹配
            if ($content -is [byte[]]) {
                $content = [System.Text.Encoding]::UTF8.GetString($content)
            }
            if ($response.StatusCode -eq 200 -and $content -match '"status"\s*:\s*"UP"') { return "UP" }
        }
        catch { }
        Start-Sleep -Seconds 2
    }
    return "TIMEOUT"
}

# ----------------------------------------------------------------------
# Paths
# ----------------------------------------------------------------------
$stamp = (Get-Date).ToString("yyyyMMdd_HHmmss")
$script:AppDirFull = [System.IO.Path]::GetFullPath($AppDir)
if (-not [System.IO.Directory]::Exists($script:AppDirFull)) {
    Write-Log "ERROR" ("AppDir does not exist: {0}" -f $script:AppDirFull)
    exit 1
}
$script:RollbackLog = Join-Path $script:AppDirFull ("rollback-{0}.log" -f $stamp)
$script:PidFile = Join-Path $script:AppDirFull "crm-api.pid"
$script:CurrentJar = Join-Path $script:AppDirFull "crm-api.jar"
$script:PrevJar = Join-Path $script:AppDirFull "crm-api.jar.prev"

Write-Log "INFO" "===== CRM API ROLLBACK START ====="
Write-Log "INFO" ("AppDir={0}" -f $script:AppDirFull)

if (-not (Test-Path $script:PrevJar)) {
    Write-Log "ERROR" "Previous jar not found: $script:PrevJar"
    Write-Log "ERROR" "Rollback is not possible. Restore crm-api.jar manually from the release package."
    exit 1
}

# ----------------------------------------------------------------------
# Stop application
# ----------------------------------------------------------------------
Write-Log "INFO" ("Rolling back on port {0}" -f $Port)
Stop-CrmApi

# ----------------------------------------------------------------------
# Optional database restore (before starting the old application)
# ----------------------------------------------------------------------
if (-not [string]::IsNullOrWhiteSpace($RestoreBackupFile)) {
    $restoreScript = Join-Path (Split-Path -Parent $PSScriptRoot) "pg/restore.ps1"
    if (-not (Test-Path $restoreScript)) {
        Write-Log "ERROR" ("Restore script not found: {0}" -f $restoreScript)
        exit 1
    }
    if (-not [System.IO.File]::Exists($RestoreBackupFile)) {
        Write-Log "ERROR" ("Backup file not found: {0}" -f $RestoreBackupFile)
        exit 1
    }

    $targetDatabase = $env:CRM_DB_NAME
    if ([string]::IsNullOrWhiteSpace($targetDatabase) -and -not [string]::IsNullOrWhiteSpace($env:CRM_DB_URL)) {
        if ($env:CRM_DB_URL -match '^jdbc:postgresql://([^/:?]+)(?::(\d+))?/([^?;]+)') { $targetDatabase = $Matches[3] }
    }
    if ([string]::IsNullOrWhiteSpace($targetDatabase)) { $targetDatabase = "crm" }

    Write-Log "WARN" ("Database restore requested before rollback: target={0}" -f $targetDatabase)
    & $restoreScript -BackupFile $RestoreBackupFile -TargetDatabase $targetDatabase -CreateDatabase -Force
    if ($LASTEXITCODE -ne 0) {
        Write-Log "ERROR" "Database restore failed; application remains stopped. Manual intervention required."
        exit 1
    }
    Write-Log "INFO" "Database restore completed"
}

# ----------------------------------------------------------------------
# Swap jars
# ----------------------------------------------------------------------
$failedJar = Join-Path $script:AppDirFull ("crm-api.jar.failed-{0}" -f $stamp)
if (Test-Path $script:CurrentJar) {
    try {
        Move-Item -LiteralPath $script:CurrentJar -Destination $failedJar -Force
        Write-Log "INFO" ("Failed jar kept at {0}" -f $failedJar)
    }
    catch {
        Write-Log "ERROR" ("Failed to move current jar: {0}" -f $_.Exception.Message)
        exit 1
    }
}
try {
    Move-Item -LiteralPath $script:PrevJar -Destination $script:CurrentJar -Force
    Write-Log "INFO" ("Previous jar restored to {0}" -f $script:CurrentJar)
}
catch {
    Write-Log "ERROR" ("Failed to restore previous jar: {0}" -f $_.Exception.Message)
    exit 1
}

# ----------------------------------------------------------------------
# Start and verify
# ----------------------------------------------------------------------
try {
    $process = Start-CrmApi
}
catch {
    Write-Log "ERROR" ("Failed to start previous jar: {0}" -f $_.Exception.Message)
    exit 1
}

$state = Wait-Health -Process $process -Timeout $TimeoutSeconds
if ($state -ne "UP") {
    Write-Log "ERROR" ("Rollback health check failed: {0}" -f $state)
    Write-Log "ERROR" "Inspect crm-api.log and crm-api-stderr.log in $script:AppDirFull"
    exit 1
}

Write-Log "INFO" "Readiness check passed"
Write-Log "INFO" "===== ROLLBACK SUCCEEDED ====="
Write-Log "INFO" ("Rolled back at {0}" -f (Get-Date).ToString("yyyy-MM-dd HH:mm:ss"))
Write-Log "INFO" ("Failed jar kept at {0}" -f $failedJar)
Write-Log "INFO" ("Rollback log: {0}" -f $script:RollbackLog)

Write-Output ("ROLLBACK_STATUS=SUCCESS")
Write-Output ("ROLLBACK_APP_DIR={0}" -f $script:AppDirFull)
Write-Output ("ROLLBACK_LOG={0}" -f $script:RollbackLog)

exit 0
