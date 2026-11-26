# Containerized run

Running the stack with `run.sh` and Docker Compose.

Behavior and configuration are documented for users in `site/content/quickstart.adoc` and `site/content/operations.adoc`, which is the source of truth. This page keeps the implementation rationale and the traps worth knowing before changing the code.

`./run.sh <local|prod|native> [up|build|down|logs|restart|ps|status] [--replace|--recreate|--no-wait]` wraps Docker Compose (`./run.sh --help` for the full list).
Unlike dev mode, this needs only Docker: the `app/src/main/docker/Dockerfile.{jvm,native}-build` images are **multi-stage** and compile the whole reactor in-container (build stage = the `ubi10/openjdk-25` builder, which ships Maven; runtime = `ubi10/openjdk-25-runtime`), so there is no host `mvn install` step and no stale-jar trap.
Each build Dockerfile has its own `Dockerfile.*-build.dockerignore` so the standard Quarkus `.dockerignore` (for the COPY-target `Dockerfile.jvm`/`.native-micro`) stays intact.
The build runs the **full** reactor (`mvn install`), not `-pl app -am`: a fresh local repo needs every `*-deployment` module built so the `extension-descriptor` check on each extension resolves.
`local` -> `docker-compose.local.yml`; `prod`/`native` -> `docker-compose.yml` (native via its `native` profile).

**run.sh safety checks.** `up` runs two checks before it builds anything, because a native build is 70 minutes and the failure used to come at the end (`Bind for 0.0.0.0:8742 failed: port is already allocated`).
Host ports 8742, 5432 and 3000 must be free, or held by the service of this same compose project that the mode would reuse; the jvm and native app services share 8742, so the other mode's container is reported with the exact `docker stop`, and `--replace` stops only that one (never a container of another compose project).
The second check refuses to start when the old PostgreSQL 17 volume `qlawkus_pgdata` holds data and `qlawkus_postgres` does not exist, since booting would create an empty database and the agent would lose its memory: `./run.sh migrate-postgres` (`scripts/migrate-postgres.sh`, dump and restore with row counts compared, old volume only read and kept as the rollback) moves it, and `docker volume create qlawkus_postgres` is the explicit opt-out.
After `up` the script waits for `/q/health/ready` (`QLAWKUS_READY_TIMEOUT`, default 300 s), prints service, state and URL, and exits non-zero with the last 40 app log lines on failure.
It also reads `/etc/resolv.conf` of every container (through `docker cp`, the native image has no shell) and flags one on a user-defined network whose resolver is not 127.0.0.11, the leftover of a failed `up` that cannot resolve `postgres`; `--recreate` (alias `restart`) force-recreates only the app service.
`scripts/test-run-sh.sh` checks all of this against a fake `docker` on the PATH, starting nothing.

**PostgreSQL 19 layout.** The `postgres` service is `pgvector/pgvector:0.8.7-pg19` with volume `qlawkus_postgres` mounted at `/var/lib/postgresql` (18+ keeps the cluster in `/var/lib/postgresql/<major>/docker`, so a later major can be upgraded in place with `pg_upgrade`).
A volume written by 17 or older cannot be read by 19.
Rollback is the old volume plus the previous compose files.

**Image pins.** Every image in the compose files and the build Dockerfiles is an exact tag (searxng also keeps its digest), so a Dependabot bump is a reviewed change; `.github/dependabot.yml` covers the compose files, `docker/lgtm`, `docker/searxng` and `app/src/main/docker`.
Moving `grafana/otel-lgtm` means re-diffing `docker/lgtm/prometheus.yaml` against the image's own (`docker run --rm --entrypoint cat grafana/otel-lgtm:<tag> /otel-lgtm/prometheus.yaml`); at 0.41.1 the image's file matched `docker/lgtm/prometheus.yaml` above `scrape_configs`, so only the tag moved.

