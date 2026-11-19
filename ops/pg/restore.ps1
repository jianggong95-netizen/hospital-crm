#Requires -Version 5.1
<#
.SYNOPSIS
    Restore a PostgreSQL custom-format backup for Hospital CRM.

.DESCRIPTION
    Verifies the SHA-256 sidecar when present, creates the target database when
    -CreateDatabase is supplied, runs pg_restore --clean --if-exists, then checks
    flyway_schema_history and key CRM tables.

    Connection values are resolved from PGHOST/PGPORT/PGUSER/PGPASSWORD or
    CRM_DB_URL/CRM_DB_NAME/CRM_DB_USERNAME/CRM_DB_PASSWORD.

.PARAMETER BackupFile
    Path to a .dump file produced by backup.ps1 or backup.sh.

.PARAMETER TargetDatabase
    Database to restore into. Must already exist unless -CreateDatabase is used.

.PARAMETER CreateDatabase
    Create the target database when it does not exist.

.PARAMETER Force
    Required when TargetDatabase equals the configured CRM database name.
    This is an intentional guard against restoring over the live database.

.EXAMPLE
    .\restore.ps1 -BackupFile D:\crm-backups\crm_full_20260101_020000.dump -TargetDatabase crm_restore_test -CreateDatabase

.EXAMPLE
    .\restore.ps1 -BackupFile .\backups\crm_full_20260101_020000.dump -TargetDatabase crm -Force
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,

    [Parameter(Mandatory = $true)]
    [string]$TargetDatabase,

    [switch]$CreateDatabase,

    [switch]$Force
)

$ErrorActionPreference = "Stop"

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
    param([string]$DbOverride)

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

# ----------------------------------------------------------------------
# Preflight
# ----------------------------------------------------------------------
$conn = Resolve-DbConnection
$env:PGPASSWORD = $conn.Password

if ([string]::IsNullOrWhiteSpace($TargetDatabase) -or $TargetDatabase -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
    Fail "TargetDatabase must match ^[A-Za-z_][A-Za-z0-9_]*$"
}
if (@("postgres", "template0", "template1") -contains $TargetDatabase.ToLowerInvariant()) {
    Fail "Refusing to restore into a PostgreSQL system database: $TargetDatabase"
}
if ($TargetDatabase -eq $conn.Database -and -not $Force) {
    Write-Log "ERROR" "Target database '$TargetDatabase' is the configured CRM database."
    Write-Log "ERROR" "Refusing to overwrite the live database without -Force. This is an anti-mistake guard."
    exit 1
}

if (-not [System.IO.File]::Exists($BackupFile)) {
    Fail "Backup file not found: $BackupFile"
}
$backupFull = [System.IO.Path]::GetFullPath($BackupFile)

$psqlCommand = Get-Command psql -ErrorAction SilentlyContinue
$pgRestoreCommand = Get-Command pg_restore -ErrorAction SilentlyContinue
$createdbCommand = Get-Command createdb -ErrorAction SilentlyContinue
if (-not $psqlCommand) { Fail "psql was not found in PATH" }
if (-not $pgRestoreCommand) { Fail "pg_restore was not found in PATH" }
if (-not $createdbCommand) { Fail "createdb was not found in PATH" }

