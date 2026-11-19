#!/usr/bin/env bash
#
# PostgreSQL full backup for Hospital CRM (pg_dump custom format).
#
# Connection values are resolved in this order:
#   --database-url / --database
#   PGHOST / PGPORT / PGUSER / PGPASSWORD
#   CRM_DB_URL / CRM_DB_NAME / CRM_DB_USERNAME / CRM_DB_PASSWORD
#
# Usage:
#   ./backup.sh [options]
#     --database-url=URL      JDBC or postgres URL
#     --database=NAME         database name (overrides URL)
#     --backup-dir=DIR        target directory (default ./backups)
#     --retention-days=N      keep N days (default 14)
#     --api-base-url=URL      CRM API base URL
#     --api-token=TOKEN       bearer token
#     --skip-report           do not call the ops API
#     -h, --help              show help
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DB_URL_ARG=""
DB_NAME_ARG=""
BACKUP_DIR="./backups"
RETENTION_DAYS="14"
API_BASE_URL=""
API_TOKEN=""
SKIP_REPORT=0

usage() {
    sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --database-url=*) DB_URL_ARG="${1#*=}" ;;
        --database=*)     DB_NAME_ARG="${1#*=}" ;;
        --backup-dir=*)   BACKUP_DIR="${1#*=}" ;;
        --retention-days=*) RETENTION_DAYS="${1#*=}" ;;
        --api-base-url=*) API_BASE_URL="${1#*=}" ;;
        --api-token=*)    API_TOKEN="${1#*=}" ;;
        --skip-report)    SKIP_REPORT=1 ;;
        -h|--help)        usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

log() {
    local level="$1"
    shift
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $*"
}

# ----------------------------------------------------------------------
# Resolve connection
# ----------------------------------------------------------------------
URL="${DB_URL_ARG:-${CRM_DB_URL:-}}"
HOST_NAME="${PGHOST:-}"
PORT="${PGPORT:-}"
USER_NAME="${PGUSER:-${CRM_DB_USERNAME:-crm}}"
PASSWORD="${PGPASSWORD:-${CRM_DB_PASSWORD:-}}"
DB_NAME="${DB_NAME_ARG:-}"
URL_DB_NAME=""

