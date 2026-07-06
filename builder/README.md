# Builder runner

On-demand rebuild of the Qlawkus app from its composition manifest. The running app
cannot rebuild itself (a native binary is frozen, a JVM app is closed-world augmented
at build time, and the runtime image ships no toolchain), so a builder with the
toolchain rebuilds on its behalf when `agent.yml` changes.

This is issue #248, the foundation of the M8 build-and-redeploy control loop. It stops
at **producing a versioned artifact**; triggering it over HTTP is #250, and swapping the
running instance to the new artifact is #251.

## What it does

`build.sh <jvm|native>`:

1. Optionally stages a manifest (`STAGING_AGENT_YML`) into the app's `agent.yml`.
2. Reconciles the app pom from the manifest by running the `generate-sources` phase
   (`mvnw -pl app -am generate-sources`): the app pom binds `qlawkus:generate` there,
   the same seam dev mode uses. A plugin-prefix goal would not resolve from the root.
3. Builds in a **fresh** Maven session so it reads the regenerated pom:
   `mvnw -pl app -am -DskipTests -DskipITs [-Pnative] package`.
4. Emits the artifact into `SHARED_DIR` and repoints `latest`.

The two Maven sessions are deliberate: `qlawkus:generate` rewrites `pom.xml` on disk,
but Maven has already resolved the model for the current session, so the build must run
in a new session to pick up the new dependency set. This is the one-shot equivalent of
what `quarkus:dev` does across two reload passes.

It is one-shot: it builds once and exits. Nothing runs until you invoke it.

## Output contract

```
$SHARED_DIR/<UTC-timestamp>/        e.g. 20260706T0210Z/
   quarkus-app/                     jvm mode: the Quarkus fast-jar tree
   application                      native mode: the *-runner binary (executable)
   agent.yml                        the exact manifest that was built (provenance)
   generate.log                     the qlawkus:generate output (capability report)
   build.json                       { mode, timestamp, sourceGitSha, artifact, status,
                                       resolvedCapabilities: { selected, excluded } }
$SHARED_DIR/latest -> <UTC-timestamp>   symlink, flipped only on success
```

The publish is atomic: the build lands in a temp dir and is renamed into place, then
`latest` is repointed, so a failed build never leaves `latest` on a partial build.
`build.json` and `latest` are the seam #250 (reads status) and #251 (reads `latest`)
build on: e.g. `JAVA_APP_JAR=$SHARED_DIR/latest/quarkus-app/quarkus-run.jar` (jvm) or
`exec $SHARED_DIR/latest/application` (native).

## Knobs (env)

| Var | Default | Purpose |
|-----|---------|---------|
| `STAGING_AGENT_YML` | _(unset)_ | Manifest to stage before building; unset builds the app's current `agent.yml` as-is. |
| `SHARED_DIR` | `target/qlawkus-builds` | Output root for versioned builds. |
| `BUILD_RETENTION` | `3` | Number of builds to keep; older ones are pruned on success. |
| `MVN_CMD` | repo `mvnw` | Maven command; overridden by the contract test. |

## Usage

```bash
# Build the current composition (JVM)
builder/build.sh jvm

# Rebuild from a staged manifest (native)
STAGING_AGENT_YML=/path/to/agent.yml builder/build.sh native
```

## Tests

- `test/build-sh-test.sh` - fast contract test. Stubs Maven, so it needs no real build;
  covers the output layout, `build.json`, the `latest` symlink, retention, and the
  atomicity guarantee on a failed build. Run it directly: `bash builder/test/build-sh-test.sh`.
- `test/real-build-smoke.sh` - opt-in end-to-end smoke against the real reactor, proving
  the manifest flows into the built artifact (not just the pom). Heavy (a full build,
  minutes) and needs a warm local repo; see the header of that script.

## Later (not in #248)

Packaging this into published builder images (frozen reactor source + warm `.m2`), so a
consumer can `docker pull` and rebuild without cloning the repo, is a deferred phase.
Until then the runner uses the toolchain of wherever it is invoked, against the reactor
it lives in.
