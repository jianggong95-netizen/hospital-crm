#!/usr/bin/env bash
#
# Scan a Hospital CRM log file for known failure patterns and raise alerts.
#
# Default file: logs/crm-api-error.log
# Rules:
#   OutOfMemoryError             CRITICAL
#   Connection refused|reset     HIGH
#   Flyway|Migration             HIGH
#   Deadlock|lock timeout        HIGH
#
# Matching rules are printed and, when --api-base-url and --api-token are
# supplied, reported to POST {ApiBaseUrl}/api/v1/ops/alerts/raise.
# Exit code is 0 when nothing matches and 0 after reporting.
#
# Usage:
#   ./log-alert.sh [--log-file=FILE] [--api-base-url=URL] [--api-token=TOKEN]
#
set -uo pipefail

LOG_FILE="logs/crm-api-error.log"
API_BASE_URL=""
API_TOKEN=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --log-file=*)      LOG_FILE="${1#*=}" ;;
        --api-base-url=*)  API_BASE_URL="${1#*=}" ;;
        --api-token=*)     API_TOKEN="${1#*=}" ;;
        -h|--help)
            sed -n '2,18p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

log() {
    local level="$1"
    shift
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $*"
}

json_escape() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | tr -d '\r\n'
}

if [[ ! -f "$LOG_FILE" ]]; then
    log "INFO" "Log file not found: ${LOG_FILE}"
    log "INFO" "Nothing to scan"
    exit 0
fi

FILE_NAME="$(basename "$LOG_FILE")"
SEARCH_DATE="$(date '+%Y%m%d')"

RULE_NAMES=("OutOfMemoryError" "ConnectionFailure" "FlywayMigration" "DatabaseLock")
RULE_PATTERNS=('OutOfMemoryError' 'Connection refused|Connection reset' 'Flyway|Migration' 'Deadlock|lock timeout')
RULE_SEVERITIES=("CRITICAL" "HIGH" "HIGH" "HIGH")
RULE_TITLES=(
    "Java OutOfMemoryError detected"
    "Database or downstream connection failure detected"
    "Flyway or database migration issue detected"
    "Database deadlock or lock timeout detected"
)

declare -a HIT_NAMES=()
declare -a HIT_SEVERITIES=()
declare -a HIT_TITLES=()
declare -a HIT_DETAILS=()
declare -a HIT_DEDUP=()

for i in "${!RULE_NAMES[@]}"; do
    pattern="${RULE_PATTERNS[$i]}"
    count="$(grep -E -c "$pattern" "$LOG_FILE" 2>/dev/null || true)"
    [[ -z "$count" ]] && count=0
    if [[ "$count" -le 0 ]]; then
        continue
    fi

    samples="$(grep -E -m3 "$pattern" "$LOG_FILE" 2>/dev/null | cut -c1-300 | tr '\n' '|' || true)"
    detail="${count} hit(s) in $(cd "$(dirname "$LOG_FILE")" && pwd)/${FILE_NAME}; samples: ${samples}"

    HIT_NAMES+=("${RULE_NAMES[$i]}")
    HIT_SEVERITIES+=("${RULE_SEVERITIES[$i]}")
    HIT_TITLES+=("${RULE_TITLES[$i]}")
    HIT_DETAILS+=("${detail}")
    HIT_DEDUP+=("LOG:${RULE_NAMES[$i]}:${FILE_NAME}:${SEARCH_DATE}")
done

if [[ "${#HIT_NAMES[@]}" -eq 0 ]]; then
    log "INFO" "No alert pattern matched in ${LOG_FILE}"
    exit 0
fi

echo ""
log "WARN" "===== LOG ALERT SUMMARY ====="
for i in "${!HIT_NAMES[@]}"; do
    log "${HIT_SEVERITIES[$i]}" "${HIT_NAMES[$i]}: ${HIT_DETAILS[$i]}"
    log "INFO" "  dedupKey=${HIT_DEDUP[$i]}"
done

if [[ -z "$API_BASE_URL" || -z "$API_TOKEN" ]]; then
    log "INFO" "ApiBaseUrl/ApiToken not supplied; alerts were not reported"
    exit 0
fi

for i in "${!HIT_NAMES[@]}"; do
    BODY=$(printf '{"alertCode":"LOG_PATTERN","severity":"%s","title":"%s","detail":"%s","dedupKey":"%s","source":"LOG"}' \
        "${HIT_SEVERITIES[$i]}" "$(json_escape "${HIT_TITLES[$i]}")" \
        "$(json_escape "${HIT_DETAILS[$i]}")" "$(json_escape "${HIT_DEDUP[$i]}")")
    if curl -fsS --max-time 30 -X POST "${API_BASE_URL%/}/api/v1/ops/alerts/raise" \
        -H "Content-Type: application/json" -H "Authorization: Bearer ${API_TOKEN}" \
        -d "$BODY" >/dev/null 2>&1; then
        log "INFO" "Alert reported: ${HIT_NAMES[$i]}"
    else
        log "WARN" "Alert report failed (ignored): ${HIT_NAMES[$i]}"
    fi
done

exit 0
