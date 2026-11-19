#!/usr/bin/env bash
#
# Restore a PostgreSQL custom-format backup for Hospital CRM.
#
# Connection values are resolved from PGHOST/PGPORT/PGUSER/PGPASSWORD or
# CRM_DB_URL/CRM_DB_NAME/CRM_DB_USERNAME/CRM_DB_PASSWORD.
#
# Usage:
#   ./restore.sh --backup-file=FILE --target-database=NAME [options]
#     --backup-file=FILE       path to a .dump file
#     --target-database=NAME   target database (required)
#     --create-database        create the target database when missing
#     --force                  required when target equals CRM_DB_NAME
#     -h, --help               show help
#
set -uo pipefail

BACKUP_FILE=""
TARGET_DATABASE=""
CREATE_DATABASE=0
FORCE=0

usage() {
    sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --backup-file=*)     BACKUP_FILE="${1#*=}" ;;
        --target-database=*) TARGET_DATABASE="${1#*=}" ;;
        --create-database)   CREATE_DATABASE=1 ;;
        --force)             FORCE=1 ;;
        -h|--help)           usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

log() {
    local level="$1"
    shift
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $*"
}

fail() {
    log "ERROR" "$1"
    exit 1
}

# ----------------------------------------------------------------------
# Resolve connection
# ----------------------------------------------------------------------
URL="${CRM_DB_URL:-}"
HOST_NAME="${PGHOST:-}"
PORT="${PGPORT:-}"
USER_NAME="${PGUSER:-${CRM_DB_USERNAME:-crm}}"
PASSWORD="${PGPASSWORD:-${CRM_DB_PASSWORD:-}}"
CRM_DATABASE="${CRM_DB_NAME:-}"
URL_DB_NAME=""

if [[ -n "$URL" && "$URL" =~ ^jdbc:postgresql://([^/:?]+)(:([0-9]+))?/([^?;]+) ]]; then
    [[ -z "$HOST_NAME" ]] && HOST_NAME="${BASH_REMATCH[1]}"
    [[ -z "$PORT" && -n "${BASH_REMATCH[3]}" ]] && PORT="${BASH_REMATCH[3]}"
    URL_DB_NAME="${BASH_REMATCH[4]}"
fi

[[ -z "$HOST_NAME" ]] && HOST_NAME="127.0.0.1"
[[ -z "$PORT" ]] && PORT="5432"
[[ -z "$USER_NAME" ]] && USER_NAME="crm"
[[ -z "$CRM_DATABASE" ]] && CRM_DATABASE="$URL_DB_NAME"
[[ -z "$CRM_DATABASE" ]] && CRM_DATABASE="crm"

export PGPASSWORD="$PASSWORD"

# ----------------------------------------------------------------------
# Preflight
# ----------------------------------------------------------------------
[[ -z "$TARGET_DATABASE" ]] && fail "--target-database is required"
if [[ ! "$TARGET_DATABASE" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    fail "Target database must match ^[A-Za-z_][A-Za-z0-9_]*$"
fi

case "$(printf '%s' "$TARGET_DATABASE" | tr '[:upper:]' '[:lower:]')" in
    postgres|template0|template1) fail "Refusing to restore into a PostgreSQL system database: $TARGET_DATABASE" ;;
esac

if [[ "$TARGET_DATABASE" == "$CRM_DATABASE" && "$FORCE" -ne 1 ]]; then
    log "ERROR" "Target database '$TARGET_DATABASE' is the configured CRM database."
    log "ERROR" "Refusing to overwrite the live database without --force. This is an anti-mistake guard."
    exit 1
fi

[[ -z "$BACKUP_FILE" ]] && fail "--backup-file is required"
[[ ! -f "$BACKUP_FILE" ]] && fail "Backup file not found: $BACKUP_FILE"
BACKUP_FILE="$(cd "$(dirname "$BACKUP_FILE")" && pwd)/$(basename "$BACKUP_FILE")"

for tool in psql pg_restore createdb; do
    command -v "$tool" >/dev/null 2>&1 || fail "$tool was not found in PATH"
done

# ----------------------------------------------------------------------
# SHA-256 verification
# ----------------------------------------------------------------------
SHA_FILE="${BACKUP_FILE}.sha256"
if [[ -f "$SHA_FILE" ]]; then
    EXPECTED="$(awk 'NR==1 {print tolower($1)}' "$SHA_FILE")"
    if command -v sha256sum >/dev/null 2>&1; then
        ACTUAL="$(sha256sum "$BACKUP_FILE" | awk '{print tolower($1)}')"
    elif command -v shasum >/dev/null 2>&1; then
        ACTUAL="$(shasum -a 256 "$BACKUP_FILE" | awk '{print tolower($1)}')"
    else
        fail "Neither sha256sum nor shasum is available"
    fi
    if [[ "$EXPECTED" != "$ACTUAL" ]]; then
        fail "SHA-256 mismatch: expected ${EXPECTED}, actual ${ACTUAL}"
    fi
    log "INFO" "SHA-256 verified: ${ACTUAL}"
else
    log "WARN" "SHA-256 sidecar not found; skipping integrity check"
fi

# ----------------------------------------------------------------------
# Target database existence / creation
# ----------------------------------------------------------------------
EXISTS="$(psql -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" -d postgres -t -A -v ON_ERROR_STOP=1 \
    -c "SELECT 1 FROM pg_database WHERE datname = '${TARGET_DATABASE}'" 2>&1)"
PSQL_EXIT=$?
if [[ "$PSQL_EXIT" -ne 0 ]]; then
    fail "Unable to query pg_database: ${EXISTS}"
fi

if [[ "$(printf '%s' "$EXISTS" | tr -d '[:space:]')" != "1" ]]; then
    if [[ "$CREATE_DATABASE" -ne 1 ]]; then
        fail "Target database '$TARGET_DATABASE' does not exist. Re-run with --create-database."
    fi
    log "INFO" "Creating database ${TARGET_DATABASE}"
    createdb -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" "$TARGET_DATABASE" || fail "createdb failed for $TARGET_DATABASE"
else
    log "INFO" "Target database '$TARGET_DATABASE' already exists"
fi

# ----------------------------------------------------------------------
# pg_restore
# ----------------------------------------------------------------------
log "INFO" "Restoring ${BACKUP_FILE} into ${TARGET_DATABASE}"
pg_restore --clean --if-exists -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" \
    -d "$TARGET_DATABASE" -Fc "$BACKUP_FILE"
RESTORE_EXIT=$?
if [[ "$RESTORE_EXIT" -ne 0 ]]; then
    log "WARN" "pg_restore returned exit code ${RESTORE_EXIT}; verification will decide the final result"
fi

# ----------------------------------------------------------------------
# Verification
# ----------------------------------------------------------------------
FAILED_CHECKS=()
TABLES=("crm_customer" "crm_order" "crm_appointment" "crm_metric_definition" "sys_audit_log")

for table in "${TABLES[@]}"; do
    VALUE="$(psql -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" -d "$TARGET_DATABASE" -t -A -v ON_ERROR_STOP=1 \
        -c "SELECT to_regclass('public.${table}') IS NOT NULL" 2>&1)"
    if [[ "$(printf '%s' "$VALUE" | tr -d '[:space:]')" == "t" ]]; then
        log "INFO" "Table check OK: ${table}"
    else
        log "ERROR" "Table check FAILED: ${table}"
        FAILED_CHECKS+=("$table")
    fi
done

FLYWAY_VERSION="$(psql -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" -d "$TARGET_DATABASE" -t -A -v ON_ERROR_STOP=1 \
    -c "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1" 2>/dev/null)"
