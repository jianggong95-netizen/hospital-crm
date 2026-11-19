#Requires -Version 5.1
<#
.SYNOPSIS
    PostgreSQL full backup for Hospital CRM (pg_dump custom format).

.DESCRIPTION
    Creates a compressed custom-format dump, a SHA-256 sidecar, a JSON metadata
    sidecar and optionally reports the result to the CRM ops API.

    Connection values are resolved in this order:
      -DatabaseUrl / -Database parameters
      PGHOST / PGPORT / PGUSER / PGPASSWORD
      CRM_DB_URL / CRM_DB_NAME / CRM_DB_USERNAME / CRM_DB_PASSWORD

.PARAMETER DatabaseUrl
    JDBC or postgres URL, for example jdbc:postgresql://127.0.0.1:5432/crm

.PARAMETER Database
    Database name. Overrides the name contained in -DatabaseUrl.

.PARAMETER BackupDir
    Directory that receives crm_full_yyyyMMdd_HHmmss.dump and sidecar files.

.PARAMETER RetentionDays
    Delete backup sets older than this many days. Default 14.

.PARAMETER ApiBaseUrl
    CRM API base URL, for example http://127.0.0.1:8080

.PARAMETER ApiToken
    Bearer token with crm:ops:manage permission.

.PARAMETER SkipReport
    Do not call the ops API even when -ApiBaseUrl is supplied.

.EXAMPLE
    .\backup.ps1 -DatabaseUrl "jdbc:postgresql://127.0.0.1:5432/crm" -BackupDir D:\crm-backups

.EXAMPLE
    .\backup.ps1 -ApiBaseUrl http://127.0.0.1:8080 -ApiToken $token
    (connection taken from PGHOST/PGPORT/PGUSER/PGPASSWORD or CRM_DB_* environment variables)
#>
[CmdletBinding()]
param(
    [string]$DatabaseUrl = "",
    [string]$Database = "",
    [string]$BackupDir = "./backups",
    [int]$RetentionDays = 14,
    [string]$ApiBaseUrl = "",
    [string]$ApiToken = "",
    [switch]$SkipReport
)

$ErrorActionPreference = "Stop"
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

$script:ExitCode = 0

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
    param([string]$UrlOverride, [string]$DbOverride)

    $url = $UrlOverride
    if ([string]::IsNullOrWhiteSpace($url)) { $url = $env:CRM_DB_URL }

    $hostName = $env:PGHOST
    $port = 0
    if (-not [string]::IsNullOrWhiteSpace($env:PGPORT)) {
        [void][int]::TryParse($env:PGPORT, [ref]$port)
    }

    $userName = $env:PGUSER
    if ([string]::IsNullOrWhiteSpace($userName)) { $userName = $env:CRM_DB_USERNAME }

    $password = $env:PGPASSWORD
    if ([string]::IsNullOrWhiteSpace($password)) { $password = $env:CRM_DB_PASSWORD }

    $dbName = $DbOverride
    $urlDbName = ""

    if (-not [string]::IsNullOrWhiteSpace($url)) {
        if ($url -match '^jdbc:postgresql://([^/:?]+)(?::(\d+))?/([^?;]+)') {
            if ([string]::IsNullOrWhiteSpace($hostName)) { $hostName = $Matches[1] }
            if ($port -le 0 -and $Matches[2]) { $port = [int]$Matches[2] }
            $urlDbName = $Matches[3]
        }
    }

    if ([string]::IsNullOrWhiteSpace($dbName)) { $dbName = $urlDbName }
    if ([string]::IsNullOrWhiteSpace($dbName)) { $dbName = $env:CRM_DB_NAME }
    if ([string]::IsNullOrWhiteSpace($dbName)) { $dbName = "crm" }
    if ([string]::IsNullOrWhiteSpace($hostName)) { $hostName = "127.0.0.1" }
    if ($port -le 0) { $port = 5432 }
    if ([string]::IsNullOrWhiteSpace($userName)) { $userName = "crm" }

    return @{
        Host     = $hostName
        Port     = $port
        User     = $userName
        Password = $password
        Database = $dbName
    }
}

