#!/usr/bin/env bash
# Startup and memory footprint of one Qlawkus build, measured the way the Quarkus team measures
# its own (https://quarkus.io/guides/performance-measure, benchmark repo
# quarkusio/spring-quarkus-perf-comparison at 9b3fb2381fb1d701f1ddb8b3996ab54eacc9148c).
#
# Thin orchestrator: time to first request comes from the vendored upstream script
# (scripts/perf/time-to-1st-request.sh); RSS is the upstream measure-rss sequence using pmap.
# Known differences from upstream, forced by GitHub-hosted runners: no sync-drop-fs-cache
# (needs root) and no taskset CPU pinning (needs dedicated cores).
#
# Usage: scripts/measure-native.sh <binary|app.jar> [options]
#
#   --label NAME            name recorded in the output (default: artifact basename)
#   --env-file FILE         KEY=VALUE lines exported into the process environment
#   --port N                HTTP port, passed as -Dquarkus.http.port (default: 8080)
#   --iterations N          repetitions of each measurement (default: 3, as upstream)
#   --pause SECONDS         upstream PAUSE_TIME (default: 5)
#   --timeout SECONDS       upstream timeout around the TTFR run (default: 60)
#   --build-peak-rss VALUE  native build Peak RSS as printed by native-image, e.g. 4.56GB
#   --build-seconds N       wall time of the build that produced the binary
#   --md                    also print the Markdown table row on stderr
#
# Prints one JSON object on stdout. A .jar is measured as a JVM run; its size is the whole
# directory holding it (the lib/ tree), since the jar itself is only a stub. Linux only.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TTFR_SCRIPT="$HERE/perf/time-to-1st-request.sh"

usage() {
  sed -n '2,/^set -euo pipefail/{/^set -euo pipefail/d;s/^# \{0,1\}//;p}' "${BASH_SOURCE[0]}"
}

if [ "$#" -lt 1 ]; then
  usage >&2
  exit 2
fi
case "$1" in
  -h|--help) usage; exit 0 ;;
esac

artifact="$1"
shift

label=""
env_file=""
port=8080
iterations=3
pause=5
timeout_s=60
build_peak_rss=""
build_seconds=""
emit_md=false

while [ "$#" -gt 0 ]; do
  case "$1" in
    --label) label="$2"; shift 2 ;;
    --env-file) env_file="$2"; shift 2 ;;
    --port) port="$2"; shift 2 ;;
    --iterations) iterations="$2"; shift 2 ;;
    --pause) pause="$2"; shift 2 ;;
    --timeout) timeout_s="$2"; shift 2 ;;
    --build-peak-rss) build_peak_rss="$2"; shift 2 ;;
    --build-seconds) build_seconds="$2"; shift 2 ;;
    --md) emit_md=true; shift ;;
    *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [ ! -f "$artifact" ]; then
  echo "no such artifact: $artifact" >&2
  exit 2
fi
artifact="$(realpath "$artifact")"
case "$artifact" in
  *[[:space:]]*)
    echo "artifact path must not contain whitespace (RUN_CMD is word-split by the upstream script): $artifact" >&2
    exit 2
    ;;
esac
artifact_name="$(basename "$artifact")"
label="${label:-$artifact_name}"

if [ -n "$env_file" ]; then
  if [ ! -f "$env_file" ]; then
    echo "no such env file: $env_file" >&2
    exit 2
  fi
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      ''|\#*) continue ;;
    esac
    export "${line%%=*}=${line#*=}"
  done < "$env_file"
fi

if [[ "$artifact" == *.jar ]]; then
  kind=jvm
  size_bytes=$(du -sb "$(dirname "$artifact")" | cut -f1)
else
  kind=native
  if [ ! -x "$artifact" ]; then
    echo "not executable: $artifact" >&2
    exit 2
  fi
  size_bytes=$(stat -c %s "$artifact")
fi

work="$(mktemp -d)"
target="http://localhost:${port}/q/health/ready"

props="-Dquarkus.http.port=${port} -Dqlawkus.agent.state.root=${work}/state -Dqlawkus.agent.facts.root=${work}/facts -Dqlawkus.agent.episodic.root=${work}/journals -Dqlawkus.agent.working-memory.root=${work}/working-memory -Dqlawkus.skills.roots=${work}/skills -Dqlawkus.secrets.keystore-path=${work}/secrets.p12"
if [ "$kind" = jvm ]; then
  run_cmd="java ${props} -jar ${artifact}"
else
  run_cmd="${artifact} ${props}"
fi

if curl -s -o /dev/null "$target"; then
  echo "something already answers on $target: stop it or pass --port" >&2
  exit 2
fi

app_pid=""

alive() {
  [ -r "/proc/$1/status" ] && ! grep -q '^State:[[:space:]]*Z' "/proc/$1/status"
}

cleanup() {
  if [ -n "$app_pid" ] && alive "$app_pid"; then
    kill -KILL "$app_pid" 2>/dev/null || true
  fi
  pkill -KILL -f -- "-Dqlawkus.agent.state.root=${work}/state" 2>/dev/null || true
  rm -rf "$work"
}
trap cleanup EXIT

