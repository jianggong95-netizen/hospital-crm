#!/usr/bin/env bash
#
# PostgreSQL restore drill for Hospital CRM.
#
# Creates a fresh backup, restores it into a temporary drill database, compares
# row counts of key tables and the Flyway version, writes drill-result-<ts>.json
# and optionally reports to the CRM ops API. The drill database is dropped at
# the end unless --keep-drill-database is supplied.
#
# Usage:
#   ./drill.sh [options]
#     --source-database=NAME    source database (default CRM_DB_NAME or crm)
#     --drill-database=NAME     temporary database name
#     --backup-dir=DIR          backup directory (default ./backups)
#     --api-base-url=URL        CRM API base URL
#     --api-token=TOKEN         bearer token
#     --keep-drill-database     keep the drill database
#     -h, --help                show help
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SOURCE_DB=""
DRILL_DB=""
BACKUP_DIR="./backups"
API_BASE_URL=""
API_TOKEN=""
KEEP_DRILL=0

usage() {
    sed -n '2,18p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --source-database=*)  SOURCE_DB="${1#*=}" ;;
        --drill-database=*)   DRILL_DB="${1#*=}" ;;
        --backup-dir=*)       BACKUP_DIR="${1#*=}" ;;
        --api-base-url=*)     API_BASE_URL="${1#*=}" ;;
        --api-token=*)        API_TOKEN="${1#*=}" ;;
        --keep-drill-database) KEEP_DRILL=1 ;;
        -h|--help)            usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

log() {
    local level="$1"
    shift
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $*"
}

fail_step() {
    log "ERROR" "$1"
    FAILURE_REASONS+=("$1")
}

json_escape() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | tr -d '\r\n'
}

# ----------------------------------------------------------------------
# Resolve connection
# ----------------------------------------------------------------------
URL="${CRM_DB_URL:-}"
HOST_NAME="${PGHOST:-}"
PORT="${PGPORT:-}"
USER_NAME="${PGUSER:-${CRM_DB_USERNAME:-crm}}"
PASSWORD="${PGPASSWORD:-${CRM_DB_PASSWORD:-}}"
URL_DB_NAME=""

