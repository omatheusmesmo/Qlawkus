#!/usr/bin/env bash
set -euo pipefail

# Build and run the Qlawkus stack in a chosen mode. No host JDK/Maven needed: the
# images compile the whole reactor in-container (multi-stage Dockerfiles under
# app/src/main/docker/). Requires only Docker with BuildKit (default in Compose v2).

cd "$(dirname "${BASH_SOURCE[0]}")"

APP_PORT=8742
PG_PORT=5432
GRAFANA_PORT=3000
OLD_PG_VOLUME="${OLD_VOLUME:-qlawkus_pgdata}"
NEW_PG_VOLUME="${NEW_VOLUME:-qlawkus_postgres}"
READY_TIMEOUT="${QLAWKUS_READY_TIMEOUT:-300}"

usage() {
  cat <<'EOF'
Usage: ./run.sh <mode> [action] [options]
       ./run.sh migrate-postgres [mode]

Modes:
  local    Ollama for both primary and fallback models, no external LLM key.  -> docker-compose.local.yml
  prod     NVIDIA primary + Ollama fallback + TTS (set LLM_API_KEY in .env).   -> docker-compose.yml
  native   prod stack, app compiled to a GraalVM native binary (heavy build).  -> docker-compose.yml (profile native)

Actions (default: up):
  up       Check the host first, then build (if needed), start the stack in the
           background and wait for the agent's /q/health/ready.
  build    Build images only, do not start.
  down     Stop and remove the stack containers (volumes are kept).
  logs     Follow the app logs.
  restart  Rebuild and recreate just the app container (same as: up --recreate).
  ps       Show stack status.
  status   Show service, state and URL for every service of the stack.

Options:
  --replace   Stop the app container of the other mode (jvm vs native share port 8742)
              of this same stack instead of refusing. Nothing else is ever stopped.
  --recreate  Force-recreate only the app container. Use it when a container was left
              with a stale resolver after a failed start and cannot resolve its peers.
  --no-wait   Do not wait for the agent to become ready.

migrate-postgres:
  Moves the data of the PostgreSQL 17 volume (qlawkus_pgdata) into the PostgreSQL 19
  volume (qlawkus_postgres) by dump and restore, with row counts compared. The stack
  must be stopped; the old volume is never modified or removed. See scripts/migrate-postgres.sh.

Before it builds, up refuses to start when host port 8742, 5432 or 3000 is held by
something this stack would not reuse, and when the old PostgreSQL volume holds data but
the new one does not exist yet (starting would create an empty database).

Environment:
  QLAWKUS_IMAGE_VARIANT  base (default) or devtools: the JVM image plus git and gh,
                         which the code-review tools shell out to. local/prod only.
  QLAWKUS_READY_TIMEOUT  Seconds to wait for readiness after up (default 300).

Examples:
  ./run.sh prod            # check, build and start the production stack
  ./run.sh local up
  ./run.sh native up --replace   # stop the jvm app container, then start native
  ./run.sh native build
  ./run.sh prod logs
  ./run.sh prod up --recreate
  ./run.sh migrate-postgres
  QLAWKUS_IMAGE_VARIANT=devtools ./run.sh prod   # JVM image with git and gh
EOF
}

die() { echo "error: $*" >&2; exit 1; }

positional=()
replace=0
recreate=0
wait_ready=1
for arg in "$@"; do
  case "$arg" in
    -h|--help) usage; exit 0 ;;
    --replace) replace=1 ;;
    --recreate) recreate=1 ;;
    --no-wait) wait_ready=0 ;;
    -*) echo "Unknown option: $arg" >&2; echo; usage; exit 1 ;;
    *) positional+=("$arg") ;;
  esac
done

mode="${positional[0]:-}"
action="${positional[1]:-up}"
[ "${#positional[@]}" -le 2 ] || die "too many arguments"

set_mode() {
  case "$1" in
    local)  files=(-f docker-compose.local.yml); app_svc="qlawkus";        other_svc="qlawkus-native"; profile=() ;;
    prod)   files=(-f docker-compose.yml);       app_svc="qlawkus";        other_svc="qlawkus-native"; profile=() ;;
    native) files=(-f docker-compose.yml);       app_svc="qlawkus-native"; other_svc="qlawkus";        profile=(--profile native) ;;
    *) return 1 ;;
  esac
}

dc() { docker compose "${files[@]}" "${profile[@]}" "$@"; }

project_name() {
  local name
  name=$(dc config 2>/dev/null | sed -n 's/^name: //p' | head -n1) || true
  if [ -z "$name" ]; then
    name="${COMPOSE_PROJECT_NAME:-$(basename "$PWD" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9_-')}"
  fi
  printf '%s' "$name"
}

volume_exists() { docker volume inspect "$1" >/dev/null 2>&1; }

