# Qlawkus - Agent Instructions

## Dev Mode Start

```bash
mvn install -pl client/deployment -am -DskipTests # builds both runtime and deployment first
mvn -pl app -am quarkus:dev              # from the root: app/ is the only runnable module, and
                                         # capability resolution needs the reactor (see agents/composition.md)
```

Changing `client/` requires re-running `mvn install -pl client/deployment -am` then restarting dev mode. Selecting only `-pl client` installs the aggregator POM, not the extension jars. Hot reload does NOT apply to extension code.

## Worktree and Branch Cleanup

After merging a PR, remove its clean feature worktree and both local and remote feature branches. Verify the merged PR and its head before deleting: rebase merges rewrite commit IDs, so use the merged PR head or patch equivalence when ancestry alone does not establish the merge. A branch already deleted remotely needs no further action.

Preserve active worktrees, open PR branches, uncommitted changes, and ignored files containing configuration or user data. Only clear a worktree lock after confirming its owning process has ended. Generated build outputs may be removed with an otherwise clean worktree.

## Detail by area

Read the page before changing the area. Each one holds implementation rationale and traps; the user-facing page in `site/content/` is the source of truth for behavior and configuration, so do not copy it here.

| Area | Page | User-facing doc under `site/content/` |
| --- | --- | --- |
| Composition manifest and the pom generator | [agents/composition.md](agents/composition.md) | composition.adoc |
| Console, voice session, skill install panel | [agents/console.md](agents/console.md) | console.adoc, voice.adoc, skills.adoc |
| Scheduled tasks, heartbeat | [agents/scheduler.md](agents/scheduler.md) | scheduler.adoc |
| MCP client | [agents/mcp-client.md](agents/mcp-client.md) | mcp.adoc |
| Web fetch | [agents/web-fetch.md](agents/web-fetch.md) | web-fetch.adoc |
| Owner approvals for gated tool calls | [agents/approvals.md](agents/approvals.md) | approvals.adoc |
| Messaging adapters, attachments, voice clients | [agents/messaging.md](agents/messaging.md) | messaging.adoc |
| Persona, owner, facts, working memory, episodic journals | [agents/cognition-memory.md](agents/cognition-memory.md) | memory.adoc, storage.adoc |
| Skills and the remote Skill Hub | [agents/skills.md](agents/skills.md) | skills.adoc |
| Secret store, keystore, the agent's shell environment | [agents/secrets.md](agents/secrets.md) | secrets.adoc |
| Model tiers, providers, embedding dimension | [agents/llm-configuration.md](agents/llm-configuration.md) | quickstart.adoc, config-reference.adoc |
| Fault tolerance, workloads, shutdown, instance lock | [agents/resilience.md](agents/resilience.md) | operations.adoc, architecture.adoc |
| Meters, traces, logs, self-observe | [agents/telemetry.md](agents/telemetry.md) | operations.adoc |
| `run.sh`, Docker Compose, image variants | [agents/containerized-run.md](agents/containerized-run.md) | quickstart.adoc, operations.adoc |

## Architecture

Multi-module Maven monorepo with a Quarkus extension pattern (`client/deployment` + `client/runtime`). Each entry below is the headline of a module; the page named in **Detail by area** holds the rest, and `site/content/` is the source of truth for what a feature does.

