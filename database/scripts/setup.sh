#!/bin/bash
set -Eeuo pipefail

: "${MONGO_HOST:?MONGO_HOST is required}"
: "${MONGO_ADMIN_USER:?MONGO_ADMIN_USER is required}"
: "${MONGO_ADMIN_PASSWORD:?MONGO_ADMIN_PASSWORD is required}"

MONGO_PORT="${MONGO_PORT:-27017}"
MONGO_SETUP_ATTEMPTS="${MONGO_SETUP_ATTEMPTS:-30}"
MONGO_SETUP_SLEEP_SECONDS="${MONGO_SETUP_SLEEP_SECONDS:-2}"
MONGO_SETUP_SERVER_SELECTION_TIMEOUT_MS="${MONGO_SETUP_SERVER_SELECTION_TIMEOUT_MS:-3000}"
MONGO_URI="mongodb://${MONGO_HOST}:${MONGO_PORT}/admin?directConnection=true&serverSelectionTimeoutMS=${MONGO_SETUP_SERVER_SELECTION_TIMEOUT_MS}"
LAST_MONGO_OUTPUT="/tmp/mongo-setup-last-output.log"

log() {
  printf '[mongo-setup] %s\n' "$*"
}

print_diagnostics() {
  log "Diagnostics for ${MONGO_HOST}:${MONGO_PORT}"

  if command -v getent >/dev/null 2>&1; then
    log "DNS lookup:"
    getent hosts "$MONGO_HOST" || true
  fi

  log "TCP probe:"
  if timeout 5 bash -c "cat < /dev/null > /dev/tcp/${MONGO_HOST}/${MONGO_PORT}" 2>/dev/null; then
    log "TCP connection to ${MONGO_HOST}:${MONGO_PORT} succeeded."
  else
    log "TCP connection to ${MONGO_HOST}:${MONGO_PORT} failed."
  fi

  if [ -s "$LAST_MONGO_OUTPUT" ]; then
    log "Last mongosh output:"
    cat "$LAST_MONGO_OUTPUT"
  fi
}

wait_for_mongo() {
  log "Waiting for MongoDB at ${MONGO_HOST}:${MONGO_PORT}."

  for attempt in $(seq 1 "$MONGO_SETUP_ATTEMPTS"); do
    if mongosh "$MONGO_URI" \
      --quiet \
      --username "$MONGO_ADMIN_USER" \
      --password "$MONGO_ADMIN_PASSWORD" \
      --eval 'db.adminCommand({ ping: 1 })' >"$LAST_MONGO_OUTPUT" 2>&1; then
      log "MongoDB is reachable."
      return 0
    fi

    log "MongoDB is not reachable yet (attempt ${attempt}/${MONGO_SETUP_ATTEMPTS})."
    if [ "$attempt" -eq 1 ] || [ $((attempt % 5)) -eq 0 ]; then
      cat "$LAST_MONGO_OUTPUT"
    fi

    sleep "$MONGO_SETUP_SLEEP_SECONDS"
  done

  log "Timed out waiting for MongoDB."
  return 1
}

main() {
  trap 'status=$?; if [ "$status" -ne 0 ]; then print_diagnostics; fi' EXIT

  wait_for_mongo

  log "Running MongoDB initialization script."
  mongosh "$MONGO_URI" \
    --quiet \
    --username "$MONGO_ADMIN_USER" \
    --password "$MONGO_ADMIN_PASSWORD" \
    /scripts/init.js
  log "MongoDB setup completed."
}

main "$@"
