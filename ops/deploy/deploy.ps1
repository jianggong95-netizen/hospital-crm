#Requires -Version 5.1
<#
.SYNOPSIS
    Deploy the Hospital CRM API jar on a Windows host.

.DESCRIPTION
    Preflight checks -> database backup -> stop old process -> keep previous jar
    -> copy new jar -> start -> poll readiness -> automatic rollback on failure.

    The managed process is identified by the pid file <AppDir>/crm-api.pid.
    Deployment log: <AppDir>/deploy-<timestamp>.log
    Application log: <AppDir>/crm-api.log (stderr goes to crm-api-stderr.log)

.PARAMETER JarPath
    New jar file. Default backend/crm-boot/target/crm-api.jar

.PARAMETER AppDir
    Runtime directory that contains crm-api.jar, pid and logs. Default ./run

.PARAMETER Port
    Port used by the old process check. Default 8080

.PARAMETER HealthUrl
    Readiness endpoint. Default http://127.0.0.1:8080/actuator/health/readiness

.PARAMETER DbBackup
    Accepted for explicitness; database backup runs by default.

.PARAMETER SkipDbBackup
    Skip the pre-deployment database backup.

.PARAMETER ApiBaseUrl
    CRM API base URL used to report the backup.

.PARAMETER ApiToken
    Bearer token with crm:ops:manage permission.

.PARAMETER TimeoutSeconds
    Health check timeout in seconds. Default 120

.EXAMPLE
    .\deploy.ps1 -JarPath ..\..\backend\crm-boot\target\crm-api.jar -AppDir ..\..\run -Port 8080

.EXAMPLE
    .\deploy.ps1 -SkipDbBackup -AppDir D:\crm-run -Port 18080 -HealthUrl http://127.0.0.1:18080/actuator/health/readiness
#>
[CmdletBinding()]
param(
    [string]$JarPath = "backend/crm-boot/target/crm-api.jar",
    [string]$AppDir = "./run",
    [int]$Port = 8080,
    [string]$HealthUrl = "http://127.0.0.1:8080/actuator/health/readiness",
    [switch]$DbBackup,
    [switch]$SkipDbBackup,
    [string]$ApiBaseUrl = "",
    [string]$ApiToken = "",
    [int]$TimeoutSeconds = 180,
    [string]$JavaOpts = "-XX:MaxRAMPercentage=50 -XX:+UseG1GC"
)

$ErrorActionPreference = "Stop"
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

$script:DeployLog = ""
$script:ExitCode = 0

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $line = "[{0}] [{1}] {2}" -f $ts, $Level, $Message
    Write-Host $line
    if ($script:DeployLog) {
        try {
            $encoding = New-Object System.Text.UTF8Encoding($false)
            [System.IO.File]::AppendAllText($script:DeployLog, $line + "`n", $encoding)
        }
        catch { }
    }
}

function Fail {
    param([string]$Message)
    Write-Log "ERROR" $Message
    exit 1
}

