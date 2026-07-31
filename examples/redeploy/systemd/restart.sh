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

# Full reactor, matching the Docker builds: a partial `-pl app -am` skips the *-deployment
# modules, so the extension-descriptor check cannot resolve them from a clean local repo.
build() {
  "${MVN}" -q -DskipTests -DskipITs install
}

restart() {
  sudo systemctl restart "${UNIT}"
}

# shellcheck source=../redeploy.sh
source "$(dirname "${BASH_SOURCE[0]}")/../redeploy.sh"
redeploy_run