if [[ -n "$URL" && "$URL" =~ ^jdbc:postgresql://([^/:?]+)(:([0-9]+))?/([^?;]+) ]]; then
    [[ -z "$HOST_NAME" ]] && HOST_NAME="${BASH_REMATCH[1]}"
    [[ -z "$PORT" && -n "${BASH_REMATCH[3]}" ]] && PORT="${BASH_REMATCH[3]}"
    URL_DB_NAME="${BASH_REMATCH[4]}"
fi

[[ -z "$HOST_NAME" ]] && HOST_NAME="127.0.0.1"
[[ -z "$PORT" ]] && PORT="5432"
[[ -z "$USER_NAME" ]] && USER_NAME="crm"
[[ -z "$SOURCE_DB" ]] && SOURCE_DB="${CRM_DB_NAME:-$URL_DB_NAME}"
[[ -z "$SOURCE_DB" ]] && SOURCE_DB="crm"
STAMP="$(date '+%Y%m%d%H%M%S')"
[[ -z "$DRILL_DB" ]] && DRILL_DB="crm_restore_drill_${STAMP}"

export PGPASSWORD="$PASSWORD"

# ----------------------------------------------------------------------
# Preflight
# ----------------------------------------------------------------------
for tool in psql createdb dropdb pg_dump pg_restore; do
    command -v "$tool" >/dev/null 2>&1 || { log "ERROR" "$tool was not found in PATH"; exit 1; }
done

if [[ ! "$SOURCE_DB" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    log "ERROR" "Invalid source database name"; exit 1
fi
if [[ ! "$DRILL_DB" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    log "ERROR" "Invalid drill database name"; exit 1
fi
if [[ "$SOURCE_DB" == "$DRILL_DB" ]]; then
    log "ERROR" "Drill database must differ from source database"; exit 1
fi

mkdir -p "$BACKUP_DIR"
BACKUP_DIR="$(cd "$BACKUP_DIR" && pwd)"

STARTED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
START_SECONDS="$(date +%s)"
RESULT_FILE="${BACKUP_DIR}/drill-result-$(date '+%Y%m%d_%H%M%S').json"

declare -a FAILURE_REASONS=()
declare -a CHECK_JSON=()

# ----------------------------------------------------------------------
# Step 1: fresh backup
# ----------------------------------------------------------------------
log "INFO" "Step 1/5: creating a fresh backup of ${SOURCE_DB}"
BACKUP_OUT="$(bash "${SCRIPT_DIR}/backup.sh" --database="$SOURCE_DB" --backup-dir="$BACKUP_DIR" --skip-report 2>&1)"
BACKUP_EXIT=$?
printf '%s\n' "$BACKUP_OUT"

BACKUP_FILE=""
if [[ "$BACKUP_EXIT" -ne 0 ]]; then
    fail_step "backup script failed with exit code ${BACKUP_EXIT}"
else
    BACKUP_FILE="$(printf '%s\n' "$BACKUP_OUT" | sed -n 's/^BACKUP_FILE=//p' | tail -n1)"
    [[ -z "$BACKUP_FILE" ]] && fail_step "backup script did not report BACKUP_FILE"
    [[ -n "$BACKUP_FILE" && ! -f "$BACKUP_FILE" ]] && fail_step "backup file does not exist: ${BACKUP_FILE}"
fi

# ----------------------------------------------------------------------
# Step 2: create drill database
# ----------------------------------------------------------------------
DRILL_CREATED=0
if [[ "${#FAILURE_REASONS[@]}" -eq 0 ]]; then
    log "INFO" "Step 2/5: creating drill database ${DRILL_DB}"
    EXISTS="$(psql -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" -d postgres -t -A \
        -c "SELECT 1 FROM pg_database WHERE datname = '${DRILL_DB}'" 2>&1)"
    if [[ "$(printf '%s' "$EXISTS" | tr -d '[:space:]')" == "1" ]]; then
        fail_step "drill database already exists: ${DRILL_DB}"
    elif createdb -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" "$DRILL_DB"; then
        DRILL_CREATED=1
    else
        fail_step "createdb failed for ${DRILL_DB}"
    fi
fi

# ----------------------------------------------------------------------
# Step 3: restore into drill database
# ----------------------------------------------------------------------
if [[ "${#FAILURE_REASONS[@]}" -eq 0 ]]; then
    log "INFO" "Step 3/5: restoring backup into ${DRILL_DB}"
    bash "${SCRIPT_DIR}/restore.sh" --backup-file="$BACKUP_FILE" --target-database="$DRILL_DB"
    RESTORE_EXIT=$?
    [[ "$RESTORE_EXIT" -ne 0 ]] && fail_step "restore script failed with exit code ${RESTORE_EXIT}"
fi

# ----------------------------------------------------------------------
# Step 4: compare row counts and Flyway versions
# ----------------------------------------------------------------------
TABLES=("crm_customer" "crm_order" "crm_appointment" "crm_metric_definition" "sys_audit_log")
SOURCE_FLYWAY_VERSION=""
DRILL_FLYWAY_VERSION=""
SOURCE_FLYWAY_COUNT=""
DRILL_FLYWAY_COUNT=""

if [[ "${#FAILURE_REASONS[@]}" -eq 0 ]]; then
    log "INFO" "Step 4/5: comparing source and drill databases"
    for table in "${TABLES[@]}"; do
        SRC="$(psql -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" -d "$SOURCE_DB" -t -A -v ON_ERROR_STOP=1 \
            -c "SELECT count(*) FROM ${table}" 2>&1)"
        SRC_EXIT=$?
        DRILL="$(psql -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" -d "$DRILL_DB" -t -A -v ON_ERROR_STOP=1 \
            -c "SELECT count(*) FROM ${table}" 2>&1)"
        DRILL_EXIT=$?
        SRC="$(printf '%s' "$SRC" | tr -d '[:space:]')"
        DRILL="$(printf '%s' "$DRILL" | tr -d '[:space:]')"

        MATCHED="false"
        if [[ "$SRC_EXIT" -ne 0 ]]; then
            fail_step "source table ${table} not readable"
        elif [[ "$DRILL_EXIT" -ne 0 ]]; then
            fail_step "drill table ${table} not readable"
        elif [[ "$SRC" == "$DRILL" ]]; then
            MATCHED="true"
        else
            fail_step "row count mismatch on ${table} (source=${SRC}, drill=${DRILL})"
        fi
        CHECK_JSON+=("{\"name\":\"$(json_escape "$table")\",\"source\":\"$(json_escape "$SRC")\",\"drill\":\"$(json_escape "$DRILL")\",\"matched\":${MATCHED}}")
        log "INFO" "${table}: source=${SRC} drill=${DRILL} matched=${MATCHED}"
    done

    SOURCE_FLYWAY_VERSION="$(psql -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" -d "$SOURCE_DB" -t -A -v ON_ERROR_STOP=1 \
        -c "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"
    DRILL_FLYWAY_VERSION="$(psql -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" -d "$DRILL_DB" -t -A -v ON_ERROR_STOP=1 \
        -c "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1" 2>/dev/null | tr -d '[:space:]')"
    SOURCE_FLYWAY_COUNT="$(psql -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" -d "$SOURCE_DB" -t -A -v ON_ERROR_STOP=1 \
        -c "SELECT count(*) FROM flyway_schema_history WHERE success = true" 2>/dev/null | tr -d '[:space:]')"
    DRILL_FLYWAY_COUNT="$(psql -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" -d "$DRILL_DB" -t -A -v ON_ERROR_STOP=1 \
        -c "SELECT count(*) FROM flyway_schema_history WHERE success = true" 2>/dev/null | tr -d '[:space:]')"

    FLYWAY_MATCHED="false"
    if [[ -z "$SOURCE_FLYWAY_VERSION" || -z "$DRILL_FLYWAY_VERSION" ]]; then
        fail_step "flyway_schema_history is missing or empty"
    elif [[ "$SOURCE_FLYWAY_VERSION" == "$DRILL_FLYWAY_VERSION" && "$SOURCE_FLYWAY_COUNT" == "$DRILL_FLYWAY_COUNT" ]]; then
        FLYWAY_MATCHED="true"
    else
        fail_step "Flyway mismatch (source=${SOURCE_FLYWAY_VERSION}/${SOURCE_FLYWAY_COUNT}, drill=${DRILL_FLYWAY_VERSION}/${DRILL_FLYWAY_COUNT})"
    fi
    CHECK_JSON+=("{\"name\":\"flyway_schema_history\",\"source\":\"$(json_escape "${SOURCE_FLYWAY_VERSION} (${SOURCE_FLYWAY_COUNT} migrations)")\",\"drill\":\"$(json_escape "${DRILL_FLYWAY_VERSION} (${DRILL_FLYWAY_COUNT} migrations)")\",\"matched\":${FLYWAY_MATCHED}}")
    log "INFO" "flyway: source=${SOURCE_FLYWAY_VERSION}/${SOURCE_FLYWAY_COUNT} drill=${DRILL_FLYWAY_VERSION}/${DRILL_FLYWAY_COUNT} matched=${FLYWAY_MATCHED}"
