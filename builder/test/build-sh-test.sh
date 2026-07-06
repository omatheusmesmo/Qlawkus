#!/usr/bin/env bash
#
# Contract test for builder/build.sh. Exercises the runner's orchestration and
# output contract with a stub Maven, so it is fast and needs no real build:
#   - jvm happy path: versioned dir, quarkus-app copied, build.json, latest symlink
#   - native happy path: the *-runner binary emitted as 'application'
#   - retention: only BUILD_RETENTION builds kept
#   - atomicity: a failed build leaves 'latest' untouched
set -euo pipefail

THIS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REAL_BUILD_SH="$THIS_DIR/../build.sh"
fails=0

pass() { echo "  PASS: $*"; }
check() { if eval "$2"; then pass "$1"; else echo "  FAIL: $1"; fails=$((fails + 1)); fi; }

# Builds a throwaway fake repo: build.sh at <root>/builder/build.sh so it derives
# REPO_ROOT=<root>, plus a stub mvnw and a seed manifest.
setup_fake_repo() {
  local root="$1"
  mkdir -p "$root/builder" "$root/app/src/main/resources/qlawkus"
  cp "$REAL_BUILD_SH" "$root/builder/build.sh"
  echo "version: 1" > "$root/app/src/main/resources/qlawkus/agent.yml"
  cat > "$root/mvnw" <<'STUB'
#!/usr/bin/env bash
args="$*"
if [[ "$args" == *"generate-sources"* ]]; then
  echo "[INFO] Resolved capability set from agent.yml"
  echo "[INFO]   1 selected, 1 excluded"
  echo "[INFO]   + messaging.discord (dev.omatheusmesmo:qlawkus-messaging-discord)"
  echo "[INFO]   - cognition.pgvector (dev.omatheusmesmo:qlawkus-cognition-pgvector)"
  exit 0
fi
if [[ "$args" == *"package"* ]]; then
  [[ "${FAKE_PACKAGE_FAIL:-0}" == "1" ]] && { echo "[ERROR] simulated failure" >&2; exit 1; }
  if [[ "$args" == *"-Pnative"* ]]; then
    mkdir -p app/target; echo bin > app/target/app-runner
  else
    mkdir -p app/target/quarkus-app/lib; echo jar > app/target/quarkus-app/quarkus-run.jar
  fi
  exit 0
fi
exit 0
STUB
  chmod +x "$root/mvnw"
}

echo "== jvm happy path =="
root="$(mktemp -d)"; setup_fake_repo "$root"
out="$root/out"
SHARED_DIR="$out" MVN_CMD="$root/mvnw" bash "$root/builder/build.sh" jvm >/dev/null
ts_dir="$(find "$out" -mindepth 1 -maxdepth 1 -type d -name '2*' | head -n1)"
check "a versioned build dir is created"          "[[ -n '$ts_dir' ]]"
check "quarkus-app tree is copied"                "[[ -f '$ts_dir/quarkus-app/quarkus-run.jar' ]]"
check "the used manifest is captured"             "[[ -f '$ts_dir/agent.yml' ]]"
check "the generate log is captured"              "[[ -f '$ts_dir/generate.log' ]]"
check "build.json exists"                         "[[ -f '$ts_dir/build.json' ]]"
check "build.json records mode jvm"               "grep -q '\"mode\": \"jvm\"' '$ts_dir/build.json'"
check "build.json records success"                "grep -q '\"status\": \"success\"' '$ts_dir/build.json'"
check "build.json parses selected capability"     "grep -q 'messaging.discord' '$ts_dir/build.json'"
check "build.json parses excluded capability"     "grep -q 'cognition.pgvector' '$ts_dir/build.json'"
check "latest symlink points at the build"        "[[ \"\$(readlink '$out/latest')\" == \"\$(basename '$ts_dir')\" ]]"
rm -rf "$root"

echo "== no manifest (optional agent.yml absent) =="
root="$(mktemp -d)"; setup_fake_repo "$root"
rm -f "$root/app/src/main/resources/qlawkus/agent.yml"
out="$root/out"
SHARED_DIR="$out" MVN_CMD="$root/mvnw" bash "$root/builder/build.sh" jvm >/dev/null
ts_dir="$(find "$out" -mindepth 1 -maxdepth 1 -type d -name '2*' | head -n1)"
check "build publishes even with no manifest"     "[[ -n '$ts_dir' && -f '$ts_dir/build.json' ]]"
check "latest is set with no manifest"            "[[ -n \"\$(readlink '$out/latest')\" ]]"
check "no agent.yml captured when absent"         "[[ ! -f '$ts_dir/agent.yml' ]]"
rm -rf "$root"

echo "== native happy path =="
root="$(mktemp -d)"; setup_fake_repo "$root"
out="$root/out"
SHARED_DIR="$out" MVN_CMD="$root/mvnw" bash "$root/builder/build.sh" native >/dev/null
ts_dir="$(find "$out" -mindepth 1 -maxdepth 1 -type d -name '2*' | head -n1)"
check "native emits the runner as application"    "[[ -f '$ts_dir/application' ]]"
check "the application binary is executable"      "[[ -x '$ts_dir/application' ]]"
check "build.json records mode native"            "grep -q '\"mode\": \"native\"' '$ts_dir/build.json'"
rm -rf "$root"

echo "== retention =="
root="$(mktemp -d)"; setup_fake_repo "$root"
out="$root/out"; mkdir -p "$out/20200101T000000Z" "$out/20200102T000000Z"
SHARED_DIR="$out" BUILD_RETENTION=2 MVN_CMD="$root/mvnw" bash "$root/builder/build.sh" jvm >/dev/null
kept="$(find "$out" -mindepth 1 -maxdepth 1 -type d -name '2*' | wc -l)"
check "retention keeps only BUILD_RETENTION dirs" "[[ '$kept' -eq 2 ]]"
check "the oldest build is pruned"                "[[ ! -d '$out/20200101T000000Z' ]]"
check "a kept newer build survives"               "[[ -d '$out/20200102T000000Z' ]]"
rm -rf "$root"

echo "== atomicity: failed build leaves latest untouched =="
root="$(mktemp -d)"; setup_fake_repo "$root"
out="$root/out"; mkdir -p "$out/20200102T000000Z"; ln -sfn "20200102T000000Z" "$out/latest"
set +e
SHARED_DIR="$out" MVN_CMD="$root/mvnw" FAKE_PACKAGE_FAIL=1 bash "$root/builder/build.sh" jvm >/dev/null 2>&1
rc=$?
set -e
check "build.sh exits non-zero on build failure"  "[[ '$rc' -ne 0 ]]"
check "latest still points at the prior build"    "[[ \"\$(readlink '$out/latest')\" == '20200102T000000Z' ]]"
check "no new versioned dir was published"        "[[ \"\$(find '$out' -mindepth 1 -maxdepth 1 -type d -name '2*' | wc -l)\" -eq 1 ]]"
rm -rf "$root"

echo
if [[ "$fails" -eq 0 ]]; then
  echo "ALL PASSED"
else
  echo "$fails CHECK(S) FAILED"; exit 1
fi