old_volume_holds_data() {
  local image state
  image=$(dc config --images 2>/dev/null | grep -m1 'pgvector' || true)
  [ -n "$image" ] || return 0
  state=$(docker run --rm --entrypoint sh -v "$OLD_PG_VOLUME:/v:ro" "$image" \
    -c 'if [ -s /v/PG_VERSION ]; then echo data; else echo empty; fi' 2>/dev/null) || state=unknown
  [ "$state" != empty ]
}

check_postgres_volume() {
  volume_exists "$OLD_PG_VOLUME" || return 0
  ! volume_exists "$NEW_PG_VOLUME" || return 0
  old_volume_holds_data || return 0
  cat >&2 <<EOF
error: refusing to start. The PostgreSQL 17 volume $OLD_PG_VOLUME holds data and $NEW_PG_VOLUME
does not exist yet: starting now would create an empty PostgreSQL 19 database and the agent
would boot with no memory.

Move the data first (the stack must be stopped; the old volume is never modified):

    ./run.sh migrate-postgres

To start with an empty database on purpose, create the volume yourself:

    docker volume create $NEW_PG_VOLUME
EOF
  exit 1
}

publishers_of() {
  docker ps --filter "publish=$1" \
    --format '{{.Names}}|{{.Label "com.docker.compose.project"}}|{{.Label "com.docker.compose.service"}}'
}

port_listening() {
  if command -v ss >/dev/null 2>&1; then
    [ -n "$(ss -H -ltn "sport = :$1" 2>/dev/null)" ]
  else
    (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
  fi
}

preflight_ports() {
  local project spec port expected holders name proj svc conflicts=0 other_mode=0
  project=$(project_name)
  for spec in "$@"; do
    port="${spec%%:*}"
    expected="${spec#*:}"
    holders=$(publishers_of "$port")
    if [ -n "$holders" ]; then
      while IFS='|' read -r name proj svc; do
        [ -n "$name" ] || continue
        if [ "$proj" = "$project" ] && [ "$svc" = "$expected" ]; then
          continue
        fi
        if [ "$proj" = "$project" ] && [ "$svc" = "$other_svc" ] && [ "$port" = "$APP_PORT" ]; then
          if [ "$replace" = 1 ]; then
            echo "Stopping $name (service $svc, the other mode of this stack) to free port $port"
            docker stop "$name" >/dev/null
            continue
          fi
          other_mode=1
          echo "error: host port $port is held by $name, the $svc service of this stack (the other mode)." >&2
        else
          echo "error: host port $port is held by container $name (compose project '${proj:-none}', service '${svc:-none}')." >&2
        fi
        echo "       stop it with: docker stop $name" >&2
        conflicts=1
      done <<<"$holders"
    elif port_listening "$port"; then
      echo "error: host port $port is held by a process outside Docker (see: ss -ltnp 'sport = :$port')." >&2
      conflicts=1
    fi
  done
  if [ "$other_mode" = 1 ]; then
    echo "       or rerun with --replace to stop only $other_svc: ./run.sh $mode $action --replace" >&2
  fi
  [ "$conflicts" = 0 ] || die "nothing was built or started; free the port(s) above and retry"
}

app_ready() {
  if command -v curl >/dev/null 2>&1; then
    curl -fsS -m 5 -o /dev/null "http://localhost:$APP_PORT/q/health/ready" >/dev/null 2>&1
  else
    (
      exec 3<>"/dev/tcp/127.0.0.1/$APP_PORT" || exit 1
      printf 'GET /q/health/ready HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3
      line=""
      read -r -t 5 line <&3 || true
      [[ "$line" == *" 200"* ]]
    ) 2>/dev/null
  fi
}

service_url() {
  case "$1" in
    "$app_svc") echo "http://localhost:$APP_PORT" ;;
    lgtm) echo "http://localhost:$GRAFANA_PORT" ;;
    postgres) echo "localhost:$PG_PORT" ;;
    *) echo "-" ;;
  esac
}

print_status() {
  local svc state health
  printf '%-16s %-22s %s\n' SERVICE STATE URL
  while IFS='|' read -r svc state health; do
    [ -n "$svc" ] || continue
    [ -z "$health" ] || state="$state ($health)"
    printf '%-16s %-22s %s\n' "$svc" "$state" "$(service_url "$svc")"
  done < <(dc ps -a --format '{{.Service}}|{{.State}}|{{.Health}}')
}

stale_resolver_services() {
  local cid net servers svc
  for cid in $(dc ps -q); do
    net=$(docker inspect -f '{{.HostConfig.NetworkMode}}' "$cid" 2>/dev/null) || continue
    case "$net" in default|bridge|host|none|container:*) continue ;; esac
    servers=$(docker cp "$cid:/etc/resolv.conf" - 2>/dev/null | tar -xO 2>/dev/null \
      | sed -n 's/^nameserver[[:space:]]*//p') || continue
    [ -n "$servers" ] || continue
    if ! grep -qx '127.0.0.11' <<<"$servers"; then
      svc=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.service"}}' "$cid")
      echo "$svc $(head -n1 <<<"$servers")"
    fi
  done
}

