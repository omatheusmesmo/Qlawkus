#!/usr/bin/env bash
#
# Reference implementation: Kubernetes.
#
# build   - build and push a new image from the promoted agent.yml
# restart - point the Deployment at that image and wait for the rollout
#
# The Deployment's readiness probe gates the rollout, so `kubectl rollout status` is the
# authoritative health gate here; set QLAWKUS_HEALTH_URL to an in-cluster or ingress URL
# only if you also want the generic health poll.
#
# Run from the repository root:
#   IMAGE=ghcr.io/you/qlawkus:$(git rev-parse --short HEAD) \
#   QLAWKUS_ADMIN_USER=... QLAWKUS_ADMIN_PASSWORD=... QLAWKUS_HEALTH_URL=https://qlawkus.example/q/health/ready \
#   examples/redeploy/k8s/rollout.sh
#
set -euo pipefail

IMAGE="${IMAGE:?set IMAGE to the new tag to build and roll out}"
DEPLOYMENT="${QLAWKUS_K8S_DEPLOYMENT:-qlawkus}"
DOCKERFILE="${QLAWKUS_DOCKERFILE:-app/src/main/docker/Dockerfile.jvm-build}"

build() {
  docker build -f "${DOCKERFILE}" -t "${IMAGE}" .
  docker push "${IMAGE}"
}

restart() {
  kubectl set image "deployment/${DEPLOYMENT}" "${DEPLOYMENT}=${IMAGE}"
  kubectl rollout status "deployment/${DEPLOYMENT}"
}

# shellcheck source=../redeploy.sh
source "$(dirname "${BASH_SOURCE[0]}")/../redeploy.sh"
redeploy_run
