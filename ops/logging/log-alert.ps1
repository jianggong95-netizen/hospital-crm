#Requires -Version 5.1
<#
.SYNOPSIS
    Scan a Hospital CRM log file for known failure patterns and raise alerts.

.DESCRIPTION
    Matches a small built-in rule set against the log file (default
    logs/crm-api-error.log). Matching rules are printed as a summary and,
    when -ApiBaseUrl and -ApiToken are supplied, reported to
    POST {ApiBaseUrl}/api/v1/ops/alerts/raise.

    Rules:
      OutOfMemoryError                CRITICAL
      Connection refused|reset        HIGH
      Flyway|Migration                HIGH
      Deadlock|lock timeout           HIGH

    Exit code is 0 when no rule matches and also 0 when alerts were reported;
    reporting failures are warnings only.

.PARAMETER LogFile
    Log file to scan. Default logs/crm-api-error.log

.PARAMETER ApiBaseUrl
    CRM API base URL.

.PARAMETER ApiToken
    Bearer token with crm:ops:manage permission.

.EXAMPLE
    .\log-alert.ps1 -LogFile D:\crm-run\logs\crm-api-error.log

.EXAMPLE
    .\log-alert.ps1 -ApiBaseUrl http://127.0.0.1:8080 -ApiToken $token
#>
[CmdletBinding()]
param(
    [string]$LogFile = "logs/crm-api-error.log",
    [string]$ApiBaseUrl = "",
    [string]$ApiToken = ""
)

$ErrorActionPreference = "Stop"
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    Write-Host ("[{0}] [{1}] {2}" -f $ts, $Level, $Message)
}

if (-not [System.IO.File]::Exists($LogFile)) {
    Write-Log "INFO" ("Log file not found: {0}" -f $LogFile)
    Write-Log "INFO" "Nothing to scan"
    exit 0
}

$fileInfo = Get-Item -LiteralPath $LogFile
$lines = @(Get-Content -LiteralPath $LogFile -Encoding UTF8 -ErrorAction Stop)
$searchDate = (Get-Date).ToString("yyyyMMdd")

$rules = @(
    [pscustomobject]@{
        Name     = "OutOfMemoryError"
        Pattern  = 'OutOfMemoryError'
        Severity = "CRITICAL"
        Title    = "Java OutOfMemoryError detected"
    },
    [pscustomobject]@{
        Name     = "ConnectionFailure"
        Pattern  = 'Connection refused|Connection reset'
        Severity = "HIGH"
        Title    = "Database or downstream connection failure detected"
    },
    [pscustomobject]@{
        Name     = "FlywayMigration"
        Pattern  = 'Flyway|Migration'
        Severity = "HIGH"
        Title    = "Flyway or database migration issue detected"
    },
    [pscustomobject]@{
        Name     = "DatabaseLock"
        Pattern  = 'Deadlock|lock timeout'
        Severity = "HIGH"
        Title    = "Database deadlock or lock timeout detected"
    }
)

$hits = @()

foreach ($rule in $rules) {
    $matched = @($lines | Select-String -Pattern $rule.Pattern)
    if ($matched.Count -eq 0) { continue }

    $samples = @()
    foreach ($item in ($matched | Select-Object -First 3)) {
        $text = ([string]$item.Line).Trim()
        if ($text.Length -gt 300) { $text = $text.Substring(0, 300) }
        $samples += $text
    }

    $detail = ("{0} hit(s) in {1}; samples: {2}" -f $matched.Count, $fileInfo.FullName, ($samples -join " | "))
    if ($detail.Length -gt 2000) { $detail = $detail.Substring(0, 2000) }

    $hits += [pscustomobject]@{
        Rule     = $rule
        Count    = $matched.Count
        Detail   = $detail
        DedupKey = ("LOG:{0}:{1}:{2}" -f $rule.Name, $fileInfo.Name, $searchDate)
    }
}

if ($hits.Count -eq 0) {
    Write-Log "INFO" ("No alert pattern matched in {0}" -f $fileInfo.FullName)
    exit 0
}

Write-Host ""
Write-Log "WARN" "===== LOG ALERT SUMMARY ====="
foreach ($hit in $hits) {
    Write-Log $hit.Rule.Severity ("{0}: {1} hit(s) [{2}]" -f $hit.Rule.Name, $hit.Count, $hit.Rule.Severity)
    Write-Log "INFO" ("  dedupKey={0}" -f $hit.DedupKey)
    Write-Log "INFO" ("  detail={0}" -f $hit.Detail)
}

if ([string]::IsNullOrWhiteSpace($ApiBaseUrl) -or [string]::IsNullOrWhiteSpace($ApiToken)) {
    Write-Log "INFO" "ApiBaseUrl/ApiToken not supplied; alerts were not reported"
    exit 0
}

$base = $ApiBaseUrl.TrimEnd('/')
$headers = @{ Authorization = "Bearer $ApiToken" }

foreach ($hit in $hits) {
    $payload = [ordered]@{
        alertCode = "LOG_PATTERN"
        severity  = $hit.Rule.Severity
        title     = $hit.Rule.Title
        detail    = $hit.Detail
        dedupKey  = $hit.DedupKey
        source    = "LOG"
    }
    $body = $payload | ConvertTo-Json -Depth 4
    try {
        Invoke-RestMethod -Uri "$base/api/v1/ops/alerts/raise" -Method Post `
            -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec 30 | Out-Null
        Write-Log "INFO" ("Alert reported: {0}" -f $hit.Rule.Name)
    }
    catch {
        Write-Log "WARN" ("Alert report failed (ignored): {0}" -f $_.Exception.Message)
    }
}

exit 0
