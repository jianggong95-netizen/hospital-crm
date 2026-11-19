#!/usr/bin/env bash
#
# Deploy the Hospital CRM API jar on a Linux host.
#
# Flow: preflight -> database backup -> stop old process -> keep previous jar
#       -> copy new jar -> start -> readiness poll -> automatic rollback.
#
# Usage:
#   ./deploy.sh [options]
#     --jar-path=FILE          new jar (default backend/crm-boot/target/crm-api.jar)
#     --app-dir=DIR            runtime directory (default ./run)
#     --port=N                 application port (default 8080)
#     --health-url=URL         readiness URL (default http://127.0.0.1:8080/actuator/health/readiness)
#     --db-backup              accepted for explicitness (backup runs by default)
#     --skip-db-backup         skip the pre-deployment database backup
#     --api-base-url=URL       CRM API base URL for backup reporting
#     --api-token=TOKEN        bearer token for backup reporting
#     --timeout-seconds=N      health check timeout (default 120)
#     -h, --help               show help
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

JAR_PATH="backend/crm-boot/target/crm-api.jar"
APP_DIR="./run"
PORT="8080"
HEALTH_URL="http://127.0.0.1:8080/actuator/health/readiness"
SKIP_DB_BACKUP=0
API_BASE_URL=""
API_TOKEN=""
TIMEOUT_SECONDS="120"

usage() {
    sed -n '2,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --jar-path=*)       JAR_PATH="${1#*=}" ;;
        --app-dir=*)        APP_DIR="${1#*=}" ;;
        --port=*)           PORT="${1#*=}" ;;
        --health-url=*)     HEALTH_URL="${1#*=}" ;;
        --db-backup)        ;;
        --skip-db-backup)   SKIP_DB_BACKUP=1 ;;
        --api-base-url=*)   API_BASE_URL="${1#*=}" ;;
        --api-token=*)      API_TOKEN="${1#*=}" ;;
        --timeout-seconds=*) TIMEOUT_SECONDS="${1#*=}" ;;
        -h|--help)          usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

mkdir -p "$APP_DIR"
APP_DIR="$(cd "$APP_DIR" && pwd)"
TS="$(date '+%Y%m%d_%H%M%S')"
LOG="$APP_DIR/deploy-${TS}.log"
PID_FILE="$APP_DIR/crm-api.pid"
CURRENT_JAR="$APP_DIR/crm-api.jar"
PREV_JAR="$APP_DIR/crm-api.jar.prev"

log() {
    local level="$1"
    shift
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $*" | tee -a "$LOG"
}

fail() {
    log "ERROR" "$1"
    exit 1
}

managed_pid() {
    if [[ -f "$PID_FILE" ]]; then
        tr -d '[:space:]' <"$PID_FILE"
    fi
}

