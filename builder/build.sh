#!/usr/bin/env bash
#
# builder/build.sh <jvm|native>
#
# Rebuilds the Qlawkus app from its composition manifest, on demand. Reconciles the
# app pom from agent.yml (qlawkus:generate), runs the Quarkus build, and emits a
# versioned artifact into SHARED_DIR alongside a build.json and an atomically
# updated 'latest' pointer. One shot: it builds once and exits.
#
# Phase 1 (no Docker): runs with the toolchain of wherever it is invoked, against
# the reactor it lives in. A later phase packages this into builder images. See
# builder/README.md for the output contract.
#
# Knobs (env):
#   STAGING_AGENT_YML  optional manifest to stage before building; when unset, the
#                      app's current agent.yml is built as-is.
#   SHARED_DIR         output root for versioned builds (default target/qlawkus-builds).
#   BUILD_RETENTION    number of builds to keep; older ones are pruned (default 3).
#   MVN_CMD            Maven command (default the repo mvnw); overridden by tests.
set -euo pipefail

usage() { echo "usage: build.sh <jvm|native>" >&2; exit 2; }
log()   { echo "[builder] $*"; }
fail()  { echo "[builder] ERROR: $*" >&2; exit 1; }

# Reads capability names on stdin (one per line) and emits a JSON array.
json_array_from() {
  local first=1 out="["
  local name
  while IFS= read -r name; do
    [[ -z "$name" ]] && continue
    if [[ $first -eq 1 ]]; then first=0; else out+=","; fi
    out+="\"$name\""
  done
  out+="]"
  printf '%s' "$out"
}

# Writes build.json into the given dir, parsing the resolved capability set from the
# captured generate log (best-effort; the raw generate.log is kept alongside).
write_build_json() {
  local dir="$1" mode="$2" ts="$3" artifact="$4" glog="$5"
  local sha selected excluded
  sha="$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  selected="$(sed -nE 's/^\[INFO\][[:space:]]+\+ ([^ ]+) \(.*/\1/p' "$glog" | json_array_from)"
  excluded="$(sed -nE 's/^\[INFO\][[:space:]]+- ([^ ]+) \(.*/\1/p' "$glog" | json_array_from)"
  cat > "$dir/build.json" <<JSON
{
  "mode": "$mode",
  "timestamp": "$ts",
  "sourceGitSha": "$sha",
  "artifact": "$artifact",
  "status": "success",
  "resolvedCapabilities": {
    "selected": $selected,
    "excluded": $excluded
  }
}
JSON
}

# Keeps the newest $keep timestamped build dirs, removing older ones. The 'latest'
# symlink and any .staging-* dirs are never counted.
prune_old_builds() {
  local dir="$1" keep="$2"
  local builds b i=0
  mapfile -t builds < <(find "$dir" -mindepth 1 -maxdepth 1 -type d -name '2*' | sort -r)
  for b in "${builds[@]}"; do
    i=$((i + 1))
    if (( i > keep )); then
      rm -rf "$b"
      log "pruned old build $(basename "$b")"
    fi
  done
}

MODE="${1:-}"
case "$MODE" in
  jvm|native) ;;
  *) usage ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

STAGING_AGENT_YML="${STAGING_AGENT_YML:-}"
SHARED_DIR="${SHARED_DIR:-$REPO_ROOT/target/qlawkus-builds}"
BUILD_RETENTION="${BUILD_RETENTION:-3}"
MVN_CMD="${MVN_CMD:-$REPO_ROOT/mvnw}"
APP_MANIFEST="$REPO_ROOT/app/src/main/resources/qlawkus/agent.yml"

# 1. Stage the manifest (optional override; default builds what the app already declares).
if [[ -n "$STAGING_AGENT_YML" ]]; then
  [[ -f "$STAGING_AGENT_YML" ]] || fail "staged agent.yml not found: $STAGING_AGENT_YML"
  mkdir -p "$(dirname "$APP_MANIFEST")"
  cp "$STAGING_AGENT_YML" "$APP_MANIFEST"
  log "staged manifest from $STAGING_AGENT_YML"
fi

cd "$REPO_ROOT"

# 2. Reconcile the app pom from the manifest by running the generate-sources phase:
#    the app pom binds qlawkus:generate there, so this is the same seam dev mode uses
#    (a plugin-prefix goal would not resolve from the root reactor). The reactor is
#    visible via -am so ${reactorProjects} populates the catalog. Captured for build.json.
generate_log="$(mktemp)"
trap 'rm -f "$generate_log"' EXIT
log "reconciling app pom from manifest (generate-sources)"
"$MVN_CMD" -B -e -pl app -am generate-sources | tee "$generate_log"

# 3. Build. A FRESH Maven session, so it reads the pom that step 2 just rewrote.
native_flags=()
[[ "$MODE" == native ]] && native_flags=(-Pnative)
log "building app ($MODE)"
"$MVN_CMD" -B -e -pl app -am -DskipTests -DskipITs "${native_flags[@]}" package

# 4. Emit the artifact atomically into a timestamped dir, then flip 'latest'.
ts="$(date -u +%Y%m%dT%H%M%SZ)"
staging_out="$SHARED_DIR/.staging-$ts"
final_out="$SHARED_DIR/$ts"
rm -rf "$staging_out"
mkdir -p "$staging_out"

if [[ "$MODE" == jvm ]]; then
  [[ -d app/target/quarkus-app ]] || fail "jvm build output app/target/quarkus-app not found"
  cp -r app/target/quarkus-app "$staging_out/quarkus-app"
  artifact_rel="quarkus-app/quarkus-run.jar"
else
  runner="$(find app/target -maxdepth 1 -type f -name '*-runner' | head -n1)"
  [[ -n "$runner" ]] || fail "native build output app/target/*-runner not found"
  cp "$runner" "$staging_out/application"
  chmod +x "$staging_out/application"
  artifact_rel="application"
fi

# The manifest is optional (no agent.yml means the pom is built as-is); capture it
# for provenance only when present.
[[ -f "$APP_MANIFEST" ]] && cp "$APP_MANIFEST" "$staging_out/agent.yml"
cp "$generate_log" "$staging_out/generate.log"
write_build_json "$staging_out" "$MODE" "$ts" "$artifact_rel" "$generate_log"

# Atomic publish: move the complete dir into place, then repoint 'latest'. A build
# that failed above never reaches here, so 'latest' always names a complete build.
mv "$staging_out" "$final_out"
ln -sfn "$ts" "$SHARED_DIR/latest"
log "published $final_out"
log "latest -> $ts"

prune_old_builds "$SHARED_DIR" "$BUILD_RETENTION"
log "done ($MODE)"