fi

# ----------------------------------------------------------------------
# Step 5: build result, report to ops API, cleanup
# ----------------------------------------------------------------------
FINISHED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
END_SECONDS="$(date +%s)"
DURATION_SECONDS=$((END_SECONDS - START_SECONDS))
STATUS_TEXT="PASS"
[[ "${#FAILURE_REASONS[@]}" -gt 0 ]] && STATUS_TEXT="FAIL"

CHECKS_BLOCK=""
if [[ "${#CHECK_JSON[@]}" -gt 0 ]]; then
    CHECKS_BLOCK="$(IFS=,; printf '%s' "${CHECK_JSON[*]}")"
fi

REASONS_BLOCK=""
if [[ "${#FAILURE_REASONS[@]}" -gt 0 ]]; then
    for reason in "${FAILURE_REASONS[@]}"; do
        [[ -n "$REASONS_BLOCK" ]] && REASONS_BLOCK+=","
        REASONS_BLOCK+="\"$(json_escape "$reason")\""
    done
fi

SUMMARY_MESSAGE="Restore drill ${STATUS_TEXT}"
if [[ "$STATUS_TEXT" == "FAIL" ]]; then
    SUMMARY_MESSAGE="${SUMMARY_MESSAGE}: ${FAILURE_REASONS[0]}"
fi
SUMMARY_MESSAGE="${SUMMARY_MESSAGE:0:500}"

