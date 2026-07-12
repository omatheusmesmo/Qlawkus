# Qlawkus

> Autonomous Personal Engineering Agent built with Quarkus LangChain4j

[![Status: Work in Progress](https://img.shields.io/badge/Status-Work%20in%20Progress-yellow?style=for-the-badge)](https://github.com/omatheusmesmo/Qlawkus/issues)

A single-user, production-ready autonomous agent with dynamic cognition, triple memory, procedural memory (skills), Google Workspace integration, multi-provider messaging (Discord, Telegram) with voice in/out, and a composable platform: a manifest decides which capabilities are built in, and a server-rendered control-plane UI drives onboarding, memory/skill management, live configuration, and job scheduling.

---

## Quick Start

### Prerequisites

- **Java 25** (LTS)
- **Maven 3.9+**
- **Docker** (for containerized runs)
- **Ollama** (optional — Dev Services auto-provisions it)

### 1. Clone & Configure

```bash
git clone https://github.com/omatheusmesmo/Qlawkus.git && cd Qlawkus
cp .env.example .env
```

Edit `.env` and set `LLM_API_KEY` if you plan to run in production mode. The Compose files ship `LLM_KIND=nvidia`; override `LLM_KIND` to use another provider.

### 2. Dev Mode (recommended for development)

Uses Dev Services — auto-provisions Ollama (+ PostgreSQL, if `cognition-pgvector` is on the classpath) via Testcontainers. No `.env` needed.

```bash
mvn install -pl client/runtime,client/deployment -am -DskipTests
cd app && mvn quarkus:dev
```

The agent starts at `http://localhost:8080`. Chat endpoint: `POST /api/chat`. Control-plane UI: `http://localhost:8080/console`.

> **Note:** `client/` is a Quarkus extension — live reload only applies to `app/`. Changes to `client/` require re-running `mvn install -pl client/runtime,client/deployment -am` + a dev-mode restart. The same applies to any other extension module (`console/`, `cognition-pgvector/`, `tools/*`, `messaging/*`).

> The containerized modes below build the whole reactor **inside** Docker (a multi-stage build on the Red Hat UBI OpenJDK image), so they need only Docker — no host JDK or Maven. `run.sh` wraps Docker Compose; run `./run.sh --help` for all modes and actions. The first in-container build downloads the full dependency tree (a few minutes); later builds reuse a cached Maven repo.

### 3. Local Docker (Ollama + PostgreSQL)

Runs entirely locally with Ollama — no cloud API keys required.

```bash
./run.sh local
```

First run pulls Ollama models (~7 GB), subsequent starts are instant.

### 4. Production Docker (bring your own LLM + PostgreSQL)

Set `LLM_API_KEY` and a real admin credential in `.env` (see [Authentication](#authentication) below — the bundled distribution refuses to boot without one).

```bash
echo "LLM_KIND=nvidia"        >> .env   # or openai, deepseek, mistral, groq, xai, openrouter
echo "LLM_API_KEY=nvapi-xxxx" >> .env
./run.sh prod
```

For the native image (faster startup, lower memory; the native compile is heavy):

```bash
./run.sh native
```

---

## Configuration

### Profiles

| Profile | LLM | Database | When |
|:--------|:----|:---------|:-----|
| **dev** | Ollama (Dev Services) | PostgreSQL (Dev Services) | `quarkus dev` |
| **prod** | NVIDIA NIM (primary) + Ollama (fallback) | External PostgreSQL | Docker / VM deployment |

A build without the `cognition-pgvector` capability is **database-free**: memory, skills, and episodic journals fall back to plain markdown files under `~/.qlawkus/`. `app/` ships with `cognition-pgvector` composed in; see [Composable Platform](#composable-platform) to build a markdown-only distribution instead.

### Authentication

Admin endpoints (`/api/admin/*`) and the console (`/console/*`) are `@Authenticated` (Basic auth, Argon2id-hashed credential). There is no insecure default in the bundled distribution — it refuses to boot without a real credential:

```bash
# Generate a PHC hash and put it in .env (or the encrypted keystore, see below)
printf '%s' 'your-password' | argon2 "$(openssl rand -hex 8)" -id -e -t 2 -m 16 -p 1
```

| Variable | Purpose |
|:---------|:--------|
| `QLAWKUS_ADMIN_PASSWORD_HASH` | Argon2id PHC hash for the `qlawkus` admin user (override the username with `QLAWKUS_ADMIN_USERNAME`) |

`%dev` keeps a known password (`qlawkus`) for local development only. In production, store the hash via the env var above or the encrypted secrets keystore (`PUT /api/admin/secrets`, alias `qlawkus.admin.password-hash`) — see [`site/content/secrets.adoc`](site/content/secrets.adoc).

### Environment Variables

All runtime config is via `.env` (gitignored) or shell exports. See [`.env.example`](.env.example) for the full list.

| Variable | Default | Purpose |
|:---------|:--------|:--------|
| `LLM_KIND` | `openai` (app default); the Compose files ship `nvidia` | Provider profile (`openai`, `nvidia`, `deepseek`, `mistral`, `groq`, `xai`, `openrouter`) — sets base URL + default chat/embedding models |
| `LLM_API_KEY` | — | API key for the chosen provider. Required for prod. NVIDIA keys: [build.nvidia.com](https://build.nvidia.com/) |
| `LLM_CHAT_MODEL` | provider default | Override the chat model (NVIDIA default `nvidia/nemotron-3-ultra-550b-a55b`) |
| `LLM_EMBEDDING_MODEL` | provider default | Override the embedding model (NVIDIA default `nvidia/nv-embedqa-e5-v5`) |
| `LLM_TIMEOUT` | `180s` | Primary model request timeout |
| `EMBEDDING_DIMENSION` | `1024` | Must match embedding model output |
| `OLLAMA_FALLBACK_MODEL` | `qwen3.5:4b` | Ollama fallback chat model |
| `OLLAMA_FALLBACK_EMBEDDING_MODEL` | `mxbai-embed-large` | Ollama fallback embedding model |
| `QLAWKUS_ADMIN_PASSWORD_HASH` | — (required in prod) | Argon2id hash for the admin user — see [Authentication](#authentication) |
| `POSTGRES_DB` | `qlawkus` | Database name (Docker Compose) |
| `POSTGRES_USER` | `qlawkus` | Database user (Docker Compose) |
| `POSTGRES_PASSWORD` | `qlawkus` | Database password (Docker Compose) |

> Messaging (`DISCORD_*`, `TELEGRAM_*`), voice (`GROQ_API_KEY`, `WHISPER_*`, `TTS_*`), Google OAuth (`GOOGLE_*`), the credential vault (`QLAWKUS_VAULT_*`), and agent memory (`AGENT_*`) are documented in [`.env.example`](.env.example). The full property surface — every `qlawkus.*` config root plus a curated `quarkus.*` allowlist — is generated from source into [`site/content/config-reference.adoc`](site/content/config-reference.adoc) and editable live from `/console/config`.

### Model Fallback & Circuit Breaker

The production setup uses NVIDIA NIM as the primary LLM with a local Ollama sidecar as automatic fallback:

- **Retry with backoff**: on transient errors (rate limits, timeouts), retries up to 3 times with configurable delays (default: 30s, 60s, 120s)
- **Circuit breaker**: after all retries fail, the circuit opens and all subsequent calls go directly to Ollama for a configurable period (default: 300s), then a half-open probe tests NVIDIA again
- **Applies to**: ChatModel, StreamingChatModel, and EmbeddingModel
- **Named models**: uses `@ModelName("primary")` for the primary and `@ModelName("fallback")` for the Ollama fallback, following quarkus-langchain4j named model pattern

| Config | Default | Purpose |
|:------|:--------|:--------|
| `qlawkus.model.fallback-enabled` | `true` | Enable fallback system |
| `qlawkus.model.retry-delays` | `30,60,120` | Retry delays in seconds |
| `qlawkus.model.circuit-breaker-timeout` | `300` | Seconds before half-open probe |

---

## Design Principles

- **Isolation by Design**: Single-tenant architecture — credentials and sandbox run in 100% isolated environment
- **Dynamic Cognition (SOUL)**: Mutable "Soul" (Core Identity + Current State + Mood) — the agent adapts its behavior in real-time
- **Triple Memory**: Working (session), Episodic (daily background journal), and Semantic (facts and preferences, RAG-injected every turn)
- **Skills (Procedural Memory)**: Reusable `SKILL.md` how-to procedures the agent authors, auto-distills, and curates itself; injected as an index each turn, loaded on demand. Pluggable storage (markdown / pgvector / hybrid), plus an optional remote registry client (search/install/publish) via the `qlawkus-tools-skill-hub` extension. See the [Skills guide](site/content/skills.adoc)
- **Autonomous Engineering**: Deep integration with `git` and `gh cli` for cloning, local testing, and autonomous code reviews
- **Composable Platform**: An `agent.yml` manifest decides which capabilities are built into the pom (`qlawkus-maven-plugin`, `mvn qlawkus:generate`) and which `RUN_TIME` properties are toggled; a database-free encrypted secrets keystore (PKCS12) backs credentials; an optional Qute + HTMX control-plane UI (`qlawkus-console`) drives onboarding, memory/skill management, a full configuration editor, and job scheduling. See [Composable Platform](#composable-platform) below
- **Lightweight**: Native compilation via GraalVM for ultra-fast startup, designed to run cheaply on small instances
- **Extensible**: Add tools by implementing `@QlawTool` beans — discovered automatically via CDI
- **Google Workspace**: 6 optional extensions (Auth, Calendar, Gmail, Drive, Sheets, Storage) with 17 AI tools, Web/Loopback OAuth2, and encrypted credential vault
- **Multi-Provider Messaging**: Provider-agnostic interface (Discord Gateway, Telegram polling/webhook; Slack & WhatsApp adapters) with per-provider formatting, chunking, auth allowlists, and a shared conversation memory across interfaces
- **Voice In/Out**: Agnostic speech-to-text (Whisper) and text-to-speech (local Piper, Groq Orpheus, ElevenLabs) with a per-language provider/fallback router

---

## Composable Platform

A single `agent.yml` manifest (`policy + exceptions`: a default posture — `enabled`/`disabled` — plus a list of capabilities that get the opposite effect) is the source of truth for two things:

- **`build-time`** — which optional extensions get compiled in. `qlawkus-maven-plugin` (`mvn qlawkus:generate`) reads the manifest, resolves each capability against every reactor module's self-announced `metadata.qlawkus.capability`, and reconciles the pom's `<dependencies>` before the build — no hand-edited pom, no dead code shipped for a capability you never listed.
- **`runtime`** — `RUN_TIME` property toggles, applied on the next restart via a `ConfigSource` layered above the baked-in defaults.

Because a native binary is frozen at build time and the runtime image ships no toolchain, the running app can never rebuild or restart itself. It only **stages** intent (`POST /api/admin/composition/manifest`, `PUT/POST/DELETE /api/admin/config-overrides`, `PUT/DELETE /api/admin/runtime-toggles`) behind Basic+Argon2id auth; an external builder with the toolchain does the actual rebuild, and an orchestrator (Compose / k8s / systemd) does the restart. Reference implementations for that loop live in [`examples/redeploy/`](examples/redeploy/).

Secrets (API keys, the admin password hash) live in a database-free, PKCS12 keystore (`~/.qlawkus/secrets.p12` by default), onboarded via `PUT /api/admin/secrets` or `keytool -importpass`, and outrank every other config source except `-D` system properties.

The optional `qlawkus-console` extension (server-rendered Qute + HTMX, no Node) puts all of this behind a UI at `/console`: a first-run onboarding wizard, management pages for memory/skills/cognition, a configuration editor covering every documented property, and a schedule page for the agent's background jobs. See [`site/content/composition.adoc`](site/content/composition.adoc) for the full design.

---

## System Architecture

```
┌────────────────────────────────────────────────────────┐
│ Interface Layer (Single-User)                          │
│ POST /api/chat (SSE) · Discord/Telegram · /console UI   │
│ Basic + Argon2id Auth                                   │
└────────────────────────┬───────────────────────────────┘
                         │
┌────────────────────────▼───────────────────────────────┐
│ Brain (ReAct Orchestrator) + SOUL Engine                │
│ Builds dynamic System Prompt: Soul + Owner + Skills idx │
└────────────┬────────────────────────────┬──────────────┘
             │                            │
┌────────────▼──────────────┐  ┌──────────▼───────────────┐
│ Cognition & Memory        │  │ ToolRegistry (CDI)       │
│ Working: session log      │  │ Life: Google Workspace   │
│ Episodic: daily journal   │  │ Career: Brag Docs        │
│ Semantic: facts (RAG)     │  │ Engineering: Git / CLI   │
│ Skills: procedural memory │  │ Skills: viewSkill, etc.  │
│ markdown (default) /      │  └──────────┬───────────────┘
│ pgvector / hybrid backend │             │
└───────────────────────────┘             │
┌────────────────────────────────┼─────────────────────────┐
│                                 │                         │
┌────────▼────────┐  ┌───────────▼───────────┐  ┌──────────▼──────────┐
│ Integrations     │  │ Execution              │  │ Composable Platform │
│ OAuth2           │  │ Sandbox                │  │ agent.yml manifest  │
│ GitHub SDK       │  │ Interactive Shell       │  │ Secrets keystore    │
│ Google Workspace │  │ ProcessManager          │  │ /console control    │
└──────────────────┘  └─────────────────────────┘  │ plane (Qute+HTMX)   │
                                                     └──────────────────────┘
                                          │
┌────────────────────────▼────────────────────────────────┐
│ LLM Engine (Ollama in Dev / NVIDIA NIM in Prod)          │
│ Retry + circuit breaker → Ollama fallback                │
└─────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
qlawkus/                              # Parent POM (semantic versioning)
├── composition-model/                # Quarkus-free: agent.yml schema, parser, policy+exceptions logic
├── composition-maven-plugin/         # qlawkus-maven-plugin: manifest -> pom, before the build
├── client/                           # Quarkus extension (core agent, cognition, REST, tools)
│   ├── deployment/                   # Build-time processor (BuildSteps, tool registration)
│   │   └── src/test/                 # Extension build-time tests (mocked LLM via WireMock)
│   └── runtime/                      # Runtime module
│       └── src/main/java/dev/omatheusmesmo/qlawkus/
│           ├── agent/                # AgentService, StartupThoughtObserver, AgentLogInterceptor
│           ├── cognition/            # Soul, SoulEngine, background jobs (review/curation/consolidation)
│           ├── compose/              # Composition + config-overrides admin services
│           ├── config/                # @ConfigMapping roots, runtime-toggle writer
│           ├── secrets/              # PKCS12 keystore reader/writer, SecretPropertyCatalog
│           ├── security/             # Argon2id admin identity provider
│           ├── skill/                # Skill SPI, markdown store, lifecycle
│           ├── rest/                 # ApiResource (SSE chat) + admin REST resources
│           ├── store/                # Store SPIs (Fact/WorkingMemory/Episodic/Soul/UserProfile/Skill)
│           │   └── markdown/         # Default file-based backends (no database)
│           └── tool/                 # @QlawTool qualifier, QlawToolProvider, sandboxed shell
├── cognition-pgvector/               # Optional extension: Postgres/pgvector backend + Flyway migrations
│                                     # (store/pg/*, reconcile/migrate). Absent = database-free build.
├── console/                          # Optional extension: server-rendered control-plane UI (/console)
│                                     # Qute + HTMX, no Node. Onboarding, memory/skills/cognition,
│                                     # config editor, schedule page.
├── tools/                            # Optional tool extensions
│   ├── qlawkus-tools-google-auth/    # Web/Loopback OAuth2 + CredentialVault (Argon2id + AES-256-GCM)
│   ├── qlawkus-tools-google-calendar/# listEvents, createEvent, checkAvailability, suggestFocusTime
│   ├── qlawkus-tools-google-gmail/   # listEmails, sendEmail, searchEmails
│   ├── qlawkus-tools-google-drive/   # listFiles, uploadFile, downloadFile, shareFile
│   ├── qlawkus-tools-google-sheets/  # readSheet, writeSheet, updateCell
│   ├── qlawkus-tools-google-storage/ # listBuckets, uploadObject, downloadObject
│   ├── qlawkus-tools-brag/           # Career brag documents
│   └── qlawkus-tools-skill-hub/      # Remote skill-registry client (search/install/publish)
├── messaging/                        # Provider-agnostic messaging (core + tts/transcription)
│   ├── discord/                      # Discord Gateway adapter (Discord4J) + slash commands
│   ├── telegram/                     # Telegram adapter (long-polling / webhook) + voice
│   ├── slack/                        # Slack adapter (Event Subscriptions)
│   └── whatsapp/                     # WhatsApp adapter (Cloud API webhook)
├── app/                               # Deployable application (packaging = quarkus, reference distribution)
│   └── src/main/
│       ├── docker/                   # Dockerfiles: COPY-target (.jvm/.native) + in-container
│       │                             # build (.jvm-build/.native-build) + entrypoint.sh
│       └── resources/                # application.properties (profile overrides)
├── examples/redeploy/                # Reference build-and-redeploy loop (compose/k8s/systemd)
├── testing-internal/                 # Shared test utilities
├── integration-tests/                # Per-feature Quarkus apps (smoke, cognition, markdown-only,
│                                     # google, terminal, brag, code-review, messaging, console)
├── site/                             # Roq docs site source (site/content/*.adoc)
├── docs/                             # Generated Antora mirror of site/ — never edit directly
├── run.sh                            # Build + run the stack: ./run.sh <local|prod|native>
├── docker-compose.yml                # Prod: bring-your-own LLM + PostgreSQL + TTS
├── docker-compose.local.yml          # Local: Ollama + PostgreSQL
└── .env.example                      # Environment template
```

### Adding Custom Tools

Create a CDI bean annotated with `@QlawTool` — it's auto-discovered by the agent:

```java
@QlawTool
@ApplicationScoped
public class MyTool {
    @Tool("Does something useful")
    public String doSomething(String input) {
        return "result: " + input;
    }
}
```

---

## Stack

| Component | Extension / Technology | Primary Use |
|:----------|:----------------------|:------------|
| **LLM Core** | `quarkus-langchain4j-ollama` (Dev) / `openai` (Prod) | ReAct Engine |
| **Embeddings** | `quarkus-langchain4j-pgvector` (optional, in `cognition-pgvector`) / in-process `InMemoryEmbeddingStore` (default) | Semantic memory (RAG) |
| **REST** | `quarkus-rest-jackson` | JSON + SSE chat endpoint |
| **Control-plane UI** | `quarkus-qute` + HTMX (in the optional `console` extension) | `/console`: onboarding, management, config editor, scheduling |
| **Scheduling** | `quarkus-scheduler` | Nightly memory/skill jobs, all listed live at `/console/schedule` |
| **Data** | `hibernate-orm-panache`, `jdbc-postgresql` (in `cognition-pgvector`; omit it for a database-free, markdown-only build) | Persona, profile, history, episodic, embeddings |
| **Migrations** | `quarkus-flyway` (in `cognition-pgvector`) | Schema evolution |
| **Security** | `elytron-security-properties-file` + Argon2id password hashing, `hibernate-validator` | Basic Auth (fail-closed, no insecure default), input validation |
| **Secrets** | Plain JDK `KeyStore` API (PKCS12) | Database-free encrypted credential store, keytool-compatible |
| **Composition** | `qlawkus-maven-plugin` (Maven Model API) | `agent.yml` manifest → pom `<dependencies>`, before the build |
| **Resilience** | `smallrye-fault-tolerance` | LLM call retry / timeout |
| **Messaging** | `qlawkus-messaging` (+ discord/telegram/slack/whatsapp) | Multi-provider chat, voice, formatting, chunking |
| **Voice** | `whisper` (STT) + OpenAI-compatible / ElevenLabs (TTS) | Speech in/out, agnostic router |
| **Google Auth** | `qlawkus-tools-google-auth` | Web/Loopback OAuth2 + CredentialVault |
| **Google Calendar** | `qlawkus-tools-google-calendar` | listEvents, createEvent, checkAvailability, suggestFocusTime |
| **Google Gmail** | `qlawkus-tools-google-gmail` | listEmails, sendEmail, searchEmails |
| **Google Drive** | `qlawkus-tools-google-drive` | listFiles, uploadFile, downloadFile, shareFile |
| **Google Sheets** | `qlawkus-tools-google-sheets` | readSheet, writeSheet, updateCell |
| **Google Storage** | `qlawkus-tools-google-storage` | listBuckets, uploadObject, downloadObject |
| **Skill Hub** | `qlawkus-tools-skill-hub` | Remote skill-registry client (search/install/publish) |

---

## Docker Compose

### Production (`docker-compose.yml`)

- **Qlawkus** — JVM container (or native with `--profile native`)
- **PostgreSQL 17 + pgvector** — persistent storage
- **Ollama** (+ `ollama-init`) — local LLM fallback
- **tts** (+ `tts-init`) — openedai-speech (Piper) for local text-to-speech, opt-in via `TTS_ENABLED`

Requires `LLM_API_KEY` (provider chosen with `LLM_KIND`, shipped as `nvidia`) and a real admin credential (see [Authentication](#authentication)). Exposes host port `8742` → container `8080` (the Google OAuth redirect URI must match this host port).

### Local (`docker-compose.local.yml`)

- **Qlawkus** — JVM container
- **PostgreSQL 17 + pgvector** — persistent storage
- **Ollama** — local LLM inference
- **ollama-init** — one-shot container that pulls models on first start

No API keys needed. Exposes host port `8742` → container `8080` (app).

### Volume Management

Ollama models are large (~7 GB). To avoid re-downloading:

```bash
# Stop and remove containers — keeps all volumes (models + data)
./run.sh local down

# Reset database only — keeps Ollama models
./run.sh local down && docker volume rm qlawkus_pgdata

# Reset everything — WARNING: models must be re-pulled (~25 min)
docker compose -f docker-compose.local.yml down -v
```

> **Dev mode** (`quarkus dev`) does NOT use Docker Compose — Dev Services auto-provisions containers as needed.

---

## API

| Endpoint | Method | Auth | Description |
|:---------|:-------|:-----|:------------|
| `/api/chat`, `/api/chat/sync` | POST | Basic | Send message, receive an SSE stream (or a single sync reply) |
| `/api/admin/memory` | GET / DELETE | Basic | Memory summary / purge (working, semantic, journals) |
| `/api/admin/memory/{review,curate,consolidate}` | POST | Basic | Trigger a background memory job now |
| `/api/admin/skills` | GET / DELETE | Basic | Skill index / purge; `/{name}/pin`, `/curate`, `/lifecycle` |
| `/api/admin/secrets` | PUT / GET / DELETE | Basic | Onboard, list, or remove a keystore secret |
| `/api/admin/composition` | POST / GET / DELETE | Basic | Stage, inspect, or discard the `agent.yml` manifest |
| `/api/admin/config-overrides` | PUT / POST / GET / DELETE | Basic | Stage a `BUILD_TIME` property value, pending rebuild |
| `/api/admin/runtime-toggles` | PUT / GET / DELETE | Basic | Set a `RUN_TIME` property value, applied on next restart |
| `/api/admin/cognition/{reconcile,migrate}` | POST | Basic | Backend reconciliation between markdown and pgvector (hybrid) |
| `/console` | GET | Basic | The control-plane UI: onboarding, memory/skills/cognition, config editor, schedule |

The full admin surface — including per-property config editing and the `agent.yml` schema — is documented in [`site/content/composition.adoc`](site/content/composition.adoc) and generated in full into [`site/content/config-reference.adoc`](site/content/config-reference.adoc).

**Example:**

```bash
curl -X POST http://localhost:8080/api/chat \
  -u qlawkus:qlawkus \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello, who are you?"}'
```

---

## Documentation

The docs site lives in [`site/content/`](site/content/) (Roq/AsciiDoc) — `index`, `quickstart`, `architecture`, `composition`, `config-reference`, `memory`, `skills`, `secrets`, `messaging`, `voice`, `google-workspace`, `storage`. `docs/` is a generated Antora mirror; edit only `site/content/`.

## Reference Links

- [Quarkus LangChain4j Documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/index.html)
- [Guide: Function Calling](https://docs.quarkiverse.io/quarkus-langchain4j/dev/quickstart-function-calling.html)
- [Guide: Simple RAG](https://docs.quarkiverse.io/quarkus-langchain4j/dev/quickstart-rag.html)
- [Guide: Guardrails](https://docs.quarkiverse.io/quarkus-langchain4j/dev/guardrails.html)
- [Guide: Fault Tolerance](https://docs.quarkiverse.io/quarkus-langchain4j/dev/guide-fault-tolerance.html)
- [Guide: Ollama Models](https://docs.quarkiverse.io/quarkus-langchain4j/dev/guide-ollama.html)
- [Dev Services — Automatic Testcontainers](https://quarkus.io/guides/dev-services)
- [SmallRye Fault Tolerance](https://quarkus.io/guides/smallrye-fault-tolerance)
- [Writing Your Own Extension](https://quarkus.io/guides/writing-extensions)

---

## Roadmap

> See [Issues](https://github.com/omatheusmesmo/Qlawkus/issues) for detailed task tracking

| Milestone | Description | Status |
|:----------|:------------|:-------|
| **M1** | Foundation, SOUL & Single-User Security | Done |
| **M2** | Cognition (Memory Engine) | Done |
| **M2.5** | Modular Architecture & Docker Distribution | Done |
| **M3** | Google Productivity Integration (Calendar, Mail, Drive, Sheets, Storage) | Done |
| **M4** | Terminal Capabilities (ShellTool, InteractiveShellTool, FileTool) | Done |
| **M5** | Career Engine (Brag Document) | Done |
| **M6** | Sandbox & Code Review | Done |
| **M7** | Messaging Interface (Multi-Provider: Discord, Telegram, Slack, WhatsApp) + Voice | Done |
| **M8** | Composable Agent Platform (manifest→pom, no-DB secrets, build-and-redeploy loop, control-plane UI, Dev UI) | In Progress — manifest/composition, secrets, build-and-redeploy loop, and the control-plane UI (onboarding, management, config editor, scheduling) are shipped; Dev UI cards and E2E QA remain |
| **M9** | Observability & Native Build | Pending |
| **M10** | Autonomy & Job Orchestration | Pending |

---

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

---

> Built with Quarkus LangChain4j