- **`composition-model/`** - Thin, **Quarkus-free** module (plain records + Jackson YAML) holding the `agent.yml` composition manifest model: `CompositionManifest`/`BuildTime`/`Posture`, the single `CompositionManifestParser`, the policy+exceptions `isEnabled` logic, and the path conventions (`CompositionPaths`). Details: [agents/composition.md](agents/composition.md).
- **`composition-maven-plugin/`** - Maven plugin (`qlawkus-maven-plugin`, goal prefix `qlawkus`) that turns the manifest into the pom **before** the build (`mvn qlawkus:generate`). Details: [agents/composition.md](agents/composition.md).
- **`client/`** - Quarkus extension (core agent, cognition, REST, tools). Not a runnable app. **Database-free**: depends on no datasource/JDBC/Flyway/pgvector - the markdown stores are its `@DefaultBean` backend.
- **`attachments/`** - Optional Quarkus extension (`deployment/` + `runtime/`, capability `attachments`). Details: [agents/messaging.md](agents/messaging.md).
- **`cognition-decision/`** - Optional capability `decision.nvidia`. When enabled at runtime it supplies a `TurnGate` using an NVIDIA NIM adapter for LangChain4j's yes/no `DecisionModel`. The gate sends user and assistant text, applies per-question thresholds, and reuses the core fail-open policy. It also supplies the `DuplicateJudge` that confirms a nominated pair in the memory review, which fails safe (keeps both facts). It has its own timeout, bulkhead and circuit breaker. See `site/content/operations.adoc`.
- **`cognition-pgvector/`** - Optional Quarkus extension (`deployment/` + `runtime/`) holding the entire Postgres/pgvector backend for the cognition stores (the `store.pg` package, the Flyway migrations, and the datasource/pgvector config as `META-INF/microprofile-config.properties`). Present in `app/`; absent in a markdown-only distribution. `requires` the `dev.omatheusmesmo.qlawkus.agent` capability.
- **`console/`** - Optional Quarkus extension (`deployment/` + `runtime/`, capability `console`) serving a server-rendered admin UI at `/console` (Qute + HTMX, no Node, `@Authenticated` reusing the app's Basic+Argon2id mechanism). Details: [agents/console.md](agents/console.md).
- **Owner approvals (`client/runtime/.../approval`)** - Opt-in gate for the `@QlawTool` tools named in `qlawkus.approval.tools` (empty by default, so nothing changes until one is named). Details: [agents/approvals.md](agents/approvals.md).
- **Scheduled tasks** (`client/runtime/.../task`) - `TaskSchedulerTool` (`@QlawTool`: `scheduleTask`, `listScheduledTasks`, `cancelScheduledTask`) lets the agent schedule owner-requested reminders. Details: [agents/scheduler.md](agents/scheduler.md).
- **`console-voice/`** - Optional Quarkus extension (`deployment/` + `runtime/`, capability `console.voice`) adding a live voice session to the console at `/console/voice`. Details: [agents/console.md](agents/console.md).
- **`console-skill-hub/`** - Optional Quarkus extension (`deployment/` + `runtime/`, capability `console.skill-hub`) adding the skill install panel at `/console/skills/install`. Details: [agents/console.md](agents/console.md).
- **`app/`** - Deployable Quarkus app (`packaging=quarkus`) and the **reference/example distribution**: one worked example of how to assemble Qlawkus, so it holds **only wiring and configuration** (pom + `application.properties` + `src/main/docker`) and **never implementation code** (no `src/main/java`). Anything an integrator would want to reuse belongs in an extension (`client` or a `*/` module), so other distributions can pick it up; `app/` just composes and configures it. Wires all extensions together; the only module you run.
- **`tools/`** - Optional tool extensions (Google Workspace 6 modules + Brag + Skill Hub + Self Observe). Each is a standalone Quarkus extension with `deployment/` + `runtime/`. `qlawkus-tools-skill-hub` is the remote skill-registry client (search/install from skills.sh); it `requires` the `dev.omatheusmesmo.qlawkus.agent` capability and is the outward-facing counterpart to the in-`client` local skill subsystem. `qlawkus-tools-self-observe` (capability `self-observe`) lets the agent query its own telemetry; see [agents/telemetry.md](agents/telemetry.md).
- **`tools/qlawkus-tools-mcp-client`** - Optional MCP client extension (`deployment/` + `runtime/`, capability `mcp-client`; in `app/` through both the pom and the `agent.yml` `except` list, since the generator drops a pom dependency whose capability is not listed). Details: [agents/mcp-client.md](agents/mcp-client.md).
- **`tools/qlawkus-tools-web-fetch`** - Optional page-reading extension (`deployment/` + `runtime/`, capability `web-fetch`, config root `qlawkus.web-fetch.*`, `enabled` default `false`; `app/` turns it on). Details: [agents/web-fetch.md](agents/web-fetch.md).
- **`messaging/`** - Messaging extension (core + Discord/Telegram/Slack/WhatsApp adapters). Details: [agents/messaging.md](agents/messaging.md).
- **`integration-tests/`** - Per-feature integration test modules (smoke, cognition, markdown-only, google, terminal, brag, code-review, messaging). Each is a standalone Quarkus app (`packaging=quarkus`). `markdown-only` is the proof of the database-free distribution (boots with no datasource); `cognition` depends on `cognition-pgvector` to exercise the Postgres path.
- **`testing-internal/`** - Shared test utilities (depends on `qlawkus-client`).
- **`heartbeat/`** - Optional extension (runtime and deployment, capability `heartbeat`) that runs an owner-configured, bounded health check. Details: [agents/scheduler.md](agents/scheduler.md).

`app/` depends on `client`, `cognition-pgvector`, all `tools/*`, and `messaging` (core + discord + telegram). Slack and WhatsApp adapters are built but not in `app/` pom.

## Testing

```bash
# Single test class (-am builds upstream modules; the failIfNoSpecifiedTests flag
# stops sibling modules in the reactor from erroring when the class isn't theirs)
mvn test -pl client/deployment -am -Dtest=UserProfileTest -Dsurefire.failIfNoSpecifiedTests=false

# Unit tests for a single module (uses WireMock, no LLM needed)
mvn test -pl client/deployment

# Integration tests (LLM-dependent tests auto-skip if no API key)
mvn test -pl integration-tests/smoke

# The live provider contract test (requires a real LLM_API_KEY)
LLM_API_KEY=your-key mvn test -pl client/deployment,integration-tests/live-provider -am -Dtest=LiveProviderContractTest -Dsurefire.failIfNoSpecifiedTests=false

# Full build (skip ITs by default - skipITs=true in parent pom)
mvn verify

# Native ITs (enable with -Pnative)
mvn verify -Pnative

# Composition maven-invoker IT (real reactor + qlawkus:generate). Needs qlawkus-composition-model
# in the local repo, so install first; off by default (skipITs) and under -Pnative.
mvn -pl composition-model install -DskipTests
mvn -pl composition-maven-plugin install -DskipTests -DskipITs=false
```

- `client/deployment/src/test/` - Extension build-time tests with WireMock-mocked LLM
- `integration-tests/*/src/test/` - QuarkusTest integration tests; some call real LLMs, some use `@InjectMock`
- LLM-dependent tests use `@EnabledIf("dev.omatheusmesmo.qlawkus.testing.QlawkusTestUtils#usesLLM")` - auto-skip when `LLM_API_KEY` is absent or "dummy", or when the module forces `qlawkus.test.mock-mode`
- Smoke tests (`integration-tests/smoke/`) hit real LLM: can take 5+ minutes per test
- Every `integration-tests/*` module that declares a `@QuarkusIntegrationTest` class must bind `maven-failsafe-plugin` (`integration-test` + `verify`, as `brag` does), or its `*IT` classes compile and never run, JVM or native. Their runtime config (file roots, `qlawkus.startup-thought.enabled`) is unprefixed rather than `%test`, because the packaged artifact boots in the prod profile.
- `integration-tests/live-provider/` is the only module that calls the real provider: one embedding call (asserting the configured dimension), one minimal call to the primary chat model and one to the `secondary` tier (called directly, not through the fallback chain; skipped unless `qlawkus.model.secondary-enabled` is true for the resolved kind), with no WireMock and no `mock-mode`, so a model the provider retires fails it where every WireMock-backed test stays green (#359). It reads the provider from the `LLM_*` environment and skips unless `LLM_API_KEY` is a real key (`LLM_KIND` and the model overrides when not on the default provider). The push and nightly builds run it in a step of its own that exports the `NVIDIA_API_KEY` repository secret as `LLM_API_KEY` (with `LLM_KIND: nvidia`), after the main build, which keeps `LLM_API_KEY: dummy`: the `LLM_*` variables are read by every module's provider resolution (`LlmKindConfigSourceFactory`), so exporting them for the whole job re-points the WireMock-backed modules at the real provider and breaks them. While that secret holds `dummy` the test skips. JVM-only: it keeps jar packaging under `-Pnative`.

## Adding Tools

Create a CDI bean annotated with `@QlawTool` + `@ApplicationScoped`. Methods annotated with `@Tool` are auto-discovered at build time by `ClientProcessor` (which also registers DTO records in `dev.omatheusmesmo.qlawkus.dto` for reflection). No manual registration needed.

New tool modules must also be added as dependencies in `app/pom.xml`.

A tool that makes outbound HTTP calls follows the retry contract in [agents/resilience.md](agents/resilience.md): throw `TransientHttpException` for transient failures, and annotate the single HTTP call rather than the tool method.

A tool sees the caller's context on both paths. On `chatSync` it runs on the caller's thread, so the `AgentDeliveryContext` ThreadLocal and the request context are already there. On the streaming path (`AgentStream`) it runs on the model client's callback thread, so `StreamedToolContext` captures the caller's delivery values and request context when the turn starts (keyed by conversation) and restores them around each call: `ContextBridgingToolExecutor` for `@QlawTool` tools (inside `QlawToolProvider`'s metering) and `StreamedToolWrapper` for the tools declared on `@RegisterAiService(tools = ...)`, the seam quarkus-langchain4j exposes for them. With no caller request context a fresh one lives for that call only. A new tool that reads request-scoped state or the delivery context needs nothing more; a new tool seam must go through this bridge, not read the ThreadLocal directly.

## Extension Development

Every module here (`client`, `tools/*`, `messaging/*`) is a full Quarkus extension: a `runtime/` + `deployment/` pair. Rules that keep that pattern correct:

- **Dependency direction is one-way.** `deployment/` depends on `runtime/` (plus other extensions' `*-deployment` artifacts); `runtime/` must NEVER depend on any `deployment` artifact. The Maven plugin validates this and fails the build if a required deployment dependency is missing.
- **Three bootstrap phases.** Build-time *augmentation* runs `@BuildStep` processors (e.g. `ClientProcessor`) - it reads the Jandex index, never loads app classes, and records bytecode. `@Record(STATIC_INIT)` bytecode runs in a static initializer (framework boot, bean wiring; do NOT open ports, start threads, or read runtime config here). `@Record(RUNTIME_INIT)` bytecode runs in `main` (ports, runtime config, service start). Push work to STATIC_INIT when possible.
- **Build steps talk via build items, not fields.** A build-step class is instantiated per invocation and discarded; share state only through immutable `*BuildItem`s. Always produce a `FeatureBuildItem`. A step runs only if something consumes what it produces.
- **Recorders bridge build to runtime.** `@Recorder` methods in `runtime/` are *recorded* (not executed) at build time; their calls replay at runtime. Parameters must be bytecode-serializable (primitives, strings, config objects, `RuntimeValue<T>`). Wrap complex objects in `RuntimeValue<T>`.
- **Config is `@ConfigMapping` interfaces.** `@ConfigRoot(phase = RUN_TIME)` + `@ConfigMapping(prefix = "qlawkus...")`, JavaDoc on each accessor (it becomes the generated config-reference description), `@WithDefault` for defaults, `Optional<T>` for optional, `Map<String, X>` for keyed/dynamic config (see `MessagingConfig`). Choose phase by need: `BUILD_TIME` (build only), `BUILD_AND_RUN_TIME_FIXED` (read at build, immutable at runtime), `RUN_TIME` (re-read each run, overridable).
- **CDI registration.** Register extension beans with `AdditionalBeanBuildItem`; use `@DefaultBean` for anything app code should be able to override; `SyntheticBeanBuildItem` for beans built through a recorder.
- **Native image.** Reflection needs registration - `ClientProcessor` already registers `dev.omatheusmesmo.qlawkus.dto` records via `ReflectiveClassBuildItem`; new reflective types (DTOs, models) must be added the same way (or `@RegisterForReflection`). Use `NativeImageResourceBuildItem` to bundle resources. Verify with `mvn verify -Pnative`.
- **A new optional extension must declare its composition capability.** Add `metadata.qlawkus.capability` to the module's `src/main/resources/META-INF/quarkus-extension.yaml` (create the source template if the module only relied on the generated one). That single key is what the `qlawkus-maven-plugin` `ReactorCatalog` maps to the module's coordinates, so the `agent.yml` manifest can compose the extension in or out. A module with no key is treated as always-present skeleton and is never toggled - so forgetting it means the capability silently cannot be selected. Name capabilities namespaced where natural (`messaging.discord`, `cognition.pgvector`); several modules may share one name to move as a unit (`google-workspace`). Keeping it in `quarkus-extension.yaml` (not the pom) is deliberate: that metadata rides into a published Quarkus platform catalog, so the same key serves both the in-repo `ReactorCatalog` and a future published-artifact catalog for consumers building from `agent.yml` without cloning.
- **Before inventing a build item, check the catalog**: [all build items](https://quarkus.io/guides/all-builditems).

### Reference links

Official Quarkus documentation:

- [Writing Your Own Extension](https://quarkus.io/guides/writing-extensions) - canonical guide
- [All build items reference](https://quarkus.io/guides/all-builditems) - the extension SPI
- [Adding extensions to the Quarkus ecosystem / Quarkiverse](https://github.com/quarkusio/quarkus/wiki/Adding-extensions-to-the-Quarkus-ecosystem)

Community tutorials and walkthroughs:

- [Quarkus: Greener, Better, Faster, Stronger](https://jtama.github.io/posts/quarkus-greener-better-faster-stronger/) (Jérôme Tama) - Dev Services, annotation transform, recorders ([source](https://github.com/jtama/quarkus-extension-demo))
- [How NOT to Create a Quarkus Extension](https://www.loicmathieu.fr/wordpress/informatique/quarkus-tip-comment-ne-pas-creer-une-extension-quarkus/) (Loïc Mathieu) - when a simple JAR + Jandex is enough instead
- [Developing a Quarkus Extension](https://matheuscruz.dev/2024/01/12/developing-a-quarkus-extension/) (Matheus Cruz) - recorders, Gizmo, Jandex scanning ([source](https://github.com/mcruzdev/quarkus-useful))
- [Creating a Quarkus Extension](https://blog.sebastian-daschner.com/entries/creating-a-quarkus-extension) (Sebastian Daschner) - video walkthrough ([source](https://github.com/sdaschner/blink-extension))
- [How to Implement a Quarkus Extension](https://www.baeldung.com/quarkus-extension-java) (Baeldung) - Liquibase extension tutorial

Resource collections:

- [Quarkus Extensions Resources](https://hollycummins.com/quarkus-extensions-resources/) (Holly Cummins) - curated guides, talks, and posts

## Databases

Up to 3 PostgreSQL databases, all managed by Flyway:
- **`qlawkus`** (default) - Main: persona, owner profile, chat history, episodic journal, pgvector embeddings, agent-created scheduled tasks. **Only present when the `cognition-pgvector` extension is on the classpath** (it owns the default datasource, the 8 `V1..V8` migrations, and the pgvector config, contributed via its `META-INF/microprofile-config.properties`). A markdown-only build has no default datasource and never connects to Postgres.
- **`qlawkus_google_auth`** - OAuth credentials + state (Google tools extension)
- **`qlawkus_brag`** - Career brag documents (Brag tool extension; its own named `brag` datasource, independent of cognition)

In Docker, `docker/postgres-init/01-create-databases.sql` creates the extra DBs. In dev mode, Flyway `clean-at-start` resets schema on every restart.

## Documentation

One source, one generated mirror. **Edit only `site/content/`** - everything else is built from it.

**Every PR documents its own change, in the same commit.** A feature PR ships the user-facing page or section under `site/content/` (mirrored to `docs/modules/ROOT/pages/`, see below), a `config-reference.adoc` include for each new `qlawkus.*` config root, and, when it adds a subsystem or changes how one is wired, the matching entry in this file's Architecture list or in the page under `agents/` that it points to. A fix updates the page that already describes the behavior it changes. `site/content/` is the source of truth for what a feature does and how it is configured, so `AGENTS.md` and `agents/` never repeat it: they carry only what a person changing the code needs (invariants, the reason behind a choice, traps) and link to the page for the rest. Keep `AGENTS.md` itself short; a new subsystem gets a page under `agents/` and one line in the Architecture list. Documentation is part of the definition of done, never a follow-up PR; a reviewer can ask for it before merge.

- **`site/content/*.adoc`** - hand-written pages (the Roq site). This is the source of truth. Every page declares its place in the navigation in its header: `:page-nav-group:` (the sidebar group), `:page-nav-order:` (an integer, global: groups appear in the order of their lowest page) and, when it differs from `:page-title:`, `:page-nav-title:`. A page without the first two fails the docs build. Adding a page therefore needs no template or nav edit.
- **`site/data/nav.yml`** and **`docs/modules/ROOT/nav.adoc`** - **generated** navigation. `docs/nav/DocsNav.java` (run by the `generate-docs-nav` execution of `docs/pom.xml`, `exec-maven-plugin`) reads those attributes and writes both, so the Roq sidebar (`site/templates/layouts/default.html` loops over `cdi:nav.groups`) and Antora can never list different pages. The current page is highlighted by comparing its source path (`page.source.path`) with the entry's `file`, never by title, so renaming a title keeps the highlight. Never edit either file by hand.
- **Site extras.** Search is the Roq Lunr plugin (`quarkus-roq-plugin-lunr`): `site/content/search-index.json` is the build-time index, the script and its stylesheet are bundled, so it works offline; `site.ignored-files=includes/**` keeps the generated config tables out of the results (the `include::` directives still resolve), and `docs-preview-build` mirrors `site/content` without `search-index.json`. Copy buttons on code blocks are `site/web/app.js`. Dark mode follows the shared tokens block in `site/web/app.css` (`prefers-color-scheme`), and the search theme variables and Mermaid theme follow it.
- **Drift check.** `scripts/check-docs-drift.sh` fails when `docs/`, `site/content/includes/` or `site/data/` differ from what is committed. The `docs-preview-build` workflow runs it right after `mvn -f docs/pom.xml verify`, so a commit that changes `site/` without the regenerated mirror, or a hand edit of a generated file, fails the PR. `scripts/test-docs-nav.sh` tests the generator and the check against throwaway fixtures.
- **`site/content/includes/*.adoc`** - **generated** config reference. `docs/pom.xml` runs `quarkus-config-doc-maven-plugin` (build-time augmentation, no datasource, depends on every `*-deployment` artifact) which writes one file per config root from the `@ConfigMapping` JavaDoc into `site/content/includes/`. Never edit these by hand - they are overwritten every build.
- **`site/public/images/`** - committed images served at the site root: `diagrams/*.svg` (hand-written SVG with `<title>`, `<desc>` and a dark palette via `prefers-color-scheme`, no build-time diagram toolchain) and `console/<page>-{light,dark}.png`, one pair per sidebar page. Pages reference them as `../images/...` because Roq serves each page at `<name>/index.html`. Regenerate the screenshots with `scripts/docs-screenshots.mjs` against a throwaway instance (not run in CI, so the build stays deterministic); `scripts/check-docs-content.sh` fails when a console page lacks its section or image, a diagram lacks its accessible text, an image or `xref:` target does not exist, or a task guide is missing. A new console page needs its section in `console.adoc` and both images in the same PR.
- **`docs/modules/ROOT/pages/*`** - the Antora copy. The `sync-site-to-antora` execution in `docs/pom.xml` (`maven-resources-plugin`, `verify` phase) copies all of `site/content/` here. It is a generated mirror; **never edit `docs/` directly** (the next build overwrites it). If you must commit the mirror without a full build, copy the changed file from `site/content/` to the matching `docs/modules/ROOT/pages/` path.

Regenerate after a config change:

```bash
mvn install -DskipTests                  # build extensions so config metadata is available
mvn -f docs/pom.xml verify -DskipTests   # generate includes/ and the navigation, then mirror site/ -> docs/
scripts/check-docs-drift.sh              # nothing generated may differ from what is committed
```

**`config-reference.adoc` is hand-written**, not generated. It surfaces each generated table with an `include::` directive. A `@ConfigMapping(prefix = "qlawkus.foo")` interface produces `qlawkus-client_qlawkus.foo.adoc`; the aggregate `qlawkus-client.adoc` holds **only** the `quarkus.*`-rooted properties. So **adding a new `qlawkus.*` config root requires adding a matching `include::includes/qlawkus-client_qlawkus.foo.adoc[]` line** under "Core agent" in `config-reference.adoc`, in **both** trees, or the new properties never render. (This is exactly how the entire `qlawkus.*` surface was silently dropped after the `@ConfigMapping` migration.)

## Key Entry Points

- `client/runtime/.../agent/AgentService.java` - `@RegisterAiService` interface (ReAct agent, `maxSequentialToolInvocations = AgentToolLimits.MAX_SEQUENTIAL_TOOL_INVOCATIONS`); wires `systemMessageProviderSupplier`, `retrievalAugmentor` (Active Memory), tools, and `toolProviderSupplier`. The declarative `QlawkusAgent` (messaging) cannot take that attribute, so `AgentToolLimitConfigSourceFactory` publishes the same constant as `quarkus.langchain4j.ai-service.max-tool-executions` (ordinal 100); without it the upstream default of 10 applies and a turn with more tool rounds fails and replays on the fallback path (#446)
- `client/runtime/.../rest/ApiResource.java` - `POST /api/chat` (SSE) and `POST /api/chat/sync`
- `client/runtime/.../cognition/SoulEngine.java` - Builds the dynamic system prompt: `Soul` persona + `UserProfile` owner block + execution-bias section, injected every turn
- `client/runtime/.../deployment/ClientProcessor.java` - Build step: discovers `@QlawTool` beans + DTOs for reflection
- `client/runtime/.../model/PrimaryChatGuard.java` - `TypedGuard` around the primary chat model: retry, circuit breaker, and the switch to the fallback provider
- `client/runtime/.../health/` - `ModelReadinessCheck` and `CognitionReadinessCheck`, the two checks behind `/q/health/ready`

## CI / Release

7 GitHub Actions workflows:

- **`build-pull-request.yml`** - On PR: quick build → JVM test matrix (per module) → native IT matrix (per IT module) → build report. No API key passed → LLM tests auto-skip
- **`build-push.yml`** - On push to main: full `mvn clean install` with `LLM_API_KEY: dummy`, then a separate step running only `integration-tests/live-provider` with the `NVIDIA_API_KEY` secret
- **`build-nightly.yml`** - Cron seg-sex 02:00 UTC + dispatch: JVM + native build, same `NVIDIA_API_KEY` secret
- **`pre-release.yml`** - On PR that changes `.github/project.yml`: validates version is not SNAPSHOT, blocks forks
- **`release-prepare.yml`** - On PR merge that changes `.github/project.yml`: sets POM version, tags (no `v` prefix), pushes tag, bumps to next SNAPSHOT
- **`release-perform.yml`** - On tag push: builds artifacts, creates draft GitHub Release with PR-based changelog, then calls `publish-images.yml`
- **`publish-images.yml`** - Reusable (`workflow_call` from release, `workflow_dispatch` for a dry run, `push` defaults to false): builds the JVM, `-devtools` and `-native` images in-container and pushes them to `ghcr.io/<owner>/qlawkus` with the version tag, plus `latest` variants on a release

Release flow: edit `current-version` in `.github/project.yml` → open PR (triggers `pre-release` validation) → merge triggers `release-prepare` → tag push triggers `release-perform` → edit/publish draft release.

Tags have no `v` prefix (e.g. `0.7.2`, not `v0.7.2`). Dependabot enabled for GitHub Actions + Maven dependencies (daily, ignores `io.quarkus:*`).

## Gotchas

- `client/` Dev Services for LLM are **disabled** in `client/runtime/application.properties` (`quarkus.langchain4j.devservices.enabled=false`) - they're enabled only in `app/` via profile overrides
- `app/` dev profile sets `flyway.clean-at-start=true` on all datasources: all data is wiped on every `quarkus:dev` restart
- Prod Docker exposes port **8742** (not 8080) for Google OAuth redirect URI compatibility
- `@Logged` on `AgentService` is an interceptor that logs agent interactions
- `SoulEngine` default timezone is `America/Sao_Paulo`
- `pty4j` + JNA are direct dependencies in `client/runtime` for interactive shell tool support