if [[ -n "$URL" && "$URL" =~ ^jdbc:postgresql://([^/:?]+)(:([0-9]+))?/([^?;]+) ]]; then
    [[ -z "$HOST_NAME" ]] && HOST_NAME="${BASH_REMATCH[1]}"
    [[ -z "$PORT" && -n "${BASH_REMATCH[3]}" ]] && PORT="${BASH_REMATCH[3]}"
    URL_DB_NAME="${BASH_REMATCH[4]}"
fi

[[ -z "$DB_NAME" ]] && DB_NAME="$URL_DB_NAME"
[[ -z "$DB_NAME" ]] && DB_NAME="${CRM_DB_NAME:-crm}"
[[ -z "$HOST_NAME" ]] && HOST_NAME="127.0.0.1"
[[ -z "$PORT" ]] && PORT="5432"
[[ -z "$USER_NAME" ]] && USER_NAME="crm"

export PGPASSWORD="$PASSWORD"

if ! command -v pg_dump >/dev/null 2>&1; then
    log "ERROR" "pg_dump was not found in PATH"
    exit 1
fi

mkdir -p "$BACKUP_DIR"
BACKUP_DIR="$(cd "$BACKUP_DIR" && pwd)"

START_SECONDS="$(date +%s)"
STARTED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
STAMP="$(date '+%Y%m%d_%H%M%S')"
BACKUP_CODE="crm_full_${STAMP}"
BACKUP_FILE="${BACKUP_DIR}/${BACKUP_CODE}.dump"
SHA_FILE="${BACKUP_FILE}.sha256"
META_FILE="${BACKUP_FILE}.meta.json"
PG_VERSION="$(pg_dump --version 2>/dev/null | head -n1)"

log "INFO" "Starting full backup: database=${DB_NAME} host=${HOST_NAME} port=${PORT} user=${USER_NAME}"
log "INFO" "Backup file: ${BACKUP_FILE}"

DUMP_LOG="${BACKUP_DIR}/.pg_dump_${STAMP}.log"
pg_dump -Fc -f "$BACKUP_FILE" -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" -d "$DB_NAME" >"$DUMP_LOG" 2>&1
DUMP_EXIT=$?

FINISH_SECONDS="$(date +%s)"
FINISHED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
DURATION_SECONDS=$((FINISH_SECONDS - START_SECONDS))
SIZE_BYTES=0
FILE_HASH=""

json_escape() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | tr -d '\r\n'
}

report_backup() {
    local status="$1"
    local message="$2"
    local size="$3"
    local hash="$4"

    if [[ "$SKIP_REPORT" -eq 1 || -z "$API_BASE_URL" ]]; then
        return 0
    fi

    local body
    local hash_field=""
    if [[ -n "$hash" ]]; then
        hash_field=",\"fileHash\":\"${hash}\""
    fi
    body=$(printf '{"backupCode":"%s","backupType":"FULL","status":"%s","sizeBytes":%s%s,"target":"%s","startedAt":"%s","finishedAt":"%s","message":"%s"}' \
        "$(json_escape "$BACKUP_CODE")" "$status" "$size" "$hash_field" "$(json_escape "$BACKUP_FILE")" \
        "$STARTED_AT" "$FINISHED_AT" "$(json_escape "$message")")

    local -a headers=(-H "Content-Type: application/json")
    if [[ -n "$API_TOKEN" ]]; then
        headers+=(-H "Authorization: Bearer ${API_TOKEN}")
    fi

    if curl -fsS --max-time 30 -X POST "${API_BASE_URL%/}/api/v1/ops/backups" "${headers[@]}" -d "$body" >/dev/null 2>&1; then
        log "INFO" "Backup result reported to ops API"
    else
        log "WARN" "Backup report failed (ignored)"
    fi
}

if [[ "$DUMP_EXIT" -ne 0 ]]; then
    [[ -f "$DUMP_LOG" ]] && tail -n 20 "$DUMP_LOG" | while IFS= read -r line; do log "ERROR" "$line"; done
    rm -f "$BACKUP_FILE" "$DUMP_LOG"
    report_backup "FAILED" "pg_dump exited with code ${DUMP_EXIT}" 0 ""
    log "ERROR" "pg_dump failed with exit code ${DUMP_EXIT}"
    exit 1
fi

if [[ ! -f "$BACKUP_FILE" ]]; then
    log "ERROR" "pg_dump reported success but the dump file does not exist"
    exit 1
fi

rm -f "$DUMP_LOG"
SIZE_BYTES="$(wc -c < "$BACKUP_FILE" | tr -d ' ')"

if command -v sha256sum >/dev/null 2>&1; then
    FILE_HASH="$(sha256sum "$BACKUP_FILE" | awk '{print tolower($1)}')"
elif command -v shasum >/dev/null 2>&1; then
    FILE_HASH="$(shasum -a 256 "$BACKUP_FILE" | awk '{print tolower($1)}')"
else
    log "ERROR" "Neither sha256sum nor shasum is available"
    exit 1
fi

printf '%s  %s\n' "$FILE_HASH" "$(basename "$BACKUP_FILE")" >"$SHA_FILE"

cat >"$META_FILE" <<EOF
{
  "backupCode": "${BACKUP_CODE}",
  "sizeBytes": ${SIZE_BYTES},
  "fileHash": "${FILE_HASH}",
  "startedAt": "${STARTED_AT}",
  "finishedAt": "${FINISHED_AT}",
  "database": "$(json_escape "$DB_NAME")",
  "pgVersion": "$(json_escape "$PG_VERSION")",
  "durationSeconds": ${DURATION_SECONDS}
}
EOF

log "INFO" "Backup completed: size=${SIZE_BYTES} bytes, duration=${DURATION_SECONDS}s, sha256=${FILE_HASH}"

# ----------------------------------------------------------------------
# Retention cleanup
# ----------------------------------------------------------------------
if [[ "$RETENTION_DAYS" -gt 0 ]]; then
    REMOVED=0
    while IFS= read -r -d '' old_dump; do
        rm -f "$old_dump" "${old_dump}.sha256" "${old_dump}.meta.json"
        REMOVED=$((REMOVED + 1))
        log "INFO" "Removed expired backup: $(basename "$old_dump")"
    done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'crm_full_*.dump' -mtime +"${RETENTION_DAYS}" -print0)
    log "INFO" "Retention cleanup finished, removed ${REMOVED} backup set(s) older than ${RETENTION_DAYS} day(s)"
fi

report_backup "SUCCESS" "Full backup completed" "$SIZE_BYTES" "$FILE_HASH"

echo ""
log "INFO" "===== BACKUP SUMMARY ====="
echo "BACKUP_CODE=${BACKUP_CODE}"
echo "BACKUP_FILE=${BACKUP_FILE}"
echo "BACKUP_SHA256=${FILE_HASH}"
echo "BACKUP_SIZE_BYTES=${SIZE_BYTES}"
echo "BACKUP_DATABASE=${DB_NAME}"
echo "BACKUP_DURATION_SECONDS=${DURATION_SECONDS}"

exit 0
