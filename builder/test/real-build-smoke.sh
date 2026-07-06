#!/usr/bin/env bash
#
# End-to-end smoke for builder/build.sh against the REAL reactor: proves that the
# composition manifest flows all the way into the built artifact (not just the pom).
# A single-session `mvn package` cannot show this - the generator rewrites the pom
# on disk after the model is resolved - which is exactly why build.sh runs generate
# and package as two Maven sessions. This smoke exercises that path for real.
#
# Opt-in and heavy (a full JVM reactor build, minutes). Same tier as the native ITs.
# Prerequisite: a warm local repo, so qlawkus:generate resolves the plugin and the
# reactor's *-deployment modules resolve. From the repo root:
#
#   mvn install -DskipTests -DskipITs
#   STAGING_AGENT_YML=path/to/agent.yml \
#     EXPECT_PRESENT_JAR=qlawkus-client \
#     EXPECT_ABSENT_JAR=qlawkus-cognition-pgvector \
#     builder/test/real-build-smoke.sh
#
# EXPECT_PRESENT_JAR / EXPECT_ABSENT_JAR are jar name prefixes checked against the
# emitted quarkus-app/lib, matched to whatever the staged manifest selects.
set -euo pipefail

THIS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$THIS_DIR/../.." && pwd)"
SHARED_DIR="${SHARED_DIR:-$REPO_ROOT/target/qlawkus-builds}"
export SHARED_DIR

: "${EXPECT_PRESENT_JAR:?set EXPECT_PRESENT_JAR to a jar prefix that must be present}"
: "${EXPECT_ABSENT_JAR:?set EXPECT_ABSENT_JAR to a jar prefix that must be absent}"

echo "[smoke] running the real jvm build via build.sh (this takes minutes)"
bash "$REPO_ROOT/builder/build.sh" jvm

lib="$SHARED_DIR/latest/quarkus-app/lib"
[[ -d "$lib" ]] || { echo "[smoke] FAIL: $lib not found"; exit 1; }

fails=0
if find "$lib" -name "${EXPECT_PRESENT_JAR}*.jar" | grep -q .; then
  echo "[smoke] PASS: selected capability jar ${EXPECT_PRESENT_JAR}* is present"
else
  echo "[smoke] FAIL: expected ${EXPECT_PRESENT_JAR}* in the artifact"; fails=$((fails + 1))
fi
if find "$lib" -name "${EXPECT_ABSENT_JAR}*.jar" | grep -q .; then
  echo "[smoke] FAIL: deselected capability jar ${EXPECT_ABSENT_JAR}* leaked into the artifact"; fails=$((fails + 1))
else
  echo "[smoke] PASS: deselected capability jar ${EXPECT_ABSENT_JAR}* is absent"
fi

[[ "$fails" -eq 0 ]] && echo "[smoke] ALL PASSED" || { echo "[smoke] $fails FAILED"; exit 1; }