REPORT_ID=""
REPORTED_VERIFIED="false"
if [[ -n "$API_BASE_URL" && -n "$API_TOKEN" && -n "$BACKUP_FILE" ]]; then
    log "INFO" "Reporting backup and drill result to ops API"
    META_FILE="${BACKUP_FILE}.meta.json"
    BACKUP_CODE="$(basename "$BACKUP_FILE" .dump)"
    META_STARTED="$STARTED_AT"
    META_FINISHED="$FINISHED_AT"
    META_SIZE=0
    META_HASH=""
    if [[ -f "$META_FILE" ]]; then
        BACKUP_CODE="$(sed -n 's/.*"backupCode"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$META_FILE" | head -n1)"
        META_STARTED="$(sed -n 's/.*"startedAt"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$META_FILE" | head -n1)"
        META_FINISHED="$(sed -n 's/.*"finishedAt"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$META_FILE" | head -n1)"
        META_SIZE="$(sed -n 's/.*"sizeBytes"[[:space:]]*:[[:space:]]*\([0-9]*\).*/\1/p' "$META_FILE" | head -n1)"
        META_HASH="$(sed -n 's/.*"fileHash"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$META_FILE" | head -n1)"
    fi
    [[ -z "$BACKUP_CODE" ]] && BACKUP_CODE="$(basename "$BACKUP_FILE" .dump)"
    [[ -z "$META_STARTED" ]] && META_STARTED="$STARTED_AT"
    [[ -z "$META_FINISHED" ]] && META_FINISHED="$FINISHED_AT"
    [[ -z "$META_SIZE" ]] && META_SIZE=0

    REPORT_BODY=$(printf '{"backupCode":"%s","backupType":"FULL","status":"SUCCESS","sizeBytes":%s,"fileHash":"%s","target":"%s","startedAt":"%s","finishedAt":"%s","message":"Restore drill source backup"}' \
        "$(json_escape "$BACKUP_CODE")" "$META_SIZE" "$(json_escape "$META_HASH")" \
        "$(json_escape "$BACKUP_FILE")" "$META_STARTED" "$META_FINISHED")
    REPORT_RESPONSE="$(curl -fsS --max-time 30 -X POST "${API_BASE_URL%/}/api/v1/ops/backups" \
        -H "Content-Type: application/json" -H "Authorization: Bearer ${API_TOKEN}" -d "$REPORT_BODY" 2>/dev/null)"
    if [[ $? -eq 0 && -n "$REPORT_RESPONSE" ]]; then
        REPORT_ID="$(printf '%s' "$REPORT_RESPONSE" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([0-9a-fA-F-]*\)".*/\1/p' | head -n1)"
    fi

    if [[ -n "$REPORT_ID" ]]; then
        log "INFO" "Backup reported, id=${REPORT_ID}"
        VERIFIED_VALUE="false"
        [[ "$STATUS_TEXT" == "PASS" ]] && VERIFIED_VALUE="true"
        VERIFY_BODY=$(printf '{"restoreVerified":%s,"message":"%s"}' \
            "$VERIFIED_VALUE" "$(json_escape "$SUMMARY_MESSAGE")")
        if curl -fsS --max-time 30 -X POST "${API_BASE_URL%/}/api/v1/ops/backups/${REPORT_ID}/verify" \
            -H "Content-Type: application/json" -H "Authorization: Bearer ${API_TOKEN}" -d "$VERIFY_BODY" >/dev/null 2>&1; then
            REPORTED_VERIFIED="true"
            log "INFO" "Restore verification reported to ops API"
        else
            log "WARN" "Restore verification report failed (ignored)"
        fi
    else
        log "WARN" "Backup report returned no data.id; verify step skipped"
    fi
fi

DRILL_KEPT="false"
[[ "$KEEP_DRILL" -eq 1 ]] && DRILL_KEPT="true"

cat >"$RESULT_FILE" <<EOF
{
  "status": "${STATUS_TEXT}",
  "sourceDatabase": "$(json_escape "$SOURCE_DB")",
  "drillDatabase": "$(json_escape "$DRILL_DB")",
  "backupFile": "$(json_escape "$BACKUP_FILE")",
  "startedAt": "${STARTED_AT}",
  "finishedAt": "${FINISHED_AT}",
  "durationSeconds": ${DURATION_SECONDS},
  "checks": [${CHECKS_BLOCK}],
  "failureReasons": [${REASONS_BLOCK}],
  "drillKept": ${DRILL_KEPT},
  "reportedBackupId": "$(json_escape "$REPORT_ID")",
  "reportedVerified": ${REPORTED_VERIFIED}
}
EOF

if [[ "$DRILL_CREATED" -eq 1 ]]; then
    if [[ "$KEEP_DRILL" -eq 1 ]]; then
        log "INFO" "Keeping drill database ${DRILL_DB} (requested)"
    else
        log "INFO" "Dropping drill database ${DRILL_DB}"
        dropdb --if-exists --force -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" "$DRILL_DB" \
            || log "WARN" "dropdb failed; remove the drill database manually"
    fi
fi

echo ""
log "INFO" "===== RESTORE DRILL SUMMARY ====="
echo "DRILL_STATUS=${STATUS_TEXT}"
echo "DRILL_SOURCE_DATABASE=${SOURCE_DB}"
echo "DRILL_DATABASE=${DRILL_DB}"
echo "DRILL_BACKUP_FILE=${BACKUP_FILE}"
echo "DRILL_RESULT_FILE=${RESULT_FILE}"

if [[ "$STATUS_TEXT" == "FAIL" ]]; then
    for reason in "${FAILURE_REASONS[@]}"; do
        log "ERROR" "FAIL REASON: ${reason}"
    done
    exit 1
fi

log "INFO" "Restore drill PASSED"
exit 0
