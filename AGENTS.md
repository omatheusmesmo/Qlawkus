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
- `SemanticExtractorObserver` - extracts facts from a conversation on `ChatCompletedEvent`. **Only the REST SSE path fires this event; the messaging path does not** - so passive fact extraction does not happen over Discord/Telegram (explicit `rememberFact` and the profile tool still work).

Config knobs: `qlawkus.agent.memory.*`, `qlawkus.agent.active-memory.*`, `qlawkus.agent.transcript-*`, `qlawkus.memory-review.*`, `qlawkus.memory-curation.*`. Admin REST: `GET/DELETE /api/admin/memory` (summary / purge), `POST /api/admin/memory/{review,curate}`. End-to-end check: `scripts/memory-benchmark.sh` (needs a running instance + real LLM).

Gotcha when testing recall: working memory and the system prompt are both sources, so to prove a fact came from the vector store, `TRUNCATE chat_message_entity` first. The embedding model (`nvidia/nv-embedqa-e5-v5`) is retrieval-tuned and scores relevant pairs high, so a cosine `min-score` of 0.9 is not as strict as it looks - `0.7` is the default margin.

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

Create a CDI bean annotated with `@ClawTool` + `@ApplicationScoped`. Methods annotated with `@Tool` are auto-discovered at build time by `ClientProcessor` (which also registers DTO records in `dev.omatheusmesmo.qlawkus.dto` for reflection). No manual registration needed.

New tool modules must also be added as dependencies in `app/pom.xml`.

## Databases

3 PostgreSQL databases, all managed by Flyway:
- **`qlawkus`** (default) - Main: SOUL, chat history, episodic journal, pgvector embeddings
- **`qlawkus_google_auth`** - OAuth credentials + state
- **`qlawkus_brag`** - Career brag documents

In Docker, `docker/postgres-init/01-create-databases.sql` creates the extra DBs. In dev mode, Flyway `clean-at-start` resets schema on every restart.

## LLM Configuration

Two named model configs via `@ModelName`:
- **`nvidia`** - Primary (OpenAI-compatible provider, default: NVIDIA NIM)
- **`ollama-fallback`** - Fallback (Ollama, used when primary fails after retries + circuit breaker)

Dev mode: Dev Services auto-provisions Ollama (no `.env` needed).
Prod: Requires `NVIDIA_AI_API_KEY` in `.env`.

Embedding dimension is hardcoded to 1024 via `EMBEDDING_DIMENSION` - must match the model output.

## Key Entry Points

- `client/runtime/.../agent/AgentService.java` - `@RegisterAiService` interface (ReAct agent, `maxSequentialToolInvocations=100`); wires `systemMessageProviderSupplier`, `retrievalAugmentor` (Active Memory), tools, and `toolProviderSupplier`
- `client/runtime/.../rest/ApiResource.java` - `POST /api/chat` (SSE) and `POST /api/chat/sync`
- `client/runtime/.../cognition/SoulEngine.java` - Builds the dynamic system prompt: `Soul` persona + `UserProfile` owner block + execution-bias section, injected every turn
- `client/runtime/.../deployment/ClientProcessor.java` - Build step: discovers `@ClawTool` beans + DTOs for reflection

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