FLYWAY_VERSION_EXIT=$?
FLYWAY_COUNT="$(psql -h "$HOST_NAME" -p "$PORT" -U "$USER_NAME" -d "$TARGET_DATABASE" -t -A -v ON_ERROR_STOP=1 \
    -c "SELECT count(*) FROM flyway_schema_history WHERE success = true" 2>/dev/null)"
FLYWAY_COUNT_EXIT=$?

FLYWAY_VERSION="$(printf '%s' "$FLYWAY_VERSION" | tr -d '[:space:]')"
FLYWAY_COUNT="$(printf '%s' "$FLYWAY_COUNT" | tr -d '[:space:]')"

if [[ "$FLYWAY_VERSION_EXIT" -ne 0 || "$FLYWAY_COUNT_EXIT" -ne 0 || -z "$FLYWAY_VERSION" ]]; then
    log "ERROR" "flyway_schema_history check FAILED (no applied migration found)"
    FAILED_CHECKS+=("flyway_schema_history")
else
    log "INFO" "Flyway latest version: ${FLYWAY_VERSION} (applied migrations: ${FLYWAY_COUNT})"
fi

echo ""
log "INFO" "===== RESTORE SUMMARY ====="
echo "RESTORE_TARGET_DATABASE=${TARGET_DATABASE}"
echo "RESTORE_BACKUP_FILE=${BACKUP_FILE}"
echo "RESTORE_FLYWAY_VERSION=${FLYWAY_VERSION}"
echo "RESTORE_FLYWAY_COUNT=${FLYWAY_COUNT}"

if [[ "${#FAILED_CHECKS[@]}" -gt 0 ]]; then
    log "ERROR" "Restore verification FAILED: $(printf '%s\n' "${FAILED_CHECKS[@]}" | sort -u | tr '\n' ' ')"
    exit 1
fi

log "INFO" "Restore verification PASSED"
exit 0
