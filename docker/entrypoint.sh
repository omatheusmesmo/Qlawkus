#!/bin/bash
set -euo pipefail

REQUIRED_VARS=(
  QUARKUS_DATASOURCE_JDBC_URL
  QUARKUS_DATASOURCE_USERNAME
  QUARKUS_DATASOURCE_PASSWORD
)

for var in "${REQUIRED_VARS[@]}"; do
  if [ -z "${!var:-}" ]; then
    echo "ERROR: Required environment variable $var is not set"
    exit 1
  fi
done

echo "Starting Qlawkus with profile: ${QUARKUS_PROFILE:-prod}"
exec /opt/jboss/container/java/run/run-java.sh