function Invoke-Native {
    param([string]$Command, [string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = & $Command @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previous
    return @{ ExitCode = $exitCode; Output = @($output) }
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
    $stdout = Join-Path $script:AppDirFull "crm-api.log"
    $stderr = Join-Path $script:AppDirFull "crm-api-stderr.log"
    $javaArgs = @($JavaOpts -split '\s+' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) + @("-jar", $script:CurrentJar)
    Write-Log "INFO" ("Starting application: java {0}" -f ($javaArgs -join " "))
    $process = Start-Process -FilePath $script:JavaPath -ArgumentList $javaArgs -WorkingDirectory $script:AppDirFull `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($script:PidFile, [string]$process.Id + "`n", $encoding)
    Write-Log "INFO" ("Application started, pid={0}" -f $process.Id)
    return $process
}

function Test-HealthOnce {
    try {
        $response = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 5
        $content = $response.Content
        # PowerShell 5 在响应缺少 charset 时返回 byte[]，需显式按 UTF-8 解码后再匹配
        if ($content -is [byte[]]) {
            $content = [System.Text.Encoding]::UTF8.GetString($content)
        }
        if ($response.StatusCode -eq 200 -and $content -match '"status"\s*:\s*"UP"') { return $true }
        return $false
    }
    catch {
        return $false
    }
}

function Wait-Health {
    param([System.Diagnostics.Process]$Process, [int]$Timeout)
    Write-Log "INFO" ("Waiting for readiness: {0} (timeout {1}s)" -f $HealthUrl, $Timeout)
    $deadline = (Get-Date).AddSeconds($Timeout)
    while ((Get-Date) -lt $deadline) {
        if ($Process -and $Process.HasExited) { return "PROCESS_EXITED" }
        if (Test-HealthOnce) { return "UP" }
        Start-Sleep -Seconds 2
    }
    return "TIMEOUT"
}

function Invoke-Rollback {
    Write-Log "WARN" "Deployment failed; starting automatic rollback"
    Stop-CrmApi
    if (-not (Test-Path $script:PrevJar)) {
        Write-Log "ERROR" "No previous jar found; automatic rollback is not possible"
        return $false
    }
    try {
        Copy-Item -LiteralPath $script:PrevJar -Destination $script:CurrentJar -Force
    }
    catch {
        Write-Log "ERROR" ("Failed to restore previous jar: {0}" -f $_.Exception.Message)
        return $false
    }
    try {
        $process = Start-CrmApi
    }
    catch {
        Write-Log "ERROR" ("Failed to start previous jar during rollback: {0}" -f $_.Exception.Message)
        return $false
    }
    $state = Wait-Health -Process $process -Timeout $TimeoutSeconds
    if ($state -eq "UP") {
        Write-Log "INFO" "Rollback succeeded; previous jar is running again"
        return $true
    }
    Write-Log "ERROR" ("Rollback health check failed: {0}" -f $state)
    return $false
}

# ----------------------------------------------------------------------
# Paths and log
# ----------------------------------------------------------------------
$stamp = (Get-Date).ToString("yyyyMMdd_HHmmss")
$script:AppDirFull = [System.IO.Path]::GetFullPath($AppDir)
if (-not [System.IO.Directory]::Exists($script:AppDirFull)) {
    [void][System.IO.Directory]::CreateDirectory($script:AppDirFull)
}
$script:DeployLog = Join-Path $script:AppDirFull ("deploy-{0}.log" -f $stamp)
$script:PidFile = Join-Path $script:AppDirFull "crm-api.pid"
$script:CurrentJar = Join-Path $script:AppDirFull "crm-api.jar"
$script:PrevJar = Join-Path $script:AppDirFull "crm-api.jar.prev"

Write-Log "INFO" "===== CRM API DEPLOYMENT START ====="
Write-Log "INFO" ("AppDir={0}" -f $script:AppDirFull)
Write-Log "INFO" ("JarPath={0}" -f $JarPath)

# ----------------------------------------------------------------------
# Preflight
# ----------------------------------------------------------------------
Write-Log "INFO" "Preflight: checking Java runtime"
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCommand) { Fail "java was not found in PATH" }
$script:JavaPath = $javaCommand.Source
$javaResult = Invoke-Native -Command $script:JavaPath -Arguments @("-version")
if ($javaResult.ExitCode -ne 0) { Fail "java -version failed" }
$javaLine = (($javaResult.Output | ForEach-Object { [string]$_ }) | Select-Object -First 1)
Write-Log "INFO" ("Java: {0}" -f $javaLine)

$jarFull = [System.IO.Path]::GetFullPath($JarPath)
if (-not [System.IO.File]::Exists($jarFull)) { Fail ("Jar file not found: {0}" -f $jarFull) }
Write-Log "INFO" ("New jar: {0} ({1} bytes)" -f $jarFull, (Get-Item -LiteralPath $jarFull).Length)

Write-Log "INFO" ("Preflight: checking port {0}" -f $Port)
$managedPid = Get-ManagedPid
$portOwner = 0
try {
    $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($listeners) { $portOwner = [int](($listeners | Select-Object -First 1).OwningProcess) }
}
catch { }
if ($portOwner -eq 0) {
    try {
        $netstatLines = netstat -ano | Select-String -Pattern (":{0}\s+.*LISTENING" -f $Port)
        foreach ($line in $netstatLines) {
            $parts = ([string]$line).Trim() -split '\s+'
            $candidate = 0
            if ([int]::TryParse($parts[$parts.Length - 1], [ref]$candidate)) { $portOwner = $candidate; break }
        }
    }
    catch { }
}
if ($portOwner -gt 0) {
    if ($managedPid -gt 0 -and $portOwner -eq $managedPid) {
        Write-Log "INFO" ("Port {0} is held by the managed CRM process (pid={1})" -f $Port, $managedPid)
    }
    else {
        Fail ("Port {0} is held by an unmanaged process (pid={1}); stop it manually before deploying" -f $Port, $portOwner)
    }
}
else {
    Write-Log "INFO" ("Port {0} is free" -f $Port)
}

Write-Log "INFO" "Preflight: checking free disk space (>= 2 GB)"
$freeBytes = $null
try {
    $root = [System.IO.Path]::GetPathRoot($script:AppDirFull)
    $deviceId = $root.TrimEnd('\')
    $disk = Get-CimInstance Win32_LogicalDisk -Filter ("DeviceID='{0}'" -f $deviceId) -ErrorAction SilentlyContinue
    if ($disk) { $freeBytes = [long]$disk.FreeSpace }
}
catch { }
if ($null -eq $freeBytes) {
    try {
        $root = [System.IO.Path]::GetPathRoot($script:AppDirFull)
        $disk = Get-WmiObject Win32_LogicalDisk -Filter ("DeviceID='{0}'" -f $root.TrimEnd('\')) -ErrorAction SilentlyContinue
        if ($disk) { $freeBytes = [long]$disk.FreeSpace }
    }
    catch { }
}
if ($null -eq $freeBytes) {
    Write-Log "WARN" "Unable to determine free disk space; continue without this check"
}
elseif ($freeBytes -lt 2GB) {
    Fail ("Free disk space is below 2 GB: {0} bytes" -f $freeBytes)
}
else {
    Write-Log "INFO" ("Free disk space: {0} GB" -f [Math]::Round($freeBytes / 1GB, 2))
}

$profileName = $env:SPRING_PROFILES_ACTIVE
if ($profileName -eq "prod") {
    Write-Log "INFO" "Preflight: prod profile secrets"
    foreach ($keyName in @("CRM_JWT_SIGNING_KEY", "CRM_DATA_ENCRYPTION_KEY")) {
        $value = [Environment]::GetEnvironmentVariable($keyName)
        if ([string]::IsNullOrWhiteSpace($value)) {
            Write-Log "WARN" ("{0} is empty while SPRING_PROFILES_ACTIVE=prod" -f $keyName)
        }
        elseif ($value -like "dev-only*") {
            Write-Log "WARN" ("{0} still uses a development default value" -f $keyName)
        }
    }
}

# ----------------------------------------------------------------------
# Database backup
# ----------------------------------------------------------------------
if (-not $SkipDbBackup) {
    $backupScript = Join-Path (Split-Path -Parent $PSScriptRoot) "pg/backup.ps1"
    if (-not (Test-Path $backupScript)) { Fail ("Backup script not found: {0}" -f $backupScript) }
    Write-Log "INFO" "Pre-deployment database backup"
    $backupArgs = @{ BackupDir = "backups" }
    if (-not [string]::IsNullOrWhiteSpace($ApiBaseUrl)) {
        $backupArgs["ApiBaseUrl"] = $ApiBaseUrl
        $backupArgs["ApiToken"] = $ApiToken
    }
    & $backupScript @backupArgs
    if ($LASTEXITCODE -ne 0) { Fail "Database backup failed; deployment aborted before any change" }
    Write-Log "INFO" "Database backup finished"
}
else {
    Write-Log "WARN" "Database backup skipped (-SkipDbBackup)"
}

# ----------------------------------------------------------------------
# Stop old process and switch jar
# ----------------------------------------------------------------------
Stop-CrmApi

if (-not [string]::Equals($jarFull, $script:CurrentJar, [System.StringComparison]::OrdinalIgnoreCase)) {
    if (Test-Path $script:CurrentJar) {
        Copy-Item -LiteralPath $script:CurrentJar -Destination $script:PrevJar -Force
        Write-Log "INFO" ("Previous jar kept at {0}" -f $script:PrevJar)
    }
    else {
        Write-Log "WARN" "No current jar found; first deployment has no previous jar"
    }
    Copy-Item -LiteralPath $jarFull -Destination $script:CurrentJar -Force
    Write-Log "INFO" ("New jar installed at {0}" -f $script:CurrentJar)
}
else {
    Write-Log "WARN" "JarPath points at the deployed jar; skipping copy and previous-jar backup"
}

# ----------------------------------------------------------------------
# Start and health check
# ----------------------------------------------------------------------
$newProcess = $null
try {
    $newProcess = Start-CrmApi
}
catch {
    Write-Log "ERROR" ("Failed to start the new jar: {0}" -f $_.Exception.Message)
    $rollbackOk = Invoke-Rollback
    if ($rollbackOk) { Write-Log "ERROR" "Result: failed deployment, rolled back to the previous jar" }
    else { Write-Log "ERROR" "Result: failed deployment, manual intervention required" }
    exit 1
}
$healthState = Wait-Health -Process $newProcess -Timeout $TimeoutSeconds

if ($healthState -ne "UP") {
    Write-Log "ERROR" ("Health check failed: {0}" -f $healthState)
    if (Test-Path (Join-Path $script:AppDirFull "crm-api-stderr.log")) {
        Write-Log "ERROR" "Last stderr lines:"
        Get-Content -LiteralPath (Join-Path $script:AppDirFull "crm-api-stderr.log") -Tail 20 -ErrorAction SilentlyContinue |
            ForEach-Object { Write-Log "ERROR" ([string]$_) }
    }
    $rollbackOk = Invoke-Rollback
    Write-Log "ERROR" "===== DEPLOYMENT FAILED ====="
    if ($rollbackOk) { Write-Log "ERROR" "Result: failed deployment, rolled back to the previous jar" }
    else { Write-Log "ERROR" "Result: failed deployment, manual intervention required" }
    exit 1
}

Write-Log "INFO" "Readiness check passed"
Write-Log "INFO" "===== DEPLOYMENT SUCCEEDED ====="
Write-Log "INFO" ("Deployed at {0}" -f (Get-Date).ToString("yyyy-MM-dd HH:mm:ss"))
Write-Log "INFO" ("Rollback command: powershell -NoProfile -ExecutionPolicy Bypass -File {0} -AppDir {1} -Port {2} -HealthUrl {3}" -f `
    (Join-Path $PSScriptRoot "rollback.ps1"), $script:AppDirFull, $Port, $HealthUrl)
Write-Log "INFO" ("Deployment log: {0}" -f $script:DeployLog)

Write-Output ("DEPLOY_STATUS=SUCCESS")
Write-Output ("DEPLOY_APP_DIR={0}" -f $script:AppDirFull)
Write-Output ("DEPLOY_LOG={0}" -f $script:DeployLog)

exit 0
