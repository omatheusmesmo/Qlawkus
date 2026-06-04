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

## Testing

```bash
# Unit tests for a single module (uses WireMock, no LLM needed)
mvn test -pl client/deployment

# Integration tests require Dev Services (auto-provisions Ollama + PostgreSQL via Testcontainers)
mvn test -pl integration-tests/smoke

# Integration tests with real LLM (slow, costs money)
mvn test -pl integration-tests/smoke -Pintegration

# Full build (skip ITs by default - skipITs=true in parent pom)
mvn verify

# Native ITs (enable with -Pnative)
mvn verify -Pnative
```

- `client/deployment/src/test/` - Extension build-time tests with WireMock-mocked LLM
- `integration-tests/*/src/test/` - QuarkusTest integration tests; some call real LLMs, some use Dev Services
- Smoke tests (`integration-tests/smoke/`) hit real LLM via Dev Services: can take 5+ minutes per test

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

- `client/runtime/.../agent/AgentService.java` - `@RegisterAiService` interface (ReAct agent, `maxSequentialToolInvocations=100`)
- `client/runtime/.../rest/ApiResource.java` - `POST /api/chat` (SSE) and `POST /api/chat/sync`
- `client/runtime/.../cognition/SoulEngine.java` - Builds dynamic system prompt from persisted `Soul` entity
- `client/runtime/.../deployment/ClientProcessor.java` - Build step: discovers `@ClawTool` beans + DTOs for reflection

## CI / Release

6 GitHub Actions workflows:

- **`build-pull-request.yml`** - On PR: quick build → JVM test matrix (per module) → native IT matrix (per IT module) → build report
- **`build-push.yml`** - On push to main: full `mvn clean install`
- **`build-nightly.yml`** - Cron seg-sex 02:00 UTC + dispatch: JVM + native build
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
