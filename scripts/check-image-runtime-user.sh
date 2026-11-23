#!/usr/bin/env bash
# The JVM and native services mount the same `workspace` volume at /home/default, so both images
# must run as the same user with the same HOME. Otherwise the native one cannot write the
# workspace the JVM image created, and its ~/.qlawkus state lands outside the volume (HOME=/).
set -euo pipefail

dir="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/docker"
jvm="$dir/Dockerfile.jvm-build"
native="$dir/Dockerfile.native-build"

last_user() { grep -E '^USER [0-9]+' "$1" | tail -1 | awk '{print $2}'; }

failed=0
jvm_user="$(last_user "$jvm")"
native_user="$(last_user "$native")"

if [ "$jvm_user" != "$native_user" ]; then
  echo "FAIL: JVM image runs as uid $jvm_user, native as uid $native_user (shared workspace volume)" >&2
  failed=1
fi

if ! grep -Eq '^ENV HOME=/home/default$' "$native"; then
  echo "FAIL: native image does not set HOME=/home/default, so ~/.qlawkus resolves outside the volume" >&2
  failed=1
fi

if ! grep -Eq '/home/default' "$native"; then
  echo "FAIL: native image never creates /home/default, so a fresh volume mounts root-owned" >&2
  failed=1
fi

exit "$failed"