report_stale_resolvers() {
  local stale svc server app_stale=1
  stale=$(stale_resolver_services)
  [ -n "$stale" ] || return 1
  while read -r svc server; do
    echo "warning: $svc resolves through $server instead of the Docker network resolver 127.0.0.11, so it cannot reach its peers by name (typically left over from a failed start)." >&2
    if [ "$svc" = "$app_svc" ]; then
      echo "         fix: ./run.sh $mode up --recreate" >&2
      app_stale=0
    else
      echo "         fix: docker compose ${files[*]} ${profile[*]} up -d --force-recreate --no-deps $svc" >&2
    fi
  done <<<"$stale"
  return "$app_stale"
}

wait_for_agent() {
  local deadline=$((SECONDS + $1)) cid state
  echo "Waiting up to ${1}s for http://localhost:$APP_PORT/q/health/ready ..."
  while [ "$SECONDS" -lt "$deadline" ]; do
    cid=$(dc ps -aq "$app_svc" | head -n1)
    state=$(docker inspect -f '{{.State.Status}}' "$cid" 2>/dev/null || echo missing)
    case "$state" in
      exited|dead|missing) echo "error: the $app_svc container is $state." >&2; return 1 ;;
    esac
    if app_ready; then return 0; fi
    sleep 3
  done
  echo "error: the agent was not ready after ${1}s." >&2
  return 1
}

fail_with_logs() {
  print_status >&2 || true
  echo >&2
  echo "Last 40 log lines of $app_svc:" >&2
  dc logs --tail 40 --no-log-prefix "$app_svc" >&2 || true
  exit 1
}

after_start() {
  local timeout="$READY_TIMEOUT" stale_app=0
  report_stale_resolvers && stale_app=1
  if [ "$wait_ready" = 1 ]; then
    [ "$stale_app" = 0 ] || [ "$timeout" -le 30 ] || timeout=30
    wait_for_agent "$timeout" || fail_with_logs
  fi
  echo
  print_status
  echo
  echo "Stack up. App on http://localhost:$APP_PORT, Grafana on http://localhost:$GRAFANA_PORT  (./run.sh $mode logs to follow)"
}

start_failed() {
  local code=$1
  report_stale_resolvers || true
  echo "error: docker compose failed to start the stack (exit $code)." >&2
  exit "$code"
}

if [ "$mode" = migrate-postgres ]; then
  if [ -n "$action" ] && [ "${#positional[@]}" -ge 2 ]; then
    set_mode "$action" || die "unknown mode: $action"
    if [ -n "$(dc ps -q --status running postgres 2>/dev/null)" ]; then
      die "the postgres service of this stack is running; stop it first: ./run.sh $action down"
    fi
  fi
  exec scripts/migrate-postgres.sh
fi

case "$mode" in
  -h|--help|"") usage; exit 0 ;;
esac
set_mode "$mode" || { echo "Unknown mode: $mode" >&2; echo; usage; exit 1; }

# The devtools variant exists only for the JVM image: the native runtime is the
# micro image, which has no package manager to install git and gh with.
if [ "$mode" = native ] && [ "${QLAWKUS_IMAGE_VARIANT:-base}" != base ]; then
  echo "QLAWKUS_IMAGE_VARIANT=${QLAWKUS_IMAGE_VARIANT} is JVM only; use ./run.sh prod for it." >&2
  exit 1
fi

# In native mode the profile-less jvm 'qlawkus' service must NOT start (it shares
# port 8742), so the services are listed explicitly. local/prod use the default set.
native_services=(qlawkus-native postgres ollama ollama-init tts tts-init lgtm)

export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

recreate_app() {
  preflight_ports "$APP_PORT:$app_svc"
  dc up -d --build --force-recreate --no-deps "$app_svc" || start_failed $?
  after_start
}

case "$action" in
  up)
    if [ "$recreate" = 1 ]; then
      recreate_app
    else
      check_postgres_volume
      preflight_ports "$APP_PORT:$app_svc" "$PG_PORT:postgres" "$GRAFANA_PORT:lgtm"
      if [ "$mode" = native ]; then
        dc up -d --build "${native_services[@]}" || start_failed $?
      else
        dc up -d --build || start_failed $?
      fi
      after_start
    fi
    ;;
  build)   dc build "$app_svc" ;;
  down)    dc down ;;
  logs)    dc logs -f "$app_svc" ;;
  restart) recreate_app ;;
  ps)      dc ps ;;
  status)  print_status ;;
  *) echo "Unknown action: $action" >&2; echo; usage; exit 1 ;;
esac