port_owner() {
    local owner=""
    if command -v ss >/dev/null 2>&1; then
        owner="$(ss -ltnp "sport = :${PORT}" 2>/dev/null | sed -n 's/.*pid=\([0-9]\+\).*/\1/p' | head -n1)"
    fi
    if [[ -z "$owner" ]] && command -v lsof >/dev/null 2>&1; then
        owner="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN 2>/dev/null | head -n1)"
    fi
    printf '%s' "$owner"
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

rollback() {
    log "WARN" "Deployment failed; starting automatic rollback"
    stop_crm
    if [[ ! -f "$PREV_JAR" ]]; then
        log "ERROR" "No previous jar found; automatic rollback is not possible"
        return 1
    fi
    cp -f "$PREV_JAR" "$CURRENT_JAR" || { log "ERROR" "Failed to restore previous jar"; return 1; }
    start_crm
    if wait_health; then
        log "INFO" "Rollback succeeded; previous jar is running again"
        return 0
    fi
    log "ERROR" "Rollback health check failed"
    return 1
}

log "INFO" "===== CRM API DEPLOYMENT START ====="
log "INFO" "AppDir=${APP_DIR}"
log "INFO" "JarPath=${JAR_PATH}"

# ----------------------------------------------------------------------
# Preflight
# ----------------------------------------------------------------------
command -v java >/dev/null 2>&1 || fail "java was not found in PATH"
JAVA_LINE="$(java -version 2>&1 | head -n1)"
log "INFO" "Java: ${JAVA_LINE}"

[[ -f "$JAR_PATH" ]] || fail "Jar file not found: ${JAR_PATH}"
JAR_PATH="$(cd "$(dirname "$JAR_PATH")" && pwd)/$(basename "$JAR_PATH")"
log "INFO" "New jar: ${JAR_PATH} ($(wc -c <"$JAR_PATH" | tr -d ' ') bytes)"

log "INFO" "Preflight: checking port ${PORT}"
OLD_PID="$(managed_pid)"
OWNER="$(port_owner)"
if [[ -n "$OWNER" ]]; then
    if [[ -n "$OLD_PID" && "$OWNER" == "$OLD_PID" ]]; then
        log "INFO" "Port ${PORT} is held by the managed CRM process (pid=${OWNER})"
    else
        fail "Port ${PORT} is held by an unmanaged process (pid=${OWNER}); stop it manually before deploying"
    fi
elif command -v ss >/dev/null 2>&1 && ss -ltn "sport = :${PORT}" 2>/dev/null | grep -q LISTEN; then
    fail "Port ${PORT} is in use but the owning pid could not be determined (run as root to inspect)"
else
    log "INFO" "Port ${PORT} is free"
fi

FREE_KB="$(df -Pk "$APP_DIR" 2>/dev/null | awk 'NR==2 {print $4}')"
if [[ -z "$FREE_KB" ]]; then
    log "WARN" "Unable to determine free disk space; continue without this check"
elif [[ "$FREE_KB" -lt 2097152 ]]; then
    fail "Free disk space is below 2 GB (${FREE_KB} KB)"
else
    log "INFO" "Free disk space: $((FREE_KB / 1024)) MB"
fi

if [[ "${SPRING_PROFILES_ACTIVE:-}" == "prod" ]]; then
    log "INFO" "Preflight: prod profile secrets"
    for key_name in CRM_JWT_SIGNING_KEY CRM_DATA_ENCRYPTION_KEY; do
        value="${!key_name:-}"
        if [[ -z "$value" ]]; then
            log "WARN" "${key_name} is empty while SPRING_PROFILES_ACTIVE=prod"
        elif [[ "$value" == dev-only* ]]; then
            log "WARN" "${key_name} still uses a development default value"
        fi
    done
fi

# ----------------------------------------------------------------------
# Database backup
# ----------------------------------------------------------------------
if [[ "$SKIP_DB_BACKUP" -eq 0 ]]; then
    BACKUP_SCRIPT="${SCRIPT_DIR}/../pg/backup.sh"
    [[ -f "$BACKUP_SCRIPT" ]] || fail "Backup script not found: ${BACKUP_SCRIPT}"
    log "INFO" "Pre-deployment database backup"
    BACKUP_ARGS=(--backup-dir=backups)
    [[ -n "$API_BASE_URL" ]] && BACKUP_ARGS+=(--api-base-url="$API_BASE_URL")
    [[ -n "$API_TOKEN" ]] && BACKUP_ARGS+=(--api-token="$API_TOKEN")
    if ! bash "$BACKUP_SCRIPT" "${BACKUP_ARGS[@]}"; then
        fail "Database backup failed; deployment aborted before any change"
    fi
    log "INFO" "Database backup finished"
else
    log "WARN" "Database backup skipped (--skip-db-backup)"
fi

# ----------------------------------------------------------------------
# Stop old process and switch jar
# ----------------------------------------------------------------------
stop_crm

if [[ "$JAR_PATH" == "$CURRENT_JAR" ]]; then
    log "WARN" "JarPath points at the deployed jar; skipping copy and previous-jar backup"
else
    if [[ -f "$CURRENT_JAR" ]]; then
        cp -f "$CURRENT_JAR" "$PREV_JAR" || fail "Failed to keep previous jar"
        log "INFO" "Previous jar kept at ${PREV_JAR}"
    else
        log "WARN" "No current jar found; first deployment has no previous jar"
    fi
    cp -f "$JAR_PATH" "$CURRENT_JAR" || fail "Failed to copy new jar"
    log "INFO" "New jar installed at ${CURRENT_JAR}"
fi

# ----------------------------------------------------------------------
# Start and health check
# ----------------------------------------------------------------------
start_crm
wait_health
HEALTH_STATE=$?

if [[ "$HEALTH_STATE" -ne 0 ]]; then
    if [[ "$HEALTH_STATE" -eq 2 ]]; then
        log "ERROR" "Application process exited before becoming ready"
    else
        log "ERROR" "Readiness check timed out"
    fi
    if [[ -f "$APP_DIR/crm-api.log" ]]; then
        log "ERROR" "Last application log lines:"
        tail -n 20 "$APP_DIR/crm-api.log" | while IFS= read -r line; do log "ERROR" "$line"; done
    fi
    if rollback; then
        log "ERROR" "===== DEPLOYMENT FAILED (rolled back) ====="
    else
        log "ERROR" "===== DEPLOYMENT FAILED (manual intervention required) ====="
    fi
    exit 1
fi

log "INFO" "Readiness check passed"
log "INFO" "===== DEPLOYMENT SUCCEEDED ====="
log "INFO" "Deployed at $(date '+%Y-%m-%d %H:%M:%S')"
log "INFO" "Rollback command: bash ${SCRIPT_DIR}/rollback.sh --app-dir=${APP_DIR} --port=${PORT} --health-url=${HEALTH_URL}"
log "INFO" "Deployment log: ${LOG}"

echo "DEPLOY_STATUS=SUCCESS"
echo "DEPLOY_APP_DIR=${APP_DIR}"
echo "DEPLOY_LOG=${LOG}"

exit 0
