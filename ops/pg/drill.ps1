#Requires -Version 5.1
<#
.SYNOPSIS
    PostgreSQL restore drill for Hospital CRM.

.DESCRIPTION
    Creates a fresh backup, restores it into a temporary drill database, compares
    row counts of key tables and the Flyway version between the source database
    and the drill database, writes drill-result-<timestamp>.json and optionally
    reports the backup plus the verification result to the CRM ops API.

    The drill database is dropped at the end unless -KeepDrillDatabase is used.

.PARAMETER SourceDatabase
    Source database name. Defaults to CRM_DB_NAME or crm.

.PARAMETER DrillDatabase
    Temporary database name. Defaults to crm_restore_drill_yyyyMMddHHmmss.

.PARAMETER BackupDir
    Directory used for the fresh backup and the drill result file.

.PARAMETER ApiBaseUrl
    CRM API base URL. Reporting failures only produce WARN messages.

.PARAMETER ApiToken
    Bearer token with crm:ops:manage permission.

.PARAMETER KeepDrillDatabase
    Keep the drill database for manual inspection.

.EXAMPLE
    .\drill.ps1 -BackupDir D:\crm-backups -ApiBaseUrl http://127.0.0.1:8080 -ApiToken $token

.EXAMPLE
    .\drill.ps1 -SourceDatabase crm -DrillDatabase crm_restore_drill_manual -KeepDrillDatabase
#>
[CmdletBinding()]
param(
    [string]$SourceDatabase = "",
    [string]$DrillDatabase = "",
    [string]$BackupDir = "./backups",
    [string]$ApiBaseUrl = "",
    [string]$ApiToken = "",
    [switch]$KeepDrillDatabase
)

$ErrorActionPreference = "Stop"
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    Write-Host ("[{0}] [{1}] {2}" -f $ts, $Level, $Message)
}

function Fail {
    param([string]$Message)
    Write-Log "ERROR" $Message
    exit 1
}

function Write-Utf8NoBom {
    param([string]$Path, [string]$Text)
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Text, $encoding)
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

function Resolve-DbConnection {
    $url = $env:CRM_DB_URL
    $hostName = $env:PGHOST
    $port = 0
    if (-not [string]::IsNullOrWhiteSpace($env:PGPORT)) {
        [void][int]::TryParse($env:PGPORT, [ref]$port)
    }

    $userName = $env:PGUSER
    if ([string]::IsNullOrWhiteSpace($userName)) { $userName = $env:CRM_DB_USERNAME }

    $password = $env:PGPASSWORD
    if ([string]::IsNullOrWhiteSpace($password)) { $password = $env:CRM_DB_PASSWORD }

    $urlDbName = ""
    if (-not [string]::IsNullOrWhiteSpace($url)) {
        if ($url -match '^jdbc:postgresql://([^/:?]+)(?::(\d+))?/([^?;]+)') {
            if ([string]::IsNullOrWhiteSpace($hostName)) { $hostName = $Matches[1] }
            if ($port -le 0 -and $Matches[2]) { $port = [int]$Matches[2] }
            $urlDbName = $Matches[3]
        }
    }

    if ([string]::IsNullOrWhiteSpace($hostName)) { $hostName = "127.0.0.1" }
    if ($port -le 0) { $port = 5432 }
    if ([string]::IsNullOrWhiteSpace($userName)) { $userName = "crm" }

    return @{
        Host     = $hostName
        Port     = $port
        User     = $userName
        Password = $password
        Database = $urlDbName
    }
}

function Get-TableCount {
    param([string]$Database, [string]$Table)
    $result = Invoke-Native -Command $script:Psql -Arguments @(
        "-h", $conn.Host, "-p", [string]$conn.Port, "-U", $conn.User, "-d", $Database,
        "-t", "-A", "-v", "ON_ERROR_STOP=1", "-c", "SELECT count(*) FROM $Table"
    )
    if ($result.ExitCode -ne 0) {
        return @{ ok = $false; value = -1; message = (($result.Output | Out-String).Trim()) }
    }
    return @{ ok = $true; value = [long](($result.Output -join "").Trim()); message = "" }
}

function Get-FlywayVersion {
    param([string]$Database)
    $result = Invoke-Native -Command $script:Psql -Arguments @(
        "-h", $conn.Host, "-p", [string]$conn.Port, "-U", $conn.User, "-d", $Database,
        "-t", "-A", "-v", "ON_ERROR_STOP=1",
        "-c", "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1"
    )
    if ($result.ExitCode -ne 0) { return "" }
    return (($result.Output -join "").Trim())
}