# ----------------------------------------------------------------------
# SHA-256 verification
# ----------------------------------------------------------------------
$shaFile = $backupFull + ".sha256"
if ([System.IO.File]::Exists($shaFile)) {
    $firstLine = Get-Content -LiteralPath $shaFile -TotalCount 1
    $expected = (($firstLine -split '\s+') | Where-Object { $_ -ne "" } | Select-Object -First 1)
    if ($expected) {
        $expected = $expected.Trim().ToLowerInvariant()
        $actual = (Get-FileHash -LiteralPath $backupFull -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($expected -ne $actual) {
            Fail ("SHA-256 mismatch: expected {0}, actual {1}" -f $expected, $actual)
        }
        Write-Log "INFO" "SHA-256 verified: $actual"
    }
}
else {
    Write-Log "WARN" "SHA-256 sidecar not found; skipping integrity check"
}

# ----------------------------------------------------------------------
# Target database existence / creation
# ----------------------------------------------------------------------
$existsResult = Invoke-Native -Command $psqlCommand.Source -Arguments @(
    "-h", $conn.Host, "-p", [string]$conn.Port, "-U", $conn.User, "-d", "postgres",
    "-t", "-A", "-v", "ON_ERROR_STOP=1",
    "-c", "SELECT 1 FROM pg_database WHERE datname = '$TargetDatabase'"
)
if ($existsResult.ExitCode -ne 0) {
    Fail "Unable to query pg_database. Check PGHOST/PGPORT/PGUSER/PGPASSWORD."
}
$existsOutput = $existsResult.Output
$targetExists = (($existsOutput -join "").Trim() -eq "1")

if (-not $targetExists) {
    if (-not $CreateDatabase) {
        Fail "Target database '$TargetDatabase' does not exist. Re-run with -CreateDatabase."
    }
    Write-Log "INFO" "Creating database $TargetDatabase"
    $createResult = Invoke-Native -Command $createdbCommand.Source -Arguments @(
        "-h", $conn.Host, "-p", [string]$conn.Port, "-U", $conn.User, $TargetDatabase
    )
    if ($createResult.ExitCode -ne 0) { Fail "createdb failed for $TargetDatabase" }
}
else {
    Write-Log "INFO" "Target database '$TargetDatabase' already exists"
}

# ----------------------------------------------------------------------
# pg_restore
# ----------------------------------------------------------------------
Write-Log "INFO" "Restoring $backupFull into $TargetDatabase"
$restoreResult = Invoke-Native -Command $pgRestoreCommand.Source -Arguments @(
    "--clean", "--if-exists",
    "-h", $conn.Host, "-p", [string]$conn.Port, "-U", $conn.User,
    "-d", $TargetDatabase, "-Fc", $backupFull
)
$restoreOutput = $restoreResult.Output
$restoreExit = $restoreResult.ExitCode
if ($restoreOutput) {
    foreach ($line in $restoreOutput) { Write-Log "INFO" ([string]$line) }
}
if ($restoreExit -ne 0) {
    Write-Log "WARN" ("pg_restore returned exit code {0}; verification will decide the final result" -f $restoreExit)
}

# ----------------------------------------------------------------------
# Verification
# ----------------------------------------------------------------------
$tables = @("crm_customer", "crm_order", "crm_appointment", "crm_metric_definition", "sys_audit_log")
$failedChecks = @()

foreach ($table in $tables) {
    $cmd = "SELECT to_regclass('public.$table') IS NOT NULL"
    $checkResult = Invoke-Native -Command $psqlCommand.Source -Arguments @(
        "-h", $conn.Host, "-p", [string]$conn.Port, "-U", $conn.User, "-d", $TargetDatabase,
        "-t", "-A", "-v", "ON_ERROR_STOP=1", "-c", $cmd
    )
    $ok = (($checkResult.Output -join "").Trim() -eq "t")
    if ($ok) {
        Write-Log "INFO" ("Table check OK: {0}" -f $table)
    }
    else {
        Write-Log "ERROR" ("Table check FAILED: {0}" -f $table)
        $failedChecks += $table
    }
}

$flywayVersionResult = Invoke-Native -Command $psqlCommand.Source -Arguments @(
    "-h", $conn.Host, "-p", [string]$conn.Port, "-U", $conn.User, "-d", $TargetDatabase,
    "-t", "-A", "-v", "ON_ERROR_STOP=1",
    "-c", "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1"
)
$flywayVersionText = (($flywayVersionResult.Output -join "").Trim())
$flywayCountResult = Invoke-Native -Command $psqlCommand.Source -Arguments @(
    "-h", $conn.Host, "-p", [string]$conn.Port, "-U", $conn.User, "-d", $TargetDatabase,
    "-t", "-A", "-v", "ON_ERROR_STOP=1",
    "-c", "SELECT count(*) FROM flyway_schema_history WHERE success = true"
)
$flywayCountText = (($flywayCountResult.Output -join "").Trim())

if (-not $flywayVersionText) {
    Write-Log "ERROR" "flyway_schema_history check FAILED (no applied migration found)"
    $failedChecks += "flyway_schema_history"
}
else {
    Write-Log "INFO" ("Flyway latest version: {0} (applied migrations: {1})" -f $flywayVersionText, $flywayCountText)
}

Write-Host ""
Write-Log "INFO" "===== RESTORE SUMMARY ====="
Write-Host ("RESTORE_TARGET_DATABASE={0}" -f $TargetDatabase)
Write-Host ("RESTORE_BACKUP_FILE={0}" -f $backupFull)
Write-Host ("RESTORE_FLYWAY_VERSION={0}" -f $flywayVersionText)
Write-Host ("RESTORE_FLYWAY_COUNT={0}" -f $flywayCountText)

if ($failedChecks.Count -gt 0) {
    Write-Log "ERROR" ("Restore verification FAILED: {0}" -f (($failedChecks | Select-Object -Unique) -join ", "))
    exit 1
}

Write-Log "INFO" "Restore verification PASSED"
exit 0
