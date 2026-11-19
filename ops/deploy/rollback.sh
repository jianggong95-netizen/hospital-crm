#!/usr/bin/env bash
#
# Roll back the Hospital CRM API to the previous jar.
#
# Stops the managed process, optionally restores a database backup, swaps
# crm-api.jar.prev into place (the failed jar is kept as
# crm-api.jar.failed-<timestamp>), starts the application and waits for
# readiness.
#
# Usage:
#   ./rollback.sh [options]
#     --app-dir=DIR                 runtime directory (default ./run)
#     --port=N                      application port (default 8080)
#     --health-url=URL              readiness URL
#     --restore-backup-file=FILE    optional .dump restored into CRM_DB_NAME with --force
#     --timeout-seconds=N           health check timeout (default 120)
#     -h, --help                    show help
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

APP_DIR="./run"
PORT="8080"
HEALTH_URL="http://127.0.0.1:8080/actuator/health/readiness"
RESTORE_BACKUP_FILE=""
TIMEOUT_SECONDS="120"

usage() {
    sed -n '2,17p'  "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --app-dir=*)              APP_DIR="${1#*=}" ;;
        --port=*)                 PORT="${1#*=}" ;;
        --health-url=*)           HEALTH_URL="${1#*=}" ;;
        --restore-backup-file=*)  RESTORE_BACKUP_FILE="${1#*=}" ;;
        --timeout-seconds=*)      TIMEOUT_SECONDS="${1#*=}" ;;
        -h|--help)                usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

[[ -d "$APP_DIR" ]] || { echo "[ERROR] AppDir does not exist: $APP_DIR" >&2; exit 1; }
APP_DIR="$(cd "$APP_DIR" && pwd)"
TS="$(date '+%Y%m%d_%H%M%S')"
LOG="$APP_DIR/rollback-${TS}.log"
PID_FILE="$APP_DIR/crm-api.pid"
CURRENT_JAR="$APP_DIR/crm-api.jar"
PREV_JAR="$APP_DIR/crm-api.jar.prev"

log() {
    local level="$1"
    shift
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $*" | tee -a "$LOG"
}

managed_pid() {
    if [[ -f "$PID_FILE" ]]; then
        tr -d '[:space:]' <"$PID_FILE"
    fi
}

stop_crm() {
    local old_pid
    old_pid="$(managed_pid)"
    if [[ -n "$old_pid" && "$old_pid" =~ ^[0-9]+$ ]] && kill -0 "$old_pid" 2>/dev/null; then
        log "INFO" "Stopping CRM API process, pid=${old_pid}"
        kill -TERM "$old_pid" 2>/dev/null
        local i
        for i in $(seq 1 60); do
            kill -0 "$old_pid" 2>/dev/null || break
            sleep 0.5
        done
        if kill -0 "$old_pid" 2>/dev/null; then
            log "WARN" "Graceful stop timed out; sending SIGKILL"
            kill -KILL "$old_pid" 2>/dev/null
            sleep 1
        fi
    fi
    rm -f "$PID_FILE"
}

start_crm() {
    command -v java >/dev/null 2>&1 || { log "ERROR" "java was not found in PATH"; return 1; }
    nohup java -XX:MaxRAMPercentage=75 -XX:+UseG1GC -jar "$CURRENT_JAR" \
        >>"$APP_DIR/crm-api.log" 2>&1 &
    local new_pid=$!
    printf '%s\n' "$new_pid" >"$PID_FILE"
    log "INFO" "Application started, pid=${new_pid}"
}

health_ok() {
    local body=""
    if command -v curl >/dev/null 2>&1; then
        body="$(curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null)" || return 1
    elif command -v wget >/dev/null 2>&1; then
        body="$(wget -qO- --timeout=5 "$HEALTH_URL" 2>/dev/null)" || return 1
    else
        log "ERROR" "Neither curl nor wget is available for the health check"
        return 1
    fi
    printf '%s' "$body" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'
}