function Get-FlywayCount {
    param([string]$Database)
    $result = Invoke-Native -Command $script:Psql -Arguments @(
        "-h", $conn.Host, "-p", [string]$conn.Port, "-U", $conn.User, "-d", $Database,
        "-t", "-A", "-v", "ON_ERROR_STOP=1",
        "-c", "SELECT count(*) FROM flyway_schema_history WHERE success = true"
    )
    if ($result.ExitCode -ne 0) { return -1 }
    return [long](($result.Output -join "").Trim())
}

# ----------------------------------------------------------------------
# Resolve arguments and preflight
# ----------------------------------------------------------------------
$conn = Resolve-DbConnection
$env:PGPASSWORD = $conn.Password

if ([string]::IsNullOrWhiteSpace($SourceDatabase)) {
    if (-not [string]::IsNullOrWhiteSpace($env:CRM_DB_NAME)) { $SourceDatabase = $env:CRM_DB_NAME }
    elseif (-not [string]::IsNullOrWhiteSpace($conn.Database)) { $SourceDatabase = $conn.Database }
    else { $SourceDatabase = "crm" }
}

$drillStamp = (Get-Date).ToString("yyyyMMddHHmmss")
if ([string]::IsNullOrWhiteSpace($DrillDatabase)) { $DrillDatabase = "crm_restore_drill_$drillStamp" }