fail() {
  echo "$1" >&2
  if [ -n "${2:-}" ] && [ -f "$2" ]; then
    echo "--- last lines of $2 ---" >&2
    tail -n 40 "$2" >&2 || true
  fi
  exit 1
}

# Upstream measure-time-to-first-request: timeout 60s around the script, then pause.
ttfr_ms=()
for i in $(seq 1 "$iterations"); do
  log="$work/ttfr-$i.log"
  set +e
  out="$(timeout "${timeout_s}s" "$TTFR_SCRIPT" "$run_cmd" "$target" "$log")"
  rc=$?
  set -e
  if [ "$rc" -ne 0 ]; then
    fail "time-to-first-request iteration $i failed (exit $rc; 124 means it did not start within ${timeout_s}s)" "$log"
  fi
  ttfr_ns=$(printf '%s\n' "$out" | sed -n 's/^TTFR=\([0-9][0-9]*\) ns$/\1/p')
  if [ -z "$ttfr_ns" ]; then
    fail "time-to-first-request iteration $i printed no TTFR line: $out"
  fi
  ttfr_ms+=("$(( ttfr_ns / 1000000 ))")
  sleep "$pause"
done

# Upstream measure-rss: start, pause, pmap total (KiB); one request; pmap again; SIGTERM; pause.
rss_startup_kib=()
rss_first_kib=()
for i in $(seq 1 "$iterations"); do
  log="$work/rss-$i.log"
  # shellcheck disable=SC2086
  $run_cmd > "$log" 2>&1 &
  app_pid=$!
  sleep "$pause"
  alive "$app_pid" || fail "process exited before the RSS sample (iteration $i)" "$log"
  rss_startup_kib+=("$(pmap -x "$app_pid" | grep total | awk '{print $4}')")
  curl -s "$target" > /dev/null || fail "first request to $target failed: the app was not listening after the ${pause}s pause (raise --pause for slow JVM starts)" "$log"
  rss_first_kib+=("$(pmap -x "$app_pid" | grep total | awk '{print $4}')")
  kill -15 "$app_pid"
  sleep "$pause"
  if alive "$app_pid"; then
    kill -KILL "$app_pid" 2>/dev/null || true
  fi
  wait "$app_pid" 2>/dev/null || true
  app_pid=""
done

mean() {
  printf '%s\n' "$@" | awk '{ s += $1 } END { printf "%.1f", s / NR }'
}
kib_to_mib() {
  printf '%s\n' "$@" | awk '{ printf "%.1f\n", $1 / 1024 }'
}

ttfr_avg=$(mean "${ttfr_ms[@]}")
startup_mib=($(kib_to_mib "${rss_startup_kib[@]}"))
first_mib=($(kib_to_mib "${rss_first_kib[@]}"))
startup_avg=$(mean "${startup_mib[@]}")
first_avg=$(mean "${first_mib[@]}")

json=$(jq -cn \
  --arg label "$label" \
  --arg kind "$kind" \
  --arg artifact "$artifact_name" \
  --argjson size_bytes "$size_bytes" \
  --argjson ttfr_ms "$(printf '%s\n' "${ttfr_ms[@]}" | jq -cs '.')" \
  --argjson ttfr_avg_ms "$ttfr_avg" \
  --argjson rss_startup_mib "$(printf '%s\n' "${startup_mib[@]}" | jq -cs '.')" \
  --argjson rss_startup_avg_mib "$startup_avg" \
  --argjson rss_first_request_mib "$(printf '%s\n' "${first_mib[@]}" | jq -cs '.')" \
  --argjson rss_first_request_avg_mib "$first_avg" \
  --arg build_peak_rss "$build_peak_rss" \
  --arg build_seconds "$build_seconds" \
  --argjson iterations "$iterations" \
  --argjson pause_s "$pause" \
  --arg measured_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  '{
    label: $label, kind: $kind, artifact: $artifact, size_bytes: $size_bytes,
    ttfr_ms: $ttfr_ms, ttfr_avg_ms: $ttfr_avg_ms,
    rss_startup_mib: $rss_startup_mib, rss_startup_avg_mib: $rss_startup_avg_mib,
    rss_first_request_mib: $rss_first_request_mib, rss_first_request_avg_mib: $rss_first_request_avg_mib,
    native_build_peak_rss: (if $build_peak_rss == "" then null else $build_peak_rss end),
    build_seconds: (if $build_seconds == "" then null else ($build_seconds | tonumber) end),
    iterations: $iterations, pause_s: $pause_s,
    method: "quarkusio/spring-quarkus-perf-comparison@9b3fb2381fb1d701f1ddb8b3996ab54eacc9148c",
    measured_at: $measured_at
  }')
printf '%s\n' "$json"

if [ "$emit_md" = true ]; then
  peak="${build_peak_rss:-n/a}"
  awk -v l="$label" -v s="$size_bytes" -v t="$ttfr_avg" -v a="$startup_avg" -v f="$first_avg" -v p="$peak" 'BEGIN {
    printf "| %s | %.1f MiB | %s ms | %s MiB | %s MiB | %s |\n", l, s / 1048576, t, a, f, p
  }' >&2
fi
