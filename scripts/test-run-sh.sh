#!/usr/bin/env bash
# Logic test for run.sh with a fake docker, ss and curl on the PATH: nothing real is started,
# stopped or built. Covers the port preflight, the PostgreSQL volume guard, the readiness wait
# and the stale resolver hint. Usage: scripts/test-run-sh.sh
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
bin="$work/bin"
state="$work/state"
mkdir -p "$bin" "$state"

cat > "$bin/docker" <<'SHIM'
#!/usr/bin/env bash
s="$FAKE_STATE"
echo "docker $*" >> "$s/calls"
case "$1" in
  compose)
    shift
    while [ "$#" -gt 0 ]; do
      case "$1" in -f|--profile) shift 2 ;; *) break ;; esac
    done
    sub="$1"; shift
    case "$sub" in
      config)
        if [ "${1:-}" = "--images" ]; then echo "pgvector/pgvector:0.8.7-pg19"; else echo "name: qlawkus"; fi ;;
      up) exit "$(cat "$s/up_exit" 2>/dev/null || echo 0)" ;;
      ps)
        case " $* " in
          *" -aq "*|*" -q "*) cat "$s/containers" 2>/dev/null || true ;;
          *) printf 'qlawkus|running|healthy\npostgres|running|healthy\nlgtm|running|\n' ;;
        esac ;;
      logs) echo "fake log line" ;;
    esac ;;
  ps)
    port="${3#publish=}"
    cat "$s/publish_$port" 2>/dev/null || true ;;
  volume) grep -qx "$3" "$s/volumes" 2>/dev/null ;;
  run) cat "$s/old_state" 2>/dev/null || echo data ;;
  stop) echo "$2" >> "$s/stopped" ;;
  inspect)
    case "$*" in
      *NetworkMode*) echo "qlawkus_default" ;;
      *Labels*) cat "$s/svc_label" 2>/dev/null || echo qlawkus ;;
      *State.Status*) echo running ;;
    esac ;;
  cp) tar -C "$s" -c resolv.conf ;;
esac
SHIM

cat > "$bin/ss" <<'SHIM'
#!/usr/bin/env bash
port="${@: -1}"
port="${port#*:}"
[ -f "$FAKE_STATE/listen_$port" ] && echo "LISTEN 0 4096 0.0.0.0:$port 0.0.0.0:*" || true
SHIM

cat > "$bin/curl" <<'SHIM'
#!/usr/bin/env bash
exit "$(cat "$FAKE_STATE/curl_exit" 2>/dev/null || echo 0)"
SHIM
chmod +x "$bin"/*

export FAKE_STATE="$state"
export PATH="$bin:$PATH"
export QLAWKUS_READY_TIMEOUT=2
failures=0

reset() {
  rm -rf "$state"; mkdir -p "$state"
  : > "$state/calls"
  printf 'nameserver 127.0.0.11\n' > "$state/resolv.conf"
  echo cid1 > "$state/containers"
}

run() { set +e; out=$("$root/run.sh" "$@" 2>&1); code=$?; set -e; }

expect() {
  local name="$1" want_code="$2" pattern="$3" forbidden="${4:-}"
  local ok=1
  [ "$code" = "$want_code" ] || ok=0
  local haystack
  haystack=$(printf '%s\n' "$out"; cat "$state/calls" 2>/dev/null || true; cat "$state/stopped" 2>/dev/null || true)
  haystack=$(tr '\n' ' ' <<<"$haystack")
  grep -qE -- "$pattern" <<<"$haystack" || ok=0
  if [ -n "$forbidden" ] && grep -qE -- "$forbidden" "$state/calls"; then ok=0; fi
  if [ "$ok" = 1 ]; then echo "ok   $name"; else echo "FAIL $name (exit $code, wanted $want_code)"; echo "$out" | sed 's/^/     /'; failures=$((failures + 1)); fi
}

reset; echo "qlawkus-qlawkus-1|qlawkus|qlawkus" > "$state/publish_8742"
run native up
expect "jvm container on 8742 blocks native up before any build" 1 "stop it with: docker stop qlawkus-qlawkus-1.*--replace" "compose.* up "

reset; echo "qlawkus-qlawkus-1|qlawkus|qlawkus" > "$state/publish_8742"
run native up --replace
expect "--replace stops only the other mode container, then starts" 0 "qlawkus-qlawkus-1" ""
grep -q "compose.* up -d --build qlawkus-native" "$state/calls" && echo "ok   native services started after --replace" || { echo "FAIL native start after --replace"; failures=$((failures + 1)); }

reset; echo "qlawkus-qlawkus-1|qlawkus|qlawkus" > "$state/publish_8742"
run prod up
expect "same mode container on 8742 is reused" 0 "Stack up" ""

reset; echo "other-postgres-1|other|postgres" > "$state/publish_5432"
run prod up
expect "foreign container on 5432 blocks up" 1 "port 5432 is held by container other-postgres-1.*docker stop other-postgres-1" "compose.* up "

reset; echo "qlawkus-qlawkus-native-1|other|qlawkus-native" > "$state/publish_8742"
run prod up --replace
expect "--replace never stops a container of another project" 1 "port 8742 is held by container qlawkus-qlawkus-native-1" "compose.* up "
[ ! -s "$state/stopped" ] && echo "ok   nothing stopped for a foreign project" || { echo "FAIL foreign container was stopped"; failures=$((failures + 1)); }

reset; touch "$state/listen_3000"
run local up
expect "non-Docker listener on 3000 blocks up" 1 "port 3000 is held by a process outside Docker" "compose.* up "

reset; printf 'qlawkus_pgdata\n' > "$state/volumes"
run prod up
expect "old volume with data and no new volume refuses and names the migration" 1 "refusing to start.*run.sh migrate-postgres" "compose.* up "

reset; printf 'qlawkus_pgdata\n' > "$state/volumes"; echo empty > "$state/old_state"
run prod up
expect "empty old volume does not block" 0 "Stack up" ""

reset; printf 'qlawkus_pgdata\nqlawkus_postgres\n' > "$state/volumes"
run prod up
expect "both volumes present does not block" 0 "Stack up" ""

reset; printf 'qlawkus_pgdata\n' > "$state/volumes"
run prod up --recreate
expect "recreate skips the volume guard and recreates only the app" 0 "up -d --build --force-recreate --no-deps qlawkus" ""

reset; echo 1 > "$state/curl_exit"
run prod up
expect "never ready exits non-zero with status and last log lines" 1 "not ready after 2s.*Last 40 log lines.*fake log line" ""

reset; echo 1 > "$state/up_exit"
run prod up
expect "failed compose up exits with its code" 1 "failed to start the stack" ""

reset; printf 'nameserver 172.17.0.1\n' > "$state/resolv.conf"; echo 1 > "$state/curl_exit"
run prod up
expect "stale resolver on the app prints the recreate hint" 1 "172.17.0.1.*run.sh prod up --recreate" ""

reset
run prod up
expect "healthy stack prints the status screen" 0 "SERVICE +STATE +URL.*http://localhost:8742" ""

reset
run bogus
expect "unknown mode fails" 1 "Unknown mode" ""

reset
run --help
expect "help exits zero" 0 "Usage: ./run.sh" ""

[ "$failures" = 0 ] && echo "all run.sh checks passed" || { echo "$failures run.sh check(s) failed"; exit 1; }