if ($SourceDatabase -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { Fail "Invalid SourceDatabase name" }
if ($DrillDatabase -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { Fail "Invalid DrillDatabase name" }
if ($DrillDatabase -eq $SourceDatabase) { Fail "DrillDatabase must differ from SourceDatabase" }

$psqlCommand = Get-Command psql -ErrorAction SilentlyContinue
$createdbCommand = Get-Command createdb -ErrorAction SilentlyContinue
$dropdbCommand = Get-Command dropdb -ErrorAction SilentlyContinue
if (-not $psqlCommand) { Fail "psql was not found in PATH" }
if (-not $createdbCommand) { Fail "createdb was not found in PATH" }
if (-not $dropdbCommand) { Fail "dropdb was not found in PATH" }
$script:Psql = $psqlCommand.Source

$backupDirFull = [System.IO.Path]::GetFullPath($BackupDir)
if (-not [System.IO.Directory]::Exists($backupDirFull)) {
    [void][System.IO.Directory]::CreateDirectory($backupDirFull)
}

$backupScript = Join-Path $PSScriptRoot "backup.ps1"
$restoreScript = Join-Path $PSScriptRoot "restore.ps1"
if (-not (Test-Path $backupScript)) { Fail "backup.ps1 not found next to drill.ps1" }
if (-not (Test-Path $restoreScript)) { Fail "restore.ps1 not found next to drill.ps1" }

$startedAt = Get-Date
$failureReasons = New-Object System.Collections.ArrayList
$checks = New-Object System.Collections.ArrayList

# ----------------------------------------------------------------------
# Step 1: fresh backup
# ----------------------------------------------------------------------
Write-Log "INFO" "Step 1/5: creating a fresh backup of $SourceDatabase"
$backupOutput = & $backupScript -Database $SourceDatabase -BackupDir $backupDirFull -SkipReport 2>&1
$backupExit = $LASTEXITCODE
$backupOutput | ForEach-Object { Write-Host ([string]$_) }

if ($backupExit -ne 0) {
    [void]$failureReasons.Add("backup script failed with exit code $backupExit")
}
$fileLine = ($backupOutput | Where-Object { ([string]$_).StartsWith("BACKUP_FILE=") } | Select-Object -Last 1)
if (-not $fileLine) { [void]$failureReasons.Add("backup script did not report BACKUP_FILE") }
$backupFile = ""
if ($fileLine) { $backupFile = ([string]$fileLine).Substring("BACKUP_FILE=".Length).Trim() }

if ($failureReasons.Count -eq 0 -and -not [System.IO.File]::Exists($backupFile)) {
    [void]$failureReasons.Add("backup file does not exist: $backupFile")
}

# ----------------------------------------------------------------------
# Step 2: create drill database
# ----------------------------------------------------------------------
$drillCreated = $false
if ($failureReasons.Count -eq 0) {
    Write-Log "INFO" "Step 2/5: creating drill database $DrillDatabase"
    $existsResult = Invoke-Native -Command $script:Psql -Arguments @(
        "-h", $conn.Host, "-p", [string]$conn.Port, "-U", $conn.User, "-d", "postgres",
        "-t", "-A", "-c", "SELECT 1 FROM pg_database WHERE datname = '$DrillDatabase'"
    )
    if ((($existsResult.Output -join "").Trim()) -eq "1") {
        [void]$failureReasons.Add("drill database already exists: $DrillDatabase")
    }
    else {
        $createResult = Invoke-Native -Command $createdbCommand.Source -Arguments @(
            "-h", $conn.Host, "-p", [string]$conn.Port, "-U", $conn.User, $DrillDatabase
        )
        if ($createResult.ExitCode -ne 0) {
            [void]$failureReasons.Add("createdb failed for $DrillDatabase")
        }
        else {
            $drillCreated = $true
        }
    }
}

# ----------------------------------------------------------------------
# Step 3: restore into drill database
# ----------------------------------------------------------------------
if ($failureReasons.Count -eq 0) {
    Write-Log "INFO" "Step 3/5: restoring backup into $DrillDatabase"
    $restoreOutput = & $restoreScript -BackupFile $backupFile -TargetDatabase $DrillDatabase 2>&1
    $restoreExit = $LASTEXITCODE
    $restoreOutput | ForEach-Object { Write-Host ([string]$_) }
    if ($restoreExit -ne 0) {
        [void]$failureReasons.Add("restore script failed with exit code $restoreExit")
    }
}

# ----------------------------------------------------------------------
# Step 4: compare row counts and Flyway versions
# ----------------------------------------------------------------------
$tables = @("crm_customer", "crm_order", "crm_appointment", "crm_metric_definition", "sys_audit_log")
if ($failureReasons.Count -eq 0) {
    Write-Log "INFO" "Step 4/5: comparing source and drill databases"
    foreach ($table in $tables) {
        $sourceCount = Get-TableCount -Database $SourceDatabase -Table $table
        $drillCount = Get-TableCount -Database $DrillDatabase -Table $table
        $matched = ($sourceCount.ok -and $drillCount.ok -and $sourceCount.value -eq $drillCount.value)
        [void]$checks.Add([ordered]@{
            name    = $table
            source  = $sourceCount.value
            drill   = $drillCount.value
            matched = $matched
        })
        if (-not $sourceCount.ok) { [void]$failureReasons.Add("source table $table not readable: $($sourceCount.message)") }
        elseif (-not $drillCount.ok) { [void]$failureReasons.Add("drill table $table not readable: $($drillCount.message)") }
        elseif (-not $matched) { [void]$failureReasons.Add("row count mismatch on $table (source=$($sourceCount.value), drill=$($drillCount.value))") }
        Write-Log "INFO" ("{0}: source={1} drill={2} matched={3}" -f $table, $sourceCount.value, $drillCount.value, $matched)
    }

    $sourceFlywayVersion = Get-FlywayVersion -Database $SourceDatabase
    $drillFlywayVersion = Get-FlywayVersion -Database $DrillDatabase
    $sourceFlywayCount = Get-FlywayCount -Database $SourceDatabase
    $drillFlywayCount = Get-FlywayCount -Database $DrillDatabase
    $flywayMatched = (-not [string]::IsNullOrWhiteSpace($sourceFlywayVersion) -and
        $sourceFlywayVersion -eq $drillFlywayVersion -and
        $sourceFlywayCount -eq $drillFlywayCount)

    [void]$checks.Add([ordered]@{
        name    = "flyway_schema_history"
        source  = ("{0} ({1} migrations)" -f $sourceFlywayVersion, $sourceFlywayCount)
        drill   = ("{0} ({1} migrations)" -f $drillFlywayVersion, $drillFlywayCount)
        matched = $flywayMatched
    })
    if (-not $flywayMatched) {
        [void]$failureReasons.Add("Flyway mismatch (source=$sourceFlywayVersion/$sourceFlywayCount, drill=$drillFlywayVersion/$drillFlywayCount)")
    }
    Write-Log "INFO" ("flyway: source={0}/{1} drill={2}/{3} matched={4}" -f $sourceFlywayVersion, $sourceFlywayCount, $drillFlywayVersion, $drillFlywayCount, $flywayMatched)
}

# ----------------------------------------------------------------------
# Step 5: report and write result
# ----------------------------------------------------------------------
$finishedAt = Get-Date
$passed = ($failureReasons.Count -eq 0)
$statusText = "FAIL"
if ($passed) { $statusText = "PASS" }
$summaryMessage = "Restore drill $statusText"
if (-not $passed) {
    $summaryMessage += ": " + (($failureReasons | Select-Object -First 3) -join "; ")
}
if ($summaryMessage.Length -gt 500) { $summaryMessage = $summaryMessage.Substring(0, 500) }

$result = [ordered]@{
    status           = $statusText
    sourceDatabase   = $SourceDatabase
    drillDatabase    = $DrillDatabase
    backupFile       = $backupFile
    startedAt        = $startedAt.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    finishedAt       = $finishedAt.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    durationSeconds  = [int][Math]::Round(($finishedAt - $startedAt).TotalSeconds, 0)
    checks           = @($checks)
    failureReasons   = @($failureReasons)
    drillKept        = [bool]$KeepDrillDatabase
    reportedBackupId = ""
    reportedVerified = $false
}
$resultFile = Join-Path $backupDirFull ("drill-result-{0}.json" -f $startedAt.ToString("yyyyMMdd_HHmmss"))

if (-not [string]::IsNullOrWhiteSpace($ApiBaseUrl) -and -not [string]::IsNullOrWhiteSpace($ApiToken) -and $backupFile) {
    Write-Log "INFO" "Reporting backup and drill result to ops API"
    $base = $ApiBaseUrl.TrimEnd('/')
    $headers = @{ Authorization = "Bearer $ApiToken" }

    $metaPath = $backupFile + ".meta.json"
    $meta = $null
    if ([System.IO.File]::Exists($metaPath)) {
        try { $meta = Get-Content -LiteralPath $metaPath -Raw | ConvertFrom-Json } catch { $meta = $null }
    }

    $startedAtText = $result.startedAt
    $finishedAtText = $result.finishedAt
    $backupCode = [System.IO.Path]::GetFileNameWithoutExtension($backupFile)
    $sizeBytes = 0
    $fileHash = ""
    if ($meta) {
        $startedAtText = $meta.startedAt
        $finishedAtText = $meta.finishedAt
        $backupCode = $meta.backupCode
        $sizeBytes = [long]$meta.sizeBytes
        $fileHash = [string]$meta.fileHash
    }

    $reportBody = [ordered]@{
        backupCode = $backupCode
        backupType = "FULL"
        status     = "SUCCESS"
        sizeBytes  = $sizeBytes
        fileHash   = $fileHash
        target     = $backupFile
        startedAt  = $startedAtText
        finishedAt = $finishedAtText
        message    = "Restore drill source backup"
    } | ConvertTo-Json -Depth 4

    try {
        $reportResponse = Invoke-RestMethod -Uri "$base/api/v1/ops/backups" -Method Post `
            -Headers $headers -ContentType "application/json" -Body $reportBody -TimeoutSec 30
        if ($reportResponse -and $reportResponse.data -and $reportResponse.data.id) {
            $result["reportedBackupId"] = [string]$reportResponse.data.id
            Write-Log "INFO" ("Backup reported, id={0}" -f $result["reportedBackupId"])

            $verifyBody = [ordered]@{
                restoreVerified = $passed
                restoreMessage  = $summaryMessage
            } | ConvertTo-Json -Depth 3
            $verifyResponse = Invoke-RestMethod -Uri ("$base/api/v1/ops/backups/{0}/verify" -f $result["reportedBackupId"]) `
                -Method Post -Headers $headers -ContentType "application/json" -Body $verifyBody -TimeoutSec 30
            $result["reportedVerified"] = $true
            Write-Log "INFO" "Restore verification reported to ops API"
        }
        else {
            Write-Log "WARN" "Backup report returned no data.id; verify step skipped"
        }
    }
    catch {
        Write-Log "WARN" ("Ops API reporting failed (ignored): {0}" -f $_.Exception.Message)
    }
}

Write-Utf8NoBom -Path $resultFile -Text (($result | ConvertTo-Json -Depth 6) + "`n")

# ----------------------------------------------------------------------
# Cleanup drill database
# ----------------------------------------------------------------------
if ($drillCreated) {
    if ($KeepDrillDatabase) {
        Write-Log "INFO" "Keeping drill database $DrillDatabase (requested)"
    }
    else {
        Write-Log "INFO" "Dropping drill database $DrillDatabase"
        $dropResult = Invoke-Native -Command $dropdbCommand.Source -Arguments @(
            "--if-exists", "--force",
            "-h", $conn.Host, "-p", [string]$conn.Port, "-U", $conn.User, $DrillDatabase
        )
        if ($dropResult.ExitCode -ne 0) { Write-Log "WARN" "dropdb failed; remove the drill database manually" }
    }
}

Write-Host ""
Write-Log "INFO" "===== RESTORE DRILL SUMMARY ====="
Write-Host ("DRILL_STATUS={0}" -f $statusText)
Write-Host ("DRILL_SOURCE_DATABASE={0}" -f $SourceDatabase)
Write-Host ("DRILL_DATABASE={0}" -f $DrillDatabase)
Write-Host ("DRILL_BACKUP_FILE={0}" -f $backupFile)
Write-Host ("DRILL_RESULT_FILE={0}" -f $resultFile)

if (-not $passed) {
    foreach ($reason in $failureReasons) { Write-Log "ERROR" ("FAIL REASON: {0}" -f $reason) }
    exit 1
}

Write-Log "INFO" "Restore drill PASSED"
exit 0
