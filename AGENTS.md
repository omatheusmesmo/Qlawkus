# Qlawkus - Agent Instructions

## Dev Mode Start

```bash
mvn install -pl client -am -DskipTests   # must run first; client/ is a Quarkus extension
cd app && mvn quarkus:dev                 # live reload only works in app/
```

Changing `client/` requires re-running `mvn install -pl client -am` then restarting dev mode. Hot reload does NOT apply to extension code.

## Architecture

Multi-module Maven monorepo with a Quarkus extension pattern (`client/deployment` + `client/runtime`):

- **`client/`** - Quarkus extension (core agent, cognition, REST, tools). Not a runnable app. **Database-free**: depends on no datasource/JDBC/Flyway/pgvector - the markdown stores are its `@DefaultBean` backend.
- **`cognition-pgvector/`** - Optional Quarkus extension (`deployment/` + `runtime/`) holding the entire Postgres/pgvector backend for the cognition stores (the `store.pg` package, the 7 Flyway migrations, and the datasource/pgvector config as `META-INF/microprofile-config.properties`). Present in `app/`; absent in a markdown-only distribution. `requires` the `dev.omatheusmesmo.qlawkus.agent` capability.
- **`app/`** - Deployable Quarkus app (`packaging=quarkus`). Wires all extensions together. Only module you run.
- **`tools/`** - Optional tool extensions (Google Workspace 6 modules + Brag). Each is a standalone Quarkus extension with `deployment/` + `runtime/`.
- **`messaging/`** - Messaging extension (core + Discord/Telegram/Slack/WhatsApp adapters). Same `deployment/` + `runtime/` pattern.
- **`integration-tests/`** - Per-feature integration test modules (smoke, cognition, markdown-only, google, terminal, brag, code-review, messaging). Each is a standalone Quarkus app (`packaging=quarkus`). `markdown-only` is the proof of the database-free distribution (boots with no datasource); `cognition` depends on `cognition-pgvector` to exercise the Postgres path.
- **`testing-internal/`** - Shared test utilities (depends on `qlawkus-client`).

`app/` depends on `client`, `cognition-pgvector`, all `tools/*`, and `messaging` (core + discord + telegram). Slack and WhatsApp adapters are built but not in `app/` pom.

## Cognition & Memory

The memory subsystem (`client/runtime/.../cognition` + `.../store`) is the core of the agent. Its guiding principle: **memory is injected into the prompt, not searched** - recall must not depend on the model choosing to call a tool. Understanding it means reading several files together:

