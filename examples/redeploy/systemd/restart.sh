#!/usr/bin/env bash
#
# Reference implementation: systemd (host toolchain).
#
# build   - regenerate the pom from the promoted agent.yml and package the app on the host
# restart - restart the unit onto the freshly built artifact
#
# Unlike compose/k8s, the build runs on the host that also runs the service, so the toolchain
# (JDK + Maven, or GraalVM for native) must be present. Deploy the built artifact to the unit's
# WorkingDirectory if it differs from the build checkout.
#
# Run from the repository root:
#   QLAWKUS_ADMIN_USER=... QLAWKUS_ADMIN_PASSWORD=... examples/redeploy/systemd/restart.sh
#
set -euo pipefail

UNIT="${QLAWKUS_SYSTEMD_UNIT:-qlawkus}"
MVN="${QLAWKUS_MVN:-./mvnw}"

build() {
  "${MVN}" -q -pl app -am clean package -DskipTests
}

restart() {
  sudo systemctl restart "${UNIT}"
}

# shellcheck source=../redeploy.sh
source "$(dirname "${BASH_SOURCE[0]}")/../redeploy.sh"
redeploy_run
