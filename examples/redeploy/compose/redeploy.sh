#!/usr/bin/env bash
#
# Reference implementation: docker-compose.
#
# Compose rebuilds and recreates the app container in one step, so `build` is a no-op
# and `restart` does both via `up -d --build`. The multi-stage Dockerfile.jvm-build
# runs `qlawkus:generate` inside the build stage, so the promoted agent.yml decides the
# new capability set. Native: point SERVICE at qlawkus-native and add `--profile native`.
#
# Run from the repository root (compose needs docker-compose.yml; promotion writes the
# source agent.yml):
#   QLAWKUS_ADMIN_USER=... QLAWKUS_ADMIN_PASSWORD=... examples/redeploy/compose/redeploy.sh
#
set -euo pipefail

SERVICE="${QLAWKUS_COMPOSE_SERVICE:-qlawkus}"
COMPOSE_FILE="${QLAWKUS_COMPOSE_FILE:-docker-compose.yml}"

build() { :; }

restart() {
  docker compose -f "${COMPOSE_FILE}" up -d --build --force-recreate --no-deps "${SERVICE}"
}

# shellcheck source=../redeploy.sh
source "$(dirname "${BASH_SOURCE[0]}")/../redeploy.sh"
redeploy_run
