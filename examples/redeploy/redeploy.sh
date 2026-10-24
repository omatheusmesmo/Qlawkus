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
# Each phase is timed, and when the run ends (either way) it is reported back with
# POST /api/admin/composition/redeploys, so the agent keeps a history and meters of its
# own redeploys. The report is data only.
#
# The app is passive: it only fetches (GET), discards (DELETE) and records the report
# (POST). Everything that executes a build or a restart lives here or in the caller,
# never in the app - so a leaked admin credential can at most stage a validated manifest
# or override set, or write a bounded history entry, never run a command.
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
REPORT_TIMEOUT="${QLAWKUS_REPORT_TIMEOUT:-10}"

log() { printf '[redeploy] %s\n' "$*" >&2; }
die() { LAST_ERROR="$*"; log "ERROR: $*"; exit 1; }

curl_api() {
  curl -fsS --user "${AUTH_USER}:${AUTH_PASS}" "$@"
}

# Phase 1: fetch the staged manifest. Prints it on stdout, or nothing if none is staged.
# Without -e, since jq exits 4 on empty output and nothing staged is the ordinary case.
fetch_staged() {
  local body
  body="$(curl_api "${API}")" || die "cannot reach the composition API at ${API}"
  jq -r '.staged // empty' <<<"${body}"
}

# Phase 1b: fetch the staged config overrides. Prints them on stdout, or nothing if none staged.
fetch_staged_config() {
  local body
  body="$(curl_api "${CONFIG_API}")" || die "cannot reach the config-overrides API at ${CONFIG_API}"
  jq -r '.staged // empty' <<<"${body}"
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

# Run reporting. Each phase is timed, and the run is reported once, when it ends, to
# POST ${API}/redeploys: on success to the new instance after verify, on failure (from the EXIT
# trap, which knows the failing phase) to whatever instance answers. The report is data only.
# Reporting never changes how the run ends: an unreachable instance only gets the report written
# to this log, and the exit status is always the run's own. redeploy_run owns the EXIT trap while
# it runs (a dry run is not a redeploy and reports nothing).
RUN_STARTED_AT=""
CURRENT_PHASE=""
PHASE_STARTED_MS=0
PHASE_TIMINGS=""
LAST_ERROR=""

# Milliseconds since the epoch: EPOCHREALTIME (bash 5) where available, whole seconds otherwise.
now_ms() {
  if [ -n "${EPOCHREALTIME:-}" ]; then
    local micros="${EPOCHREALTIME/[.,]/}"
    printf '%s\n' "$((micros / 1000))"
  else
    printf '%s\n' "$(($(date +%s) * 1000))"
  fi
}

phase_begin() {
  CURRENT_PHASE="$1"
  PHASE_STARTED_MS="$(now_ms)"
}

phase_end() {
  local elapsed=$(($(now_ms) - PHASE_STARTED_MS))
  PHASE_TIMINGS="${PHASE_TIMINGS:+${PHASE_TIMINGS},}\"${CURRENT_PHASE}\":${elapsed}"
  CURRENT_PHASE=""
}

report_run() {
  local outcome="$1" failed_phase="${2:-}" message body
  message="$(printf '%s' "${3:-}" | tr -d '[:cntrl:]')"
  body="$(jq -cn --arg startedAt "${RUN_STARTED_AT}" --arg outcome "${outcome}" \
    --arg failedPhase "${failed_phase}" --arg message "${message:0:200}" \
    --argjson phases "{${PHASE_TIMINGS}}" \
    '{startedAt: $startedAt, outcome: $outcome, phases: $phases}
     + (if $failedPhase == "" then {} else {failedPhase: $failedPhase} end)
     + (if $message == "" then {} else {message: $message} end)')" \
    || { log "WARN: could not build the run report"; return 0; }
  if curl_api --max-time "${REPORT_TIMEOUT}" -X POST -H 'Content-Type: application/json' \
      --data "${body}" -o /dev/null "${API}/redeploys"; then
    log "reported the ${outcome} run"
  else
    log "WARN: could not report the ${outcome} run; it is recorded only in this log: ${body}"
  fi
}

# EXIT trap while a run is in flight: reaching it means the run failed in CURRENT_PHASE.
on_run_exit() {
  local status=$?
  set +e
  trap - EXIT
  if [ -n "${CURRENT_PHASE}" ]; then
    local failed="${CURRENT_PHASE}"
    phase_end
    report_run failure "${failed}" "${LAST_ERROR:-${failed} failed with exit status ${status}}"
  fi
  exit "${status}"
}

redeploy_run() {
  local staged staged_config
  RUN_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  PHASE_TIMINGS=""
  LAST_ERROR=""
  [ "${DRY_RUN}" = "1" ] || trap on_run_exit EXIT

  phase_begin fetch
  staged="$(fetch_staged)"
  staged_config="$(fetch_staged_config)"
  phase_end

  phase_begin promote
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
  phase_end

  if [ "${DRY_RUN}" = "1" ]; then
    log "dry run: stopping after promotion (no build, restart, discard, or report)"
    return 0
  fi

  phase_begin build
  log "phase 3: build"
  build
  phase_end

  phase_begin restart
  log "phase 4: restart"
  restart
  phase_end

  phase_begin verify
  wait_for_health
  [ -n "${staged}" ] && discard_staged
  [ -n "${staged_config}" ] && discard_staged_config
  phase_end

  trap - EXIT
  report_run success
  log "redeploy complete"
}

# Run directly only when executed, not when sourced by a reference implementation.
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  redeploy_run
fi