Three layers, all injected each turn via `SoulEngine` (the agent's `systemMessageProviderSupplier`) and the retrieval augmentor:

- **Persona** - `Soul` (a plain singleton domain object) rendered into the system message by `SoulEngine`. Persisted behind the `SoulStore` SPI, backend selected by the same `qlawkus.cognition.backend` switch: `store.markdown.MarkdownSoulStore` (`@DefaultBean`, a `soul.md` file seeded from `META-INF/qlawkus/default-soul.md`, no DB), `store.pg.PgSoulStore` (the `soul` table via `SoulEntity`), `store.pg.HybridSoulStore` (reuses pg).
- **Owner** - `UserProfile` (a plain singleton domain object; the single user this agent serves) injected every turn by `SoulEngine`. Maintained by `UpdateUserProfileTool`, refreshed by `MemoryCurationJob`. Persisted behind the `UserProfileStore` SPI: `MarkdownUserProfileStore` (`@DefaultBean`, an `owner.md` file), `PgUserProfileStore` (the `user_profile` table via `UserProfileEntity`), `HybridUserProfileStore`. Roots for the markdown soul/profile files: `qlawkus.agent.state.root` (default `~/.qlawkus/state`).
- **Facts** - `FactStore` SPI, backend selected at **build time** via `@IfBuildProperty` on `qlawkus.cognition.backend` (`markdown` | `pgvector` | `hybrid`): `store.markdown.MarkdownFactStore` (`@DefaultBean`; `.md` files + in-process `InMemoryEmbeddingStore` with a JSON embedding cache, no DB; shared file logic in `MarkdownFactFiles`), `store.pg.PgFactStore` (pgvector; `@IfBuildProperty pgvector enableIfMissing`, only present when the `cognition-pgvector` extension is on the classpath), `store.pg.HybridFactStore` (files source-of-truth + pg mirror). Root: `qlawkus.agent.facts.root` (default `~/.qlawkus/facts`). The markdown stores are `@DefaultBean` across the module split, so they win whenever the pgvector extension is absent (database-free) and the non-default pg/hybrid beans override them when it is present - regardless of the `backend` value. Every embedding is tagged with a `MemorySource` (`remember-tool`, `semantic-extractor`, `episodic-consolidator`, `transcript`) - the enum exists so producers and purges never drift.

Stores & retrieval:

- **Active Memory** - `ActiveMemoryAugmentor` is the AiService `retrievalAugmentor`; it runs an `EmbeddingStoreContentRetriever` before each reply and injects query-relevant facts. It filters OUT `source=transcript` so curated facts stay clean.
- **Working memory** - `WorkingMemoryStore` SPI (also a langchain4j `ChatMemoryStore`), windowed to 40 messages. `updateMessages` is append-only (preserves `createdAt`; never reset it) and fires `MessagesAppendedEvent` for every new message on any channel. Backends: `store.markdown.MarkdownWorkingMemoryStore` (`@DefaultBean`, one append-only `<memoryId>.jsonl` per conversation under `qlawkus.agent.working-memory.root`, default `~/.qlawkus/working-memory`), `store.pg.PgWorkingMemoryStore` (the `chat_message_entity` table), `store.pg.HybridWorkingMemoryStore` (reuses pg - the rolling chat log is transient, not file-versioned). The markdown store is the `@DefaultBean`; because upstream ships a `@DefaultBean InMemoryChatMemoryStore`, `ClientProcessor` vetoes it (`ExcludedTypeBuildItem`) so there is a single default and the non-default pg/hybrid beans still override when the pgvector extension is present.
- **Transcripts (session_search)** - `TranscriptArchiveObserver` embeds each message as `source=transcript`; `SearchTranscriptsTool` searches only those.
- **Episodic** - `EpisodicConsolidatorJob` (nightly) summarizes each day into journals (also embedded through the `FactStore` as `source=episodic-consolidator`). The journal record is an `EpisodicStore` SPI, backend selected at build time by the same `qlawkus.cognition.backend`: `store.markdown.MarkdownEpisodicStore` (`@DefaultBean`; dated `<date>.md` files, one per day, no DB; file logic in `MarkdownEpisodicFiles`), `store.pg.PgEpisodicStore` (the `Journal` table), `store.pg.HybridEpisodicStore` (files source-of-truth + pg mirror). Pg/hybrid share Panache access via `JournalRepository` and live in the `cognition-pgvector` extension. Root: `qlawkus.agent.episodic.root` (default `~/.qlawkus/journals`).

Scheduled background jobs, each with a manual `POST /api/admin/memory/*` trigger:

- `MemoryReviewJob` - semantic dedup of near-duplicate facts (`/review`).
- `MemoryCurationJob` - folds facts into the owner profile, reconciling contradictions; leaves facts untouched (`/curate`).
- `SemanticExtractorObserver` - extracts facts from a conversation on `ChatCompletedEvent`. The event is emitted channel-agnostically by `ChatCompletionEmitter`, which observes the store-level `MessagesAppendedEvent` and fires once per turn (when a final assistant message - one without tool-execution requests - is appended). **Passive fact extraction works on every channel** (REST, Discord, Telegram); gate it with `qlawkus.agent.semantic-extractor.enabled` (default `true`). The same event also drives the Brag `AchievementProcessor`, so both run on all channels.

Config knobs: `qlawkus.agent.memory.*`, `qlawkus.agent.active-memory.*`, `qlawkus.agent.transcript-*`, `qlawkus.agent.semantic-extractor.*`, `qlawkus.memory-review.*`, `qlawkus.memory-curation.*`. Admin REST: `GET/DELETE /api/admin/memory` (summary / purge), `POST /api/admin/memory/{review,curate}`. End-to-end check: `scripts/memory-benchmark.sh` (needs a running instance + real LLM).

Gotcha when testing recall: working memory and the system prompt are both sources, so to prove a fact came from the vector store, `TRUNCATE chat_message_entity` first. The embedding model (`nvidia/nv-embedqa-e5-v5`) is retrieval-tuned and scores relevant pairs high, so a cosine `min-score` of 0.9 is not as strict as it looks - `0.7` is the default margin.

## Skills (procedural memory)

Skills are the agent's **procedural** memory (how to do a recurring task), complementing the declarative memory above. Each skill is a `SKILL.md` file (YAML frontmatter `name` + `description` + Markdown body). Same discipline as memory: the index is **injected, not searched** - `SoulEngine` adds a `## Skills` block (name + description only, via `SkillIndexRenderer`, capped by `qlawkus.skills.max-injected`) every turn; the full body is loaded on demand with the `viewSkill` tool (progressive disclosure).

- **SPI**: `skill.SkillStore` (interface) with `Skill`/`SkillSummary` records. SKILL.md **parsing is delegated to the upstream `dev.langchain4j.skills` loaders** (`FileSystemSkillLoader`, agentskills.io spec, BOM-managed `dev.langchain4j:langchain4j-skills`); `SkillFrontmatter` owns only the **write** side (rendering a `Skill` back to frontmatter + body), which upstream does not provide. The backend is selected at **build time** via `@IfBuildProperty` on `qlawkus.cognition.backend` (`markdown` | `pgvector` | `hybrid`): `MarkdownSkillStore` (`@DefaultBean`, no DB, shared file logic in `MarkdownSkillFiles`), `store.pg.PgSkillStore` (the `skill` table, `V6` migration), `store.pg.HybridSkillStore` (files source-of-truth + pg mirror). Reads from all `qlawkus.skills.roots` (first wins); writes only to the first, owned root (default `~/.qlawkus/skills`).
- **Tools** (in `AgentService`): `ViewSkillTool` (load body), `ManageSkillTool` (`createOrUpdateSkill` / `deleteSkill`).
- **Auto-distill**: `SkillExtractorObserver` mines a reusable skill from each `ChatCompletedEvent` (gate `qlawkus.skills.extractor.enabled`), mirroring `SemanticExtractorObserver`.
- **Curation**: `SkillCurationJob` removes redundant skills (scheduled + `POST /api/admin/skills/curate`).
- **Lifecycle**: `viewSkill` calls `recordUse`; `SkillLifecycleJob` ages unused skills `ACTIVE -> STALE -> ARCHIVED` (config `qlawkus.skills.lifecycle.*`, scheduled + `POST /api/admin/skills/lifecycle`). Archived skills leave the injected index but stay loadable; pinned skills (`POST /api/admin/skills/{name}/pin`) never transition. Telemetry: pg columns (migration `V7`) for pgvector/hybrid, a `.qlawkus-usage.json` sidecar for markdown.
- **Admin REST**: `GET/DELETE /api/admin/skills`, `GET /api/admin/skills/{name}`, `POST /api/admin/skills/{curate,lifecycle}`, `POST /api/admin/skills/{name}/pin`.
- **Bundled skills (build-time)**: extensions/apps ship read-only skills as classpath resources under `META-INF/qlawkus-skills/<name>/SKILL.md`. `ClientProcessor.bundledSkills` (a `@BuildStep` + `SkillsRecorder`) scans and parses them (via the same `FileSystemSkillLoader`) at **augmentation** and bakes them into the synthetic `BundledSkills` bean - no runtime classpath scanning. Stores merge bundled (read-only) with owned skills; owned wins on a name clash.

Config knobs: `qlawkus.cognition.backend`, `qlawkus.skills.*`. Facts, episodic journals, skills, working memory, the persona (`Soul`) and the owner profile (`UserProfile`) are all pluggable on this same switch, and the entire Postgres backend now lives in the optional `cognition-pgvector` extension - so a build without it is database-free (validated by `integration-tests/markdown-only`). The remaining broader plan (a `SkillHub` capability backed by jskills/skills.sh) lives outside the repo in the owner's notes.

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

Up to 3 PostgreSQL databases, all managed by Flyway:
- **`qlawkus`** (default) - Main: persona, owner profile, chat history, episodic journal, pgvector embeddings. **Only present when the `cognition-pgvector` extension is on the classpath** (it owns the default datasource, the 7 `V1..V7` migrations, and the pgvector config, contributed via its `META-INF/microprofile-config.properties`). A markdown-only build has no default datasource and never connects to Postgres.
- **`qlawkus_google_auth`** - OAuth credentials + state (Google tools extension)
- **`qlawkus_brag`** - Career brag documents (Brag tool extension; its own named `brag` datasource, independent of cognition)

In Docker, `docker/postgres-init/01-create-databases.sql` creates the extra DBs. In dev mode, Flyway `clean-at-start` resets schema on every restart.

## Documentation

One source, one generated mirror. **Edit only `site/content/`** - everything else is built from it.

- **`site/content/*.adoc`** - hand-written pages (the Roq site): `index`, `architecture`, `config-reference`, `quickstart`, `messaging`, `voice`, `google-workspace`. This is the source of truth.
- **`site/content/includes/*.adoc`** - **generated** config reference. `docs/pom.xml` runs `quarkus-config-doc-maven-plugin` (build-time augmentation, no datasource, depends on every `*-deployment` artifact) which writes one file per config root from the `@ConfigMapping` JavaDoc into `site/content/includes/`. Never edit these by hand - they are overwritten every build.
- **`docs/modules/ROOT/pages/*`** - the Antora copy. The `sync-site-to-antora` execution in `docs/pom.xml` (`maven-resources-plugin`, `verify` phase) copies all of `site/content/` here. It is a generated mirror; **never edit `docs/` directly** (the next build overwrites it). If you must commit the mirror without a full build, copy the changed file from `site/content/` to the matching `docs/modules/ROOT/pages/` path.

Regenerate after a config change:

```bash
mvn install -DskipTests                  # build extensions so config metadata is available
mvn -f docs/pom.xml verify -DskipTests   # generate includes/ into site/, then mirror site/ -> docs/
```

**`config-reference.adoc` is hand-written**, not generated. It surfaces each generated table with an `include::` directive. A `@ConfigMapping(prefix = "qlawkus.foo")` interface produces `qlawkus-client_qlawkus.foo.adoc`; the aggregate `qlawkus-client.adoc` holds **only** the `quarkus.*`-rooted properties. So **adding a new `qlawkus.*` config root requires adding a matching `include::includes/qlawkus-client_qlawkus.foo.adoc[]` line** under "Core agent" in `config-reference.adoc`, in **both** trees, or the new properties never render. (This is exactly how the entire `qlawkus.*` surface was silently dropped after the `@ConfigMapping` migration.)

## LLM Configuration

Two named model configs via `@ModelName`, both provider-agnostic:
- **`primary`** - Primary model (any OpenAI-compatible provider; default endpoint: NVIDIA NIM). Pointed via the neutral `LLM_*` env vars (`LLM_BASE_URL`, `LLM_API_KEY`, `LLM_CHAT_MODEL`, `LLM_EMBEDDING_MODEL`, `LLM_TIMEOUT`).
- **`fallback`** - Fallback (Ollama, used when primary fails after retries + circuit breaker).

Dev mode: Dev Services auto-provisions Ollama (no `.env` needed).
Prod: Requires `LLM_API_KEY` in `.env`.

Embedding dimension is hardcoded to 1024 via `EMBEDDING_DIMENSION` - must match the model output.

### Dependency versions (do NOT conflate the two langchain4j namespaces)

| What | Version | Where set |
|------|---------|-----------|
| Quarkus platform | `3.33.2` | `quarkus.platform.version` in root `pom.xml` |
| quarkus-langchain4j (Quarkiverse extension + BOM) | `1.11.2` | `quarkus-langchain4j.version` in root `pom.xml` |
| Upstream `dev.langchain4j` core (transitive) | `1.16.2` | managed by `quarkus-langchain4j-bom:1.11.2` - do not set |
| Upstream beta modules (`langchain4j-skills`, `langchain4j-pgvector`, `langchain4j-agentic`) | `1.16.2-beta26` | same BOM - do not set |

The Quarkiverse **extension** version (`1.11.2`) and the upstream **library** version (`1.16.2`) are different namespaces: `1.11.2` is NOT a langchain4j-core version. `quarkus-langchain4j-bom` transitively imports the `langchain4j-bom`, which pins every `dev.langchain4j:*` artifact. So when adding an upstream module (e.g. `langchain4j-skills`, or `dev.langchain4j:langchain4j` for `InMemoryEmbeddingStore`), add it with **no `<version>`** - the BOM resolves it. Re-verify any time with:

```bash
mvn dependency:tree -pl client/runtime -Dincludes='dev.langchain4j'
```

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
