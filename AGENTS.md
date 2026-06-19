# Qlawkus - Agent Instructions

## Dev Mode Start

```bash
mvn install -pl client -am -DskipTests   # must run first; client/ is a Quarkus extension
cd app && mvn quarkus:dev                 # live reload only works in app/
```

Changing `client/` requires re-running `mvn install -pl client -am` then restarting dev mode. Hot reload does NOT apply to extension code.

## Architecture

Multi-module Maven monorepo with a Quarkus extension pattern (`client/deployment` + `client/runtime`):

- **`client/`** - Quarkus extension (core agent, cognition, REST, tools). Not a runnable app.
- **`app/`** - Deployable Quarkus app (`packaging=quarkus`). Wires all extensions together. Only module you run.
- **`tools/`** - Optional tool extensions (Google Workspace 6 modules + Brag). Each is a standalone Quarkus extension with `deployment/` + `runtime/`.
- **`messaging/`** - Messaging extension (core + Discord/Telegram/Slack/WhatsApp adapters). Same `deployment/` + `runtime/` pattern.
- **`integration-tests/`** - Per-feature integration test modules (smoke, cognition, google, terminal, brag, code-review, messaging). Each is a standalone Quarkus app (`packaging=quarkus`).
- **`testing-internal/`** - Shared test utilities (depends on `qlawkus-client`).

`app/` depends on `client`, all `tools/*`, and `messaging` (core + discord + telegram). Slack and WhatsApp adapters are built but not in `app/` pom.

## Cognition & Memory

The memory subsystem (`client/runtime/.../cognition` + `.../store`) is the core of the agent. Its guiding principle: **memory is injected into the prompt, not searched** - recall must not depend on the model choosing to call a tool. Understanding it means reading several files together:

Three layers, all injected each turn via `SoulEngine` (the agent's `systemMessageProviderSupplier`) and the retrieval augmentor:

- **Persona** - `Soul` (singleton entity) rendered into the system message by `SoulEngine`.
- **Owner** - `UserProfile` (singleton; the single user this agent serves) injected every turn by `SoulEngine`. Maintained by `UpdateUserProfileTool`, refreshed by `MemoryCurationJob`.
- **Facts** - `FactStore` / `PgFactStore` (pgvector). Every embedding is tagged with a `MemorySource` (`remember-tool`, `semantic-extractor`, `episodic-consolidator`, `transcript`) - the enum exists so producers and purges never drift.

Stores & retrieval:

- **Active Memory** - `ActiveMemoryAugmentor` is the AiService `retrievalAugmentor`; it runs an `EmbeddingStoreContentRetriever` before each reply and injects query-relevant facts. It filters OUT `source=transcript` so curated facts stay clean.
- **Working memory** - `PgWorkingMemoryStore` (a langchain4j `ChatMemoryStore`), windowed to 40 messages. `updateMessages` is append-only (preserves `createdAt`; never reset it) and fires `MessagesAppendedEvent` for every new message on any channel.
- **Transcripts (session_search)** - `TranscriptArchiveObserver` embeds each message as `source=transcript`; `SearchTranscriptsTool` searches only those.
- **Episodic** - `EpisodicConsolidatorJob` (nightly) summarizes each day into journals (also embedded).

Scheduled background jobs, each with a manual `POST /api/admin/memory/*` trigger:

- `MemoryReviewJob` - semantic dedup of near-duplicate facts (`/review`).
- `MemoryCurationJob` - folds facts into the owner profile, reconciling contradictions; leaves facts untouched (`/curate`).
- `SemanticExtractorObserver` - extracts facts from a conversation on `ChatCompletedEvent`. The event is emitted channel-agnostically by `ChatCompletionEmitter`, which observes the store-level `MessagesAppendedEvent` and fires once per turn (when a final assistant message - one without tool-execution requests - is appended). **Passive fact extraction works on every channel** (REST, Discord, Telegram); gate it with `qlawkus.agent.semantic-extractor.enabled` (default `true`). The same event also drives the Brag `AchievementProcessor`, so both run on all channels.

Config knobs: `qlawkus.agent.memory.*`, `qlawkus.agent.active-memory.*`, `qlawkus.agent.transcript-*`, `qlawkus.agent.semantic-extractor.*`, `qlawkus.memory-review.*`, `qlawkus.memory-curation.*`. Admin REST: `GET/DELETE /api/admin/memory` (summary / purge), `POST /api/admin/memory/{review,curate}`. End-to-end check: `scripts/memory-benchmark.sh` (needs a running instance + real LLM).

Gotcha when testing recall: working memory and the system prompt are both sources, so to prove a fact came from the vector store, `TRUNCATE chat_message_entity` first. The embedding model (`nvidia/nv-embedqa-e5-v5`) is retrieval-tuned and scores relevant pairs high, so a cosine `min-score` of 0.9 is not as strict as it looks - `0.7` is the default margin.

## Skills (procedural memory)

Skills are the agent's **procedural** memory (how to do a recurring task), complementing the declarative memory above. Each skill is a `SKILL.md` file (YAML frontmatter `name` + `description` + Markdown body). Same discipline as memory: the index is **injected, not searched** - `SoulEngine` adds a `## Skills` block (name + description only, via `SkillIndexRenderer`, capped by `qlawkus.skills.max-injected`) every turn; the full body is loaded on demand with the `viewSkill` tool (progressive disclosure).

- **SPI**: `skill.SkillStore` (interface) with `Skill`/`SkillSummary` records and `SkillFrontmatter`. The backend is selected at **build time** via `@IfBuildProperty` on `qlawkus.cognition.backend` (`markdown` | `pgvector` | `hybrid`): `MarkdownSkillStore` (`@DefaultBean`, no DB, shared file logic in `MarkdownSkillFiles`), `store.pg.PgSkillStore` (the `skill` table, `V6` migration), `store.pg.HybridSkillStore` (files source-of-truth + pg mirror). Reads from all `qlawkus.skills.roots` (first wins); writes only to the first, owned root (default `~/.qlawkus/skills`).
- **Tools** (in `AgentService`): `ViewSkillTool` (load body), `ManageSkillTool` (`createOrUpdateSkill` / `deleteSkill`).
- **Auto-distill**: `SkillExtractorObserver` mines a reusable skill from each `ChatCompletedEvent` (gate `qlawkus.skills.extractor.enabled`), mirroring `SemanticExtractorObserver`.
- **Curation**: `SkillCurationJob` removes redundant skills (scheduled + `POST /api/admin/skills/curate`). Admin: `GET/DELETE /api/admin/skills`, `GET /api/admin/skills/{name}`.

Config knobs: `qlawkus.cognition.backend`, `qlawkus.skills.*`. Deferred SP1 follow-ups: usage-telemetry lifecycle (active/stale/archived) and build-time discovery of extension-contributed bundled skills. The broader plan (making facts/episodic/working memory pluggable too) lives outside the repo in the owner's notes.

## Testing

```bash
# Single test class (-am builds upstream modules; the failIfNoSpecifiedTests flag
# stops sibling modules in the reactor from erroring when the class isn't theirs)
mvn test -pl client/deployment -am -Dtest=UserProfileTest -Dsurefire.failIfNoSpecifiedTests=false

# Unit tests for a single module (uses WireMock, no LLM needed)
mvn test -pl client/deployment

# Integration tests (LLM-dependent tests auto-skip if no API key)
mvn test -pl integration-tests/smoke

# Integration tests with real LLM (requires NVIDIA_AI_API_KEY env var)
NVIDIA_AI_API_KEY=your-key mvn test -pl integration-tests/smoke

# Full build (skip ITs by default - skipITs=true in parent pom)
mvn verify

# Native ITs (enable with -Pnative)
mvn verify -Pnative
```

- `client/deployment/src/test/` - Extension build-time tests with WireMock-mocked LLM
- `integration-tests/*/src/test/` - QuarkusTest integration tests; some call real LLMs, some use `@InjectMock`
- LLM-dependent tests use `@EnabledIf("dev.omatheusmesmo.qlawkus.testing.QlawkusTestUtils#usesLLM")` - auto-skip when `NVIDIA_AI_API_KEY` is absent or "dummy"
- Smoke tests (`integration-tests/smoke/`) hit real LLM: can take 5+ minutes per test

## Adding Tools

Create a CDI bean annotated with `@QlawTool` + `@ApplicationScoped`. Methods annotated with `@Tool` are auto-discovered at build time by `ClientProcessor` (which also registers DTO records in `dev.omatheusmesmo.qlawkus.dto` for reflection). No manual registration needed.

New tool modules must also be added as dependencies in `app/pom.xml`.

## Extension Development

Every module here (`client`, `tools/*`, `messaging/*`) is a full Quarkus extension: a `runtime/` + `deployment/` pair. Rules that keep that pattern correct:

- **Dependency direction is one-way.** `deployment/` depends on `runtime/` (plus other extensions' `*-deployment` artifacts); `runtime/` must NEVER depend on any `deployment` artifact. The Maven plugin validates this and fails the build if a required deployment dependency is missing.
- **Three bootstrap phases.** Build-time *augmentation* runs `@BuildStep` processors (e.g. `ClientProcessor`) - it reads the Jandex index, never loads app classes, and records bytecode. `@Record(STATIC_INIT)` bytecode runs in a static initializer (framework boot, bean wiring; do NOT open ports, start threads, or read runtime config here). `@Record(RUNTIME_INIT)` bytecode runs in `main` (ports, runtime config, service start). Push work to STATIC_INIT when possible.
- **Build steps talk via build items, not fields.** A build-step class is instantiated per invocation and discarded; share state only through immutable `*BuildItem`s. Always produce a `FeatureBuildItem`. A step runs only if something consumes what it produces.
- **Recorders bridge build to runtime.** `@Recorder` methods in `runtime/` are *recorded* (not executed) at build time; their calls replay at runtime. Parameters must be bytecode-serializable (primitives, strings, config objects, `RuntimeValue<T>`). Wrap complex objects in `RuntimeValue<T>`.
- **Config is `@ConfigMapping` interfaces.** `@ConfigRoot(phase = RUN_TIME)` + `@ConfigMapping(prefix = "qlawkus...")`, JavaDoc on each accessor (it becomes the generated config-reference description), `@WithDefault` for defaults, `Optional<T>` for optional, `Map<String, X>` for keyed/dynamic config (see `MessagingConfig`). Choose phase by need: `BUILD_TIME` (build only), `BUILD_AND_RUN_TIME_FIXED` (read at build, immutable at runtime), `RUN_TIME` (re-read each run, overridable).
- **CDI registration.** Register extension beans with `AdditionalBeanBuildItem`; use `@DefaultBean` for anything app code should be able to override; `SyntheticBeanBuildItem` for beans built through a recorder.
- **Native image.** Reflection needs registration - `ClientProcessor` already registers `dev.omatheusmesmo.qlawkus.dto` records via `ReflectiveClassBuildItem`; new reflective types (DTOs, models) must be added the same way (or `@RegisterForReflection`). Use `NativeImageResourceBuildItem` to bundle resources. Verify with `mvn verify -Pnative`.
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

3 PostgreSQL databases, all managed by Flyway:
- **`qlawkus`** (default) - Main: SOUL, chat history, episodic journal, pgvector embeddings
- **`qlawkus_google_auth`** - OAuth credentials + state
- **`qlawkus_brag`** - Career brag documents

In Docker, `docker/postgres-init/01-create-databases.sql` creates the extra DBs. In dev mode, Flyway `clean-at-start` resets schema on every restart.

## LLM Configuration

Two named model configs via `@ModelName`, both provider-agnostic:
- **`primary`** - Primary model (any OpenAI-compatible provider; default endpoint: NVIDIA NIM). Pointed via the neutral `LLM_*` env vars (`LLM_BASE_URL`, `LLM_API_KEY`, `LLM_CHAT_MODEL`, `LLM_EMBEDDING_MODEL`, `LLM_TIMEOUT`).
- **`fallback`** - Fallback (Ollama, used when primary fails after retries + circuit breaker).

Dev mode: Dev Services auto-provisions Ollama (no `.env` needed).
Prod: Requires `LLM_API_KEY` in `.env`.

Embedding dimension is hardcoded to 1024 via `EMBEDDING_DIMENSION` - must match the model output.

## Key Entry Points

- `client/runtime/.../agent/AgentService.java` - `@RegisterAiService` interface (ReAct agent, `maxSequentialToolInvocations=100`); wires `systemMessageProviderSupplier`, `retrievalAugmentor` (Active Memory), tools, and `toolProviderSupplier`
- `client/runtime/.../rest/ApiResource.java` - `POST /api/chat` (SSE) and `POST /api/chat/sync`
- `client/runtime/.../cognition/SoulEngine.java` - Builds the dynamic system prompt: `Soul` persona + `UserProfile` owner block + execution-bias section, injected every turn
- `client/runtime/.../deployment/ClientProcessor.java` - Build step: discovers `@QlawTool` beans + DTOs for reflection

## CI / Release

6 GitHub Actions workflows:

- **`build-pull-request.yml`** - On PR: quick build → JVM test matrix (per module) → native IT matrix (per IT module) → build report. No API key passed → LLM tests auto-skip
- **`build-push.yml`** - On push to main: full `mvn clean install` with `NVIDIA_AI_API_KEY` → all tests run
- **`build-nightly.yml`** - Cron seg-sex 02:00 UTC + dispatch: JVM + native build with `NVIDIA_AI_API_KEY` → all tests run
- **`pre-release.yml`** - On PR that changes `.github/project.yml`: validates version is not SNAPSHOT, blocks forks
- **`release-prepare.yml`** - On PR merge that changes `.github/project.yml`: sets POM version, tags (no `v` prefix), pushes tag, bumps to next SNAPSHOT
- **`release-perform.yml`** - On tag push: builds artifacts, creates draft GitHub Release with PR-based changelog

Release flow: edit `current-version` in `.github/project.yml` → open PR (triggers `pre-release` validation) → merge triggers `release-prepare` → tag push triggers `release-perform` → edit/publish draft release.

Tags have no `v` prefix (e.g. `0.7.2`, not `v0.7.2`). Dependabot enabled for GitHub Actions + Maven dependencies (daily, ignores `io.quarkus:*`).

## Gotchas

- `client/` Dev Services for LLM are **disabled** in `client/runtime/application.properties` (`quarkus.langchain4j.devservices.enabled=false`) - they're enabled only in `app/` via profile overrides
- `app/` dev profile sets `flyway.clean-at-start=true` on all datasources: all data is wiped on every `quarkus:dev` restart
- Prod Docker exposes port **8742** (not 8080) for Google OAuth redirect URI compatibility
- `@Logged` on `AgentService` is an interceptor that logs agent interactions
- `SoulEngine` default timezone is `America/Sao_Paulo`
- `pty4j` + JNA are direct dependencies in `client/runtime` for interactive shell tool support
