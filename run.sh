#!/usr/bin/env bash
set -euo pipefail

# Build and run the Qlawkus stack in a chosen mode. No host JDK/Maven needed: the
# images compile the whole reactor in-container (multi-stage Dockerfiles under
# app/src/main/docker/). Requires only Docker with BuildKit (default in Compose v2).

usage() {
  cat <<'EOF'
Usage: ./run.sh <mode> [action]

Modes:
  local    Ollama for both primary and fallback models, no external LLM key.  -> docker-compose.local.yml
  prod     NVIDIA primary + Ollama fallback + TTS (set LLM_API_KEY in .env).   -> docker-compose.yml
  native   prod stack, app compiled to a GraalVM native binary (heavy build).  -> docker-compose.yml (profile native)

Actions (default: up):
  up       Build (if needed) and start the stack in the background.
  build    Build images only, do not start.
  down     Stop and remove the stack containers.
  logs     Follow the app logs.
  restart  Rebuild and recreate just the app container.
  ps       Show stack status.

Examples:
  ./run.sh prod            # build + start the production stack
  ./run.sh local up
  ./run.sh native build
  ./run.sh prod logs
EOF
}

mode="${1:-}"
action="${2:-up}"

case "$mode" in
  local)  files=(-f docker-compose.local.yml); app_svc="qlawkus";        profile=() ;;
  prod)   files=(-f docker-compose.yml);        app_svc="qlawkus";        profile=() ;;
  native) files=(-f docker-compose.yml);        app_svc="qlawkus-native"; profile=(--profile native) ;;
  -h|--help|"") usage; exit 0 ;;
  *) echo "Unknown mode: $mode" >&2; echo; usage; exit 1 ;;
esac

# In native mode the profile-less jvm 'qlawkus' service must NOT start (it shares
# port 8742), so the services are listed explicitly. local/prod use the default set.
native_services=(qlawkus-native postgres ollama ollama-init tts tts-init)

export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

dc() { docker compose "${files[@]}" "${profile[@]}" "$@"; }

case "$action" in
  up)
    if [ "$mode" = native ]; then
      dc up -d --build "${native_services[@]}"
    else
      dc up -d --build
    fi
    echo "Stack up. App on http://localhost:8742  (./run.sh $mode logs to follow)"
    ;;
  build)   dc build "$app_svc" ;;
  down)    dc down ;;
  logs)    dc logs -f "$app_svc" ;;
  restart) dc up -d --build --force-recreate --no-deps "$app_svc" ;;
  ps)      dc ps ;;
  *) echo "Unknown action: $action" >&2; echo; usage; exit 1 ;;
esac
