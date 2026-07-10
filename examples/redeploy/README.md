# Redeploy contract - reference implementations

The running agent cannot rebuild or restart itself (a native binary is frozen, a JVM app
is closed-world augmented at build time, and the runtime image ships no toolchain).
Swapping a capability set in or out is therefore a **rebuild-and-redeploy** driven from
outside the app. These files are the reference implementations of that flow; the app only
stages a validated manifest and exposes it (see the composition admin endpoint), never
building or restarting anything itself.

## The 5-phase contract

1. **fetch** - read the staged manifest over the authenticated composition API (`GET`).
2. **promote** - write it into the source tree the builder reads (`app/src/main/resources/qlawkus/agent.yml`).
3. **build** - regenerate the pom from the manifest and build the artifact.
4. **restart** - swap the running instance onto the new artifact.
5. **verify** - wait for health, then discard the consumed staged manifest (`DELETE`).

`redeploy.sh` is the environment-independent driver (phases 1, 2, 5). Each implementation
supplies phases 3 and 4 by defining `build` and `restart`, then sourcing it.

## Prerequisites

- `curl` and `jq` on the machine running the redeploy (it talks to the composition API).
- The **builder** side needs the toolchain (JDK + Maven, or GraalVM/Mandrel for native).
  With compose/k8s the toolchain lives in the multi-stage Docker build; with systemd it is
  the host itself.
- Admin credentials for the composition API, as `QLAWKUS_ADMIN_USER` / `QLAWKUS_ADMIN_PASSWORD`.

## Running (from the repository root)

```bash
export QLAWKUS_ADMIN_USER=... QLAWKUS_ADMIN_PASSWORD=...

# docker-compose: build + recreate the app container in one step
examples/redeploy/compose/redeploy.sh

# kubernetes: build+push an image, then roll the Deployment onto it
IMAGE=ghcr.io/you/qlawkus:$(git rev-parse --short HEAD) \
  QLAWKUS_HEALTH_URL=https://qlawkus.example/q/health/ready \
  examples/redeploy/k8s/rollout.sh

# systemd: package on the host, then restart the unit
examples/redeploy/systemd/restart.sh
```

Nothing here depends on `run.sh`; the compose recipe mirrors its restart step standalone.

## Configuration (environment variables)

| Variable | Default | Meaning |
|----------|---------|---------|
| `QLAWKUS_BASE_URL` | `http://localhost:8742` | Base URL of the running agent |
| `QLAWKUS_ADMIN_USER` / `QLAWKUS_ADMIN_PASSWORD` | (required) | Composition API credentials |
| `QLAWKUS_SOURCE_MANIFEST` | `app/src/main/resources/qlawkus/agent.yml` | Where promotion writes the manifest |
| `QLAWKUS_HEALTH_URL` | `${BASE_URL}/q/health/ready` | Readiness endpoint polled in phase 5 |
| `QLAWKUS_HEALTH_TIMEOUT` | `180` | Seconds to wait for health |
| `QLAWKUS_DRY_RUN` | `0` | `1` stops after promotion (no build/restart/discard) |

Per-implementation knobs are documented in each script's header (`QLAWKUS_COMPOSE_SERVICE`,
`IMAGE` / `QLAWKUS_K8S_DEPLOYMENT`, `QLAWKUS_SYSTEMD_UNIT`).

## When there is no staged manifest

If `GET` shows no staged manifest, the redeploy rebuilds the currently committed one (a
plain no-op promotion). This is safe to run in CI on every deploy.
