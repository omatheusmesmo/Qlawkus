#!/usr/bin/env bash
#
# Qlawkus redeploy contract - the generic hook.
#
# The running agent cannot rebuild or restart itself: a native binary is frozen,
# a JVM app is closed-world augmented at build time, and the runtime image ships
# no toolchain. So composing a new capability set in or out is a rebuild-and-redeploy
# driven from OUTSIDE the app. This script is the environment-independent part of
# that flow; each reference implementation (compose/, k8s/, systemd/) supplies the
# two environment-specific steps by defining `build` and `restart` before sourcing it.
#
# The 5-phase contract:
#   1. fetch    - read the staged manifest and config overrides over the authenticated admin API
#   2. promote  - write them into the source tree the builder reads (agent.yml, config-overrides.properties)
#   3. build    - regenerate the pom from the manifest and build (caller-provided)
#   4. restart  - swap the running instance onto the new artifact (caller-provided)
#   5. verify   - wait for health, confirm the new active state, discard what was staged
#
# The app is passive: it only fetches (GET), and discards (DELETE). Everything that
# executes a build or a restart lives here or in the caller, never in the app - so a
# leaked admin credential can at most stage a validated manifest or override set, never
# run a command.
#
# Usage (standalone, rebuilds the currently committed manifest, no promotion):
#   QLAWKUS_ADMIN_USER=... QLAWKUS_ADMIN_PASSWORD=... ./redeploy.sh
# Usage (from a reference implementation):
#   define build() and restart(), then `source redeploy.sh` and call `redeploy_run`.
#
set -euo pipefail

BASE_URL="${QLAWKUS_BASE_URL:-http://localhost:8742}"
API="${BASE_URL}/api/admin/composition"
CONFIG_API="${BASE_URL}/api/admin/config-overrides"
AUTH_USER="${QLAWKUS_ADMIN_USER:?set QLAWKUS_ADMIN_USER}"
AUTH_PASS="${QLAWKUS_ADMIN_PASSWORD:?set QLAWKUS_ADMIN_PASSWORD}"
SOURCE_MANIFEST="${QLAWKUS_SOURCE_MANIFEST:-app/src/main/resources/qlawkus/agent.yml}"
SOURCE_CONFIG_OVERRIDES="${QLAWKUS_SOURCE_CONFIG_OVERRIDES:-app/src/main/resources/qlawkus/config-overrides.properties}"
HEALTH_URL="${QLAWKUS_HEALTH_URL:-${BASE_URL}/q/health/ready}"
HEALTH_TIMEOUT="${QLAWKUS_HEALTH_TIMEOUT:-180}"
DRY_RUN="${QLAWKUS_DRY_RUN:-0}"

log() { printf '[redeploy] %s\n' "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }

curl_api() {
  curl -fsS --user "${AUTH_USER}:${AUTH_PASS}" "$@"
}

# Phase 1: fetch the staged manifest. Prints it on stdout, or nothing if none is staged.
fetch_staged() {
  local body
  body="$(curl_api "${API}")" || die "cannot reach the composition API at ${API}"
  jq -er '.staged // empty' <<<"${body}"
}

# Phase 1b: fetch the staged config overrides. Prints them on stdout, or nothing if none staged.
fetch_staged_config() {
  local body
  body="$(curl_api "${CONFIG_API}")" || die "cannot reach the config-overrides API at ${CONFIG_API}"
  jq -er '.staged // empty' <<<"${body}"
}

# Phase 2: promote the staged manifest into the source tree the builder reads.
promote() {
  local staged="$1"
  [ -f "${SOURCE_MANIFEST}" ] || die "source manifest not found: ${SOURCE_MANIFEST} (run from the repo root)"
  printf '%s\n' "${staged}" >"${SOURCE_MANIFEST}"
  log "promoted staged manifest into ${SOURCE_MANIFEST}"
}

# Phase 2b: promote the staged config overrides into the source tree the builder reads. Overwrites
# the file wholesale, same as the manifest - it is owned by this step, never hand-edited.
promote_config() {
  local staged="$1"
  [ -f "${SOURCE_CONFIG_OVERRIDES}" ] || die "source config overrides not found: ${SOURCE_CONFIG_OVERRIDES} (run from the repo root)"
  printf '%s\n' "${staged}" >"${SOURCE_CONFIG_OVERRIDES}"
  log "promoted staged config overrides into ${SOURCE_CONFIG_OVERRIDES}"
}

# Default build/restart hooks. A reference implementation defines its own BEFORE sourcing
# this file; the `declare -F` guards mean sourcing never clobbers a caller's definition
# (otherwise these no-ops would silently win and the redeploy would skip build and restart).
# So these apply only to a standalone run, which supplies neither.
declare -F build >/dev/null 2>&1 || build() { log "no build step defined; skipping (define build() in a reference implementation)"; }
declare -F restart >/dev/null 2>&1 || restart() { log "no restart step defined; skipping (define restart() in a reference implementation)"; }

# Phase 5a: wait for the new instance to report ready.
wait_for_health() {
  log "waiting for ${HEALTH_URL} (timeout ${HEALTH_TIMEOUT}s)"
  local waited=0
  until curl -fsS -o /dev/null "${HEALTH_URL}"; do
    waited=$((waited + 3))
    [ "${waited}" -lt "${HEALTH_TIMEOUT}" ] || die "instance did not become healthy within ${HEALTH_TIMEOUT}s"
    sleep 3
  done
  log "instance is healthy"
}

# Phase 5b: discard the staged manifest once the new instance is live.
discard_staged() {
  curl_api -X DELETE "${API}/manifest" -o /dev/null -w '%{http_code}' \
    | grep -qE '^(204|404)$' && log "discarded the consumed staged manifest" \
    || log "WARN: could not discard the staged manifest (leave it for a retry)"
}

# Phase 5c: discard the staged config overrides once the new instance is live.
discard_staged_config() {
  curl_api -X DELETE "${CONFIG_API}/overrides" -o /dev/null -w '%{http_code}' \
    | grep -qE '^(204|404)$' && log "discarded the consumed staged config overrides" \
    || log "WARN: could not discard the staged config overrides (leave it for a retry)"
}

redeploy_run() {
  local staged staged_config
  staged="$(fetch_staged)"
  staged_config="$(fetch_staged_config)"

  if [ -z "${staged}" ]; then
    log "no staged manifest; rebuilding the currently committed one"
  else
    promote "${staged}"
  fi

  if [ -z "${staged_config}" ]; then
    log "no staged config overrides"
  else
    promote_config "${staged_config}"
  fi

  if [ "${DRY_RUN}" = "1" ]; then
    log "dry run: stopping after promotion (no build, restart, or discard)"
    return 0
  fi

  log "phase 3: build"
  build
  log "phase 4: restart"
  restart
  wait_for_health
  [ -n "${staged}" ] && discard_staged
  [ -n "${staged_config}" ] && discard_staged_config
  log "redeploy complete"
}

# Run directly only when executed, not when sourced by a reference implementation.
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  redeploy_run
fi