wait_health() {
    log "INFO" "Waiting for readiness: ${HEALTH_URL} (timeout ${TIMEOUT_SECONDS}s)"
    local deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
    local pid
    pid="$(managed_pid)"
    while [[ "$(date +%s)" -lt "$deadline" ]]; do
        if [[ -n "$pid" ]] && ! kill -0 "$pid" 2>/dev/null; then
            return 2
        fi
        if health_ok; then
            return 0
        fi
        sleep 2
    done
    return 1
}

log "INFO" "===== CRM API ROLLBACK START ====="
log "INFO" "AppDir=${APP_DIR}"

if [[ ! -f "$PREV_JAR" ]]; then
    log "ERROR" "Previous jar not found: ${PREV_JAR}"
    log "ERROR" "Rollback is not possible. Restore crm-api.jar manually from the release package."
    exit 1
fi

log "INFO" "Rolling back on port ${PORT}"
stop_crm

# ----------------------------------------------------------------------
# Optional database restore (before starting the old application)
# ----------------------------------------------------------------------
if [[ -n "$RESTORE_BACKUP_FILE" ]]; then
    RESTORE_SCRIPT="${SCRIPT_DIR}/../pg/restore.sh"
    [[ -f "$RESTORE_SCRIPT" ]] || { log "ERROR" "Restore script not found: ${RESTORE_SCRIPT}"; exit 1; }
    [[ -f "$RESTORE_BACKUP_FILE" ]] || { log "ERROR" "Backup file not found: ${RESTORE_BACKUP_FILE}"; exit 1; }

    TARGET_DATABASE="${CRM_DB_NAME:-}"
    if [[ -z "$TARGET_DATABASE" && -n "${CRM_DB_URL:-}" && "$CRM_DB_URL" =~ ^jdbc:postgresql://([^/:?]+)(:([0-9]+))?/([^?;]+) ]]; then
        TARGET_DATABASE="${BASH_REMATCH[4]}"
    fi
    [[ -z "$TARGET_DATABASE" ]] && TARGET_DATABASE="crm"

    log "WARN" "Database restore requested before rollback: target=${TARGET_DATABASE}"
    if ! bash "$RESTORE_SCRIPT" --backup-file="$RESTORE_BACKUP_FILE" --target-database="$TARGET_DATABASE" --create-database --force; then
        log "ERROR" "Database restore failed; application remains stopped. Manual intervention required."
        exit 1
    fi
    log "INFO" "Database restore completed"
fi

# ----------------------------------------------------------------------
# Swap jars
# ----------------------------------------------------------------------
FAILED_JAR="$APP_DIR/crm-api.jar.failed-${TS}"
if [[ -f "$CURRENT_JAR" ]]; then
    mv -f "$CURRENT_JAR" "$FAILED_JAR" || { log "ERROR" "Failed to move current jar"; exit 1; }
    log "INFO" "Failed jar kept at ${FAILED_JAR}"
fi
mv -f "$PREV_JAR" "$CURRENT_JAR" || { log "ERROR" "Failed to restore previous jar"; exit 1; }
log "INFO" "Previous jar restored to ${CURRENT_JAR}"

# ----------------------------------------------------------------------
# Start and verify
# ----------------------------------------------------------------------
start_crm || exit 1
wait_health
HEALTH_STATE=$?
if [[ "$HEALTH_STATE" -ne 0 ]]; then
    if [[ "$HEALTH_STATE" -eq 2 ]]; then
        log "ERROR" "Rollback failed: application exited before becoming ready"
    else
        log "ERROR" "Rollback failed: readiness check timed out"
    fi
    log "ERROR" "Inspect crm-api.log in ${APP_DIR}"
    exit 1
fi

log "INFO" "Readiness check passed"
log "INFO" "===== ROLLBACK SUCCEEDED ====="
log "INFO" "Rolled back at $(date '+%Y-%m-%d %H:%M:%S')"
log "INFO" "Failed jar kept at ${FAILED_JAR}"
log "INFO" "Rollback log: ${LOG}"

echo "ROLLBACK_STATUS=SUCCESS"
echo "ROLLBACK_APP_DIR=${APP_DIR}"
echo "ROLLBACK_LOG=${LOG}"

exit 0
