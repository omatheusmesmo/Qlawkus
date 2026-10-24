#!/usr/bin/env bash
set -euo pipefail

# Verifies that a Qlawkus devtools image carries the binaries the code-review
# tools shell out to, running each as the image's own user (185).
#
# Usage: scripts/check-devtools-image.sh <image>
#   e.g. docker build -f app/src/main/docker/Dockerfile.jvm-build \
#          --build-arg QLAWKUS_IMAGE_VARIANT=devtools -t qlawkus:devtools .
#        scripts/check-devtools-image.sh qlawkus:devtools

image="${1:?usage: $0 <image>}"
status=0

for binary in git gh; do
  if version="$(docker run --rm --entrypoint "$binary" "$image" --version 2>&1)"; then
    echo "ok   $binary: $(head -n 1 <<<"$version")"
  else
    echo "FAIL $binary is missing from $image: $version" >&2
    status=1
  fi
done

exit "$status"