**Image composition.** Both build Dockerfiles take `QLAWKUS_COMPOSITION`: `full` (default, so `./run.sh prod` and the Compose files keep building every capability) or `image`, which copies `app/src/main/docker/agent-image.yml` over `agent.yml` in the build stage and runs `qlawkus:generate -pl app -am`. That manifest is the same as the reference one (`ImageManifestTest` pins them together), so the image carries every capability: `cognition-pgvector`, `google-workspace` and `brag` own databases that stay off until a URL is given, so the same bytes run on files and on Postgres (see `agents/cognition-memory.md`). `integration-tests/image-no-database` boots that composition with no URL.
The generator has to run in a Maven invocation of its own, after the plugin is installed and before the reactor install: it rewrites `app/pom.xml` on disk, but the build that runs it has already read the pom, so dropped dependencies would still be on the classpath.
The manifest lives beside the Dockerfiles, not under `src/main/resources`, so it is never baked into a classpath and the reference `agent.yml` stays the one the pom matches; `ImageManifestTest` keeps it equal to `agent.yml` minus those two.
`publish-images.yml` publishes only two images, both this composition: the JVM image (no suffix) and `-native`. The full composition and the devtools variant stay build-from-source (`./run.sh prod`, `QLAWKUS_IMAGE_VARIANT=devtools`), so a release builds no more than two images. Each one is booted before it is pushed, with no database and no `.env`, to check it reaches setup mode (the token in the log, the first-run page served), takes a password with that token, finds the keystore it wrote under `/home/default/.qlawkus` (on the volume) and survives an agent turn up to the model call. Both Dockerfiles set `QLAWKUS_WORKSPACE_ROOT=/home/default/workspace`: the property defaults to the working directory, which is not writable by uid 185, and without it every turn fails on the shell tool (found by running the image with no environment at all).
A new capability that owns a datasource must be added to both the manifest and `ImageManifestTest`'s list.

**Image variants.** `Dockerfile.jvm-build` takes the build arg `QLAWKUS_IMAGE_VARIANT`, which picks its final stage: `base` (default) or `devtools`, the same runtime plus `git-core` (UBI repos) and `gh` (GitHub's official RPM repo, which serves only its latest release, so `gh` is not pinned: a pin would break the build on the next release).
Both compose files pass it through, so `QLAWKUS_IMAGE_VARIANT=devtools ./run.sh prod` builds it.
It is a distribution choice, kept out of the base so the base stays lean (measured with `docker image ls`: base 791 MB on disk / 242 MB content, devtools 966 MB / 286 MB, so +175 MB / +44 MB).
The `devtools` variant is defined for the JVM Dockerfile, and `./run.sh native` refuses the variant.
The native build stage uses the Mandrel builder that Quarkus itself names as its default (`ubi10-quarkus-mandrel-builder-image:jdk-25` for 4.0.1, pinned here to an exact `jdk-25.x`): the generated `io.quarkus.runner.Feature` calls GraalVM `Feature` API methods, and a builder from another JDK line fails the image step with `NoSuchMethodError ... registerBuildTimeBootstrapIndy` (Dependabot had moved it to `jdk-26.0.0_0`, which also broke the nightly native build). Dependabot ignores major moves of that image; follow the builder Quarkus documents when bumping Quarkus.
The native Dockerfiles use UBI minimal with `freetype` and `fontconfig` for headless PDF processing and package the shared libraries emitted beside the native runner.
The native image runs as uid 185 with `HOME=/home/default`, like the JVM image, because both services mount the same `workspace` volume at `/home/default`: another uid cannot write what the JVM image created, and `~/.qlawkus` would resolve outside the volume. `HOME` alone is not enough for the native image: Java takes `user.home` from the passwd entry, and ubi-minimal has none for uid 185, so it resolved to `?` and the keystore, state and facts landed in `/work/?`, lost with the container. `Dockerfile.native-build` appends the entry, and the smoke boot checks that the keystore lands under `/home/default`.
`scripts/check-image-runtime-user.sh` pins that in the PR build.
The code-review tools (`CodeReviewTool`, `ReviewResponseTool`) are what need it, and they check first: `ShellTool.missingExecutable` looks the executable up on the PATH the command would run with (the workspace env file can override the process PATH) and returns a result naming the binary, with the devtools hint for `git`/`gh`, instead of letting `sh` fail with a bare `not found`.
`scripts/check-devtools-image.sh <image>` verifies an image actually carries both.

Every stack also runs `lgtm` (`grafana/otel-lgtm`: collector, Prometheus, Tempo, Loki, Grafana on :3000), defined once in `docker/lgtm/compose.yml` and pulled into each compose file with `extends`.
The agent services turn the OTel SDK on and export traces and logs to `lgtm:4317`; Prometheus scrapes `/q/metrics` on the agent's **management port 9000**, never published, through the network alias `agent` that the JVM and native services share.
Grafana refuses to start without `GRAFANA_ADMIN_PASSWORD` (the image otherwise makes anonymous visitors Admin).
`docker/lgtm/prometheus.yaml` is the image's own file plus the scrape job, so a Dependabot bump of the tag means re-copying it.
The agent does not depend on `lgtm`: with it down, only telemetry is lost.
