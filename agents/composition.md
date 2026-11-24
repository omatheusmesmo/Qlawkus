# Composition manifest and generator

The `agent.yml` manifest and the pom generator.

Behavior and configuration are documented for users in `site/content/composition.adoc`, which is the source of truth. This page keeps the implementation rationale and the traps worth knowing before changing the code.

## composition-model/

**`composition-model/`** - Thin, **Quarkus-free** module (plain records + Jackson YAML) holding the `agent.yml` composition manifest model: `CompositionManifest`/`BuildTime`/`Posture`, the single `CompositionManifestParser`, the policy+exceptions `isEnabled` logic, and the path conventions (`CompositionPaths`).
Shared by the (future) pom generator that reads `build-time` before the build and the running app that reads `runtime` each boot, so the schema and policy never drift (same single-source-of-truth discipline as `KeystoreSecrets`).
The `qlawkus.composition.*` location config (`CompositionConfig`, BUILD_TIME) lives in `client`.
At runtime `client` also exposes an authenticated composition control plane (`rest.CompositionAdminResource` + `compose.CompositionAdminService`): `POST /api/admin/composition/manifest` validates a new `agent.yml` (via the shared `CompositionManifestParser`) and stages it under `qlawkus.agent.state.root`; `GET /api/admin/composition` returns the active (baked, read from the classpath `agent.yml`) + staged pair; `DELETE .../manifest` discards it.
It only records intent (validate + write), never builds - the seam to an external builder is HTTP, not a shared volume, so even a leaked admin credential can at most stage a validated manifest.
The same admin surface generalizes to arbitrary config values via `config.metadata.ConfigMetadataIndex` (reads the `quarkus-config-doc` metadata bundled in every extension jar - the same source that generates `config-reference.adoc` - so the property list is never hand-maintained): `RUN_TIME` properties go through `config.RuntimeToggleWriter` + `PUT`/`DELETE /api/admin/runtime-toggles` (writes straight into the `runtime:` override file, applies on restart); `BUILD_TIME`/`BUILD_AND_RUN_TIME_FIXED` properties go through `compose.ConfigOverridesAdminService` + `PUT`/`POST`/`DELETE /api/admin/config-overrides`, staging a `.properties` document that is fetched and promoted into `config-overrides.properties` alongside `agent.yml` by the same redeploy loop - parallel files, never merged, so the pom generator never has to reconcile `application.properties` lines.
The same surface also records what the external redeploy loop reports: `POST /api/admin/composition/redeploys` (body `dto.RedeployReport`, validated at the API layer: start instant, `success`|`failure`, `failedPhase` required on a failure, per-phase milliseconds for the closed set `fetch|promote|build|restart|verify`, durations capped at a day, an optional message capped at 200 chars with no control characters) stores it through `compose.RedeployHistoryService`, and `GET .../redeploys` reads it back.
It is data only: no field is ever interpreted as an instruction, so a leaked admin credential can at most write a bounded history entry.
`redeploy.sh` times each phase and reports once when the run ends (an EXIT trap names the failing phase), never altering its own exit status if no instance answers.
The history (last 20 runs, plus running totals and the last success, which a failure streak cannot evict) is written atomically to `redeploys.json` under `qlawkus.agent.state.root`; a corrupt file is treated as empty rather than stopping boot.
The console schedule page shows the last run read-only.
`secrets.SecretPropertyCatalog` excludes known-secret property names from the index entirely, so a secret is never rendered or writable through either endpoint.

## composition-maven-plugin/

**`composition-maven-plugin/`** - Maven plugin (`qlawkus-maven-plugin`, goal prefix `qlawkus`) that turns the manifest into the pom **before** the build (`mvn qlawkus:generate`).
Quarkus resolves dependencies before augmentation and Maven does no tree-shaking, so a manifest can only decide the dependencies from a generator that runs ahead of the build, not a `@BuildStep`.
`GenerateMojo` reads `agent.yml` (via `composition-model`), resolves the selected capabilities against a `CapabilityCatalog`, and uses the Maven Model API to decide which top-level dependency entries to add or remove and the XML parser's source offsets to edit them in place.
Comments, formatting, and other sections survive composition.
Idempotent.
Added deps carry no version (BOM-managed, like `quarkus extension add`).
**Quarkus-free** at runtime; needs an ASM>=9.8 override for the plugin descriptor on Java 25.
The catalog (#74) is decentralized: each extension module self-describes its capability name in its own `quarkus-extension.yaml` under `metadata.qlawkus.capability`, and the default `ReactorCatalog` reads that key from every reactor module (`${reactorProjects}`), reading the source descriptor template so it works before any module is built.
`CapabilityCatalog` stays an interface so a published-artifact strategy (external consumer, outside the monorepo) can be added without touching the generator.
Capability names ship namespaced where natural (`messaging.discord`, `cognition.pgvector`) and several modules may share one (`google-workspace` = the 6 google modules); `qlawkus-client` is the always-present skeleton and declares none.
Each run logs the **resolved capability set** (selected/excluded) so the policy-and-exceptions shorthand never hides the full state, and an `except` name no module declares **fails the build** (opt-out `-Dqlawkus.composition.fail-on-unknown-capability=false`) - the anti-typo guard, since an unannounced name otherwise silently resolves to nothing.
When `qlawkus.composition.fail-on-pom-drift` is true (the `env.CI` profile on the reactor root), a pom that disagrees with the manifest fails instead of being rewritten, listing dependencies it would add or remove (#463); `ReferenceAppCompositionTest` pins the reference `app` pair in unit tests.
The goal's default phase is `generate-sources`, wired as an `<execution>` in the app pom, so every Maven build reconciles the pom from `agent.yml` before Quarkus resolves dependencies.
**It does not re-run inside a live `quarkus:dev` session**: dev mode notices the changed `agent.yml` and logs "Files changed but restart not needed" without re-entering `generate-sources`, so a manifest edit applies on the *next* build - restart dev mode, or run `mvn qlawkus:generate`.
Capability resolution reads `${reactorProjects}`, so the generator needs the reactor visible (`mvn -pl app -am quarkus:dev` from the root, not a lone `cd app && mvn quarkus:dev`).
End-to-end proof is the `maven-invoker` IT under `src/it/` (a real two-capability reactor + agent app), gated by the `composition-invoker-it` profile (off under `-Pnative`, honors `skipITs`); its post-build hooks are **BeanShell** (`.bsh`), not Groovy, because Groovy's shaded ASM cannot read Java 25 class files.