function Send-BackupReport {
    param(
        [string]$Status,
        [string]$Message,
        [string]$Target,
        [string]$BackupCode,
        [long]$SizeBytes,
        [string]$FileHash,
        [string]$StartedAt,
        [string]$FinishedAt
    )

    if ($SkipReport -or [string]::IsNullOrWhiteSpace($ApiBaseUrl)) { return $null }

    $base = $ApiBaseUrl.TrimEnd('/')
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($ApiToken)) {
        $headers["Authorization"] = "Bearer $ApiToken"
    }

    $payload = [ordered]@{
        backupCode = $BackupCode
        backupType = "FULL"
        status     = $Status
        sizeBytes  = $SizeBytes
        target     = $Target
        startedAt  = $StartedAt
        finishedAt = $FinishedAt
        message    = $Message
    }
    if (-not [string]::IsNullOrWhiteSpace($FileHash)) { $payload["fileHash"] = $FileHash }

    $body = $payload | ConvertTo-Json -Depth 4
    try {
        $response = Invoke-RestMethod -Uri "$base/api/v1/ops/backups" -Method Post `
            -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec 30
        Write-Log "INFO" "Backup result reported to ops API"
        return $response
    }
    catch {
        Write-Log "WARN" ("Backup report failed (ignored): {0}" -f $_.Exception.Message)
        return $null
    }
}

# ----------------------------------------------------------------------
# Resolve connection and preflight
# ----------------------------------------------------------------------
$conn = Resolve-DbConnection -UrlOverride $DatabaseUrl -DbOverride $Database
$env:PGPASSWORD = $conn.Password

$pgDumpCommand = Get-Command pg_dump -ErrorAction SilentlyContinue
if (-not $pgDumpCommand) {
    Fail "pg_dump was not found in PATH. Install PostgreSQL client tools or add them to PATH."
}

$backupDirFull = [System.IO.Path]::GetFullPath($BackupDir)
if (-not [System.IO.Directory]::Exists($backupDirFull)) {
    [void][System.IO.Directory]::CreateDirectory($backupDirFull)
}

$startedAt = Get-Date
$startedAtUtc = $startedAt.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$stamp = $startedAt.ToString("yyyyMMdd_HHmmss")
$backupCode = "crm_full_$stamp"
$backupFile = Join-Path $backupDirFull ($backupCode + ".dump")
$shaFile = $backupFile + ".sha256"
$metaFile = $backupFile + ".meta.json"

Write-Log "INFO" ("Starting full backup: database={0} host={1} port={2} user={3}" -f $conn.Database, $conn.Host, $conn.Port, $conn.User)
Write-Log "INFO" "Backup file: $backupFile"

try {
    $versionResult = Invoke-Native -Command $pgDumpCommand.Source -Arguments @("--version")
    $pgVersion = (($versionResult.Output | ForEach-Object { [string]$_ }) -join " ").Trim()
}
catch {
    $pgVersion = "unknown"
}

# ----------------------------------------------------------------------
# Run pg_dump
# ----------------------------------------------------------------------
$dumpResult = Invoke-Native -Command $pgDumpCommand.Source -Arguments @(
    "-Fc", "-f", $backupFile,
    "-h", $conn.Host,
    "-p", [string]$conn.Port,
    "-U", $conn.User,
    "-d", $conn.Database
)
$dumpOutput = $dumpResult.Output
$dumpExit = $dumpResult.ExitCode

$finishedAt = Get-Date
$durationSeconds = [int][Math]::Round(($finishedAt - $startedAt).TotalSeconds, 0)
$finishedAtUtc = $finishedAt.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

if ($dumpExit -ne 0) {
    if ($dumpOutput) { $dumpOutput | ForEach-Object { Write-Log "ERROR" ([string]$_) } }
    if ([System.IO.File]::Exists($backupFile)) { [System.IO.File]::Delete($backupFile) }
    Send-BackupReport -Status "FAILED" -Message ("pg_dump exited with code {0}" -f $dumpExit) `
        -Target $backupFile -BackupCode $backupCode -SizeBytes 0 -FileHash "" `
        -StartedAt $startedAtUtc -FinishedAt $finishedAtUtc | Out-Null
    Fail ("pg_dump failed with exit code {0}" -f $dumpExit)
}

if (-not [System.IO.File]::Exists($backupFile)) {
    Fail "pg_dump reported success but the dump file does not exist."
}

$sizeBytes = (Get-Item -LiteralPath $backupFile).Length
$fileHash = (Get-FileHash -LiteralPath $backupFile -Algorithm SHA256).Hash.ToLowerInvariant()

$shaText = "{0}  {1}" -f $fileHash, [System.IO.Path]::GetFileName($backupFile)
Write-Utf8NoBom -Path $shaFile -Text ($shaText + "`n")

$meta = [ordered]@{
    backupCode      = $backupCode
    sizeBytes       = [long]$sizeBytes
    fileHash        = $fileHash
    startedAt       = $startedAtUtc
    finishedAt      = $finishedAtUtc
    database        = $conn.Database
    pgVersion       = $pgVersion
    durationSeconds = $durationSeconds
}
Write-Utf8NoBom -Path $metaFile -Text (($meta | ConvertTo-Json -Depth 4) + "`n")

Write-Log "INFO" ("Backup completed: size={0} bytes, duration={1}s, sha256={2}" -f $sizeBytes, $durationSeconds, $fileHash)

# ----------------------------------------------------------------------
# Retention cleanup
# ----------------------------------------------------------------------
if ($RetentionDays -gt 0) {
    $cutoff = (Get-Date).AddDays(-1 * $RetentionDays)
    $removedSets = 0
    $oldDumps = Get-ChildItem -Path $backupDirFull -Filter "crm_full_*.dump" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -lt $cutoff }
    foreach ($item in $oldDumps) {
        foreach ($suffix in @("", ".sha256", ".meta.json")) {
            $candidate = $item.FullName + $suffix
            if ([System.IO.File]::Exists($candidate)) {
                [System.IO.File]::Delete($candidate)
            }
        }
        $removedSets++
        Write-Log "INFO" ("Removed expired backup: {0}" -f $item.Name)
    }
    Write-Log "INFO" ("Retention cleanup finished, removed {0} backup set(s) older than {1} day(s)" -f $removedSets, $RetentionDays)
}

# ----------------------------------------------------------------------
# Report to ops API (failure is only a warning)
# ----------------------------------------------------------------------
$report = Send-BackupReport -Status "SUCCESS" -Message "Full backup completed" `
    -Target $backupFile -BackupCode $backupCode -SizeBytes $sizeBytes -FileHash $fileHash `
    -StartedAt $startedAtUtc -FinishedAt $finishedAtUtc

Write-Host ""
Write-Log "INFO" "===== BACKUP SUMMARY ====="
Write-Output ("BACKUP_CODE={0}" -f $backupCode)
Write-Output ("BACKUP_FILE={0}" -f $backupFile)
Write-Output ("BACKUP_SHA256={0}" -f $fileHash)
Write-Output ("BACKUP_SIZE_BYTES={0}" -f $sizeBytes)
Write-Output ("BACKUP_DATABASE={0}" -f $conn.Database)
Write-Output ("BACKUP_DURATION_SECONDS={0}" -f $durationSeconds)
if ($report -and $report.data -and $report.data.id) {
    Write-Output ("BACKUP_REPORT_ID={0}" -f $report.data.id)
}

exit 0
