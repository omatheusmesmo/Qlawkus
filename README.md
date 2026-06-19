# Qlawkus

> Autonomous Personal Engineering Agent built with Quarkus LangChain4j

[![Status: Work in Progress](https://img.shields.io/badge/Status-Work%20in%20Progress-yellow?style=for-the-badge)](https://github.com/omatheusmesmo/Qlawkus/issues)

A single-user, production-ready autonomous agent with dynamic cognition, triple memory, Google Workspace integration, multi-provider messaging (Discord, Telegram) with voice in/out, and self-improvement capabilities.

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

Edit `.env` and set `NVIDIA_AI_API_KEY` if you plan to run in production mode.

### 2. Dev Mode (recommended for development)

Uses Dev Services — auto-provisions Ollama + PostgreSQL via Testcontainers. No `.env` needed.

```bash
mvn install -pl client -am -DskipTests
cd app && mvn quarkus:dev
```

The agent starts at `http://localhost:8080`. Chat endpoint: `POST /api/chat`.

> **Note:** `client/` is a Quarkus extension — live reload only applies to `app/`. Changes to `client/` require `mvn install -pl client -am` + restart.

### 3. Local Docker (Ollama + PostgreSQL)

Runs entirely locally with Ollama — no cloud API keys required.

```bash
docker compose -f docker-compose.local.yml up --build
```

First run pulls Ollama models (~7 GB), subsequent starts are instant.

### 4. Production Docker (NVIDIA NIM + PostgreSQL)

Uses NVIDIA NIM API for LLM inference. Requires `NVIDIA_AI_API_KEY`.

```bash
export NVIDIA_AI_API_KEY=nvapi-xxxx
docker compose up --build
```

For native image (faster startup, lower memory):

```bash
docker compose --profile native up --build
```

---

## Configuration

### Profiles

| Profile | LLM | Database | When |
|:--------|:----|:---------|:-----|
| **dev** | Ollama (Dev Services) | PostgreSQL (Dev Services) | `quarkus dev` |
| **prod** | NVIDIA NIM (primary) + Ollama (fallback) | External PostgreSQL | Docker / VM deployment |

### Environment Variables

All runtime config is via `.env` (gitignored) or shell exports. See [`.env.example`](.env.example) for the full list.

| Variable | Default | Purpose |
|:---------|:--------|:--------|
| `NVIDIA_AI_API_KEY` | — | Required for prod. Get yours at [build.nvidia.com](https://build.nvidia.com/) |
| `NVIDIA_CHAT_MODEL` | — | Chat model ID on NVIDIA NIM (e.g. `z-ai/glm-5.1`) |
| `NVIDIA_EMBEDDING_MODEL` | `nvidia/nv-embedqa-e5-v5` | Embedding model (1024 dims) |
| `EMBEDDING_DIMENSION` | `1024` | Must match embedding model output |
| `OLLAMA_FALLBACK_MODEL` | `gemma4:e2b` | Ollama fallback chat model |
| `OLLAMA_FALLBACK_EMBEDDING_MODEL` | `mxbai-embed-large` | Ollama fallback embedding model |
| `API_USER_PASSWORD` | `qlawkus` | Basic auth password for `qlawkus` user |
| `POSTGRES_DB` | `qlawkus` | Database name (Docker Compose) |
| `POSTGRES_USER` | `qlawkus` | Database user (Docker Compose) |
| `POSTGRES_PASSWORD` | `qlawkus` | Database password (Docker Compose) |

> Messaging (`DISCORD_*`, `TELEGRAM_*`), voice (`GROQ_API_KEY`, `WHISPER_*`, `TTS_*`), Google OAuth (`GOOGLE_*`), the credential vault (`QLAWKUS_VAULT_*`), and agent memory (`AGENT_*`) are documented in [`.env.example`](.env.example).

### Model Fallback & Circuit Breaker

The production setup uses NVIDIA NIM as the primary LLM with a local Ollama sidecar as automatic fallback:

- **Retry with backoff**: on transient errors (rate limits, timeouts), retries up to 3 times with configurable delays (default: 30s, 60s, 120s)
- **Circuit breaker**: after all retries fail, the circuit opens and all subsequent calls go directly to Ollama for a configurable period (default: 300s), then a half-open probe tests NVIDIA again
- **Applies to**: ChatModel, StreamingChatModel, and EmbeddingModel
- **Named models**: uses `@ModelName("nvidia")` for the primary and `@ModelName("ollama-fallback")` for the fallback, following quarkus-langchain4j named model pattern

| Config | Default | Purpose |
|:------|:--------|:--------|
| `qlawkus.model.fallback-enabled` | `true` | Enable fallback system |
| `qlawkus.model.retry-delays` | `30,60,120` | Retry delays in seconds |
| `qlawkus.model.circuit-breaker-timeout` | `300` | Seconds before half-open probe |

---

## Design Principles

- **Isolation by Design**: Single-tenant architecture — credentials and sandbox run in 100% isolated environment
- **Dynamic Cognition (SOUL)**: Mutable "Soul" (Core Identity + Current State + Mood) — the agent adapts its behavior in real-time
- **Triple Memory**: Working (session), Episodic (daily background journal), and Semantic (facts and preferences via pgvector)
- **Autonomous Engineering**: Deep integration with `git` and `gh cli` for cloning, local testing, and autonomous code reviews
- **Safe Self-Improvement**: The agent writes, validates (AST), and compiles Groovy scripts at runtime to create new skills, gated by strict Quarkus Security policies
- **Lightweight**: Native compilation via GraalVM for ultra-fast startup, designed to run cheaply on small instances
- **Extensible**: Add tools by implementing `@QlawTool` beans — discovered automatically via CDI
- **Google Workspace**: 6 optional extensions (Auth, Calendar, Gmail, Drive, Sheets, Storage) with 17 AI tools, Web/Loopback OAuth2, and encrypted credential vault
- **Multi-Provider Messaging**: Provider-agnostic interface (Discord Gateway, Telegram polling/webhook; Slack & WhatsApp adapters) with per-provider formatting, chunking, auth allowlists, and a shared conversation memory across interfaces
- **Voice In/Out**: Agnostic speech-to-text (Whisper) and text-to-speech (local Piper, Groq Orpheus, ElevenLabs) with a per-language provider/fallback router

---

## System Architecture

```
┌────────────────────────────────────────────────────────┐
│ Interface Layer (Single-User)                          │
│ POST /api/chat (SSE) · Basic Auth                      │
└────────────────────────┬───────────────────────────────┘
                         │
┌────────────────────────▼───────────────────────────────┐
│ Brain (ReAct Orchestrator) + SOUL Engine                │
│ Builds dynamic System Prompt based on "State"          │
└────────────┬────────────────────────────┬──────────────┘
             │                            │
┌────────────▼──────────────┐  ┌──────────▼───────────────┐
│ Cognition & Memory        │  │ ToolRegistry (CDI)       │
│ Working: PG (Session)     │  │ Life: Google Workspace │
│ Episodic: Daily Job       │  │ Career: Brag Docs        │
│ Semantic: pgvector        │  │ Engineering: Git / CLI   │
└───────────────────────────┘  │ Dynamic: Groovy Engine   │
                               └──────────┬───────────────┘
                                          │
┌───────────────────────────────┼─────────────────────┐
│                               │                     │
┌────────▼────────┐  ┌────────────────────▼──────────┐  ┌────────▼────────────┐
│ Integrations    │  │ Execution                      │  │ Self-Improvement    │
│ OAuth2          │  │ Sandbox                        │  │ CodeGenerator       │
│ GitHub SDK      │  │ Test Runner                    │  │ Groovy Compiler     │
│ Google Workspace │  │ ProcessManager                 │  │ AST Validator       │
└─────────────────┘  └───────────────────────────────┘  └─────────────────────┘
                                          │
┌────────────────────────▼────────────────────────────────┐
│ LLM Engine (Ollama in Dev / NVIDIA NIM in Prod)        │
└─────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
qlawkus/                          # Parent POM (semantic versioning)
├── tools/ # Quarkus extension modules (optional tool integrations)
│ ├── qlawkus-tools-google-auth/ # Web/Loopback OAuth2 + CredentialVault (Argon2id + AES-256-GCM)
│ ├── qlawkus-tools-google-calendar/ # listEvents, createEvent, checkAvailability, suggestFocusTime
│ ├── qlawkus-tools-google-gmail/ # listEmails, sendEmail, searchEmails
│ ├── qlawkus-tools-google-drive/ # listFiles, uploadFile, downloadFile, shareFile
│ ├── qlawkus-tools-google-sheets/ # readSheet, writeSheet, updateCell
│ └── qlawkus-tools-google-storage/ # listBuckets, uploadObject, downloadObject
├── messaging/ # Provider-agnostic messaging (core + tts/transcription)
│ ├── discord/ # Discord Gateway adapter (Discord4J) + slash commands
│ ├── telegram/ # Telegram adapter (long-polling / webhook) + voice
│ ├── slack/ # Slack adapter (Event Subscriptions)
│ └── whatsapp/ # WhatsApp adapter (Cloud API webhook)
├── client/ # Quarkus extension (library)
│   ├── deployment/               # Build-time processor (BuildSteps, tool registration)
│   │   └── src/test/             # Extension build-time tests (mocked LLM via WireMock)
│   └── runtime/                  # Runtime module
│       └── src/main/java/dev/omatheusmesmo/qlawkus/
│           ├── agent/            # AgentService, StartupThoughtObserver, AgentLogInterceptor
│           ├── cognition/        # Soul, SoulEngine, Mood, EpisodicConsolidatorJob
│           │                     # SemanticExtractorObserver, UpdateSelfStateTool, SearchMemoriesTool
│           ├── dto/              # ChatRequest, MemorySummary, JournalSummary
│           ├── rest/             # ApiResource (SSE chat), AdminResource
│           ├── store/            # FactStore, WorkingMemoryStore, EpisodicStore (interfaces)
│           │   └── pg/           # PostgreSQL implementations + ChatMessageEntity, Journal
│           └── tool/             # @QlawTool qualifier, QlawToolProvider, QlawToolProviderSupplier
├── app/                          # Deployable application (packaging = quarkus)
│   └── src/main/
│       ├── docker/               # Dockerfiles (JVM + native) + entrypoint.sh
│       └── resources/            # application.properties (profile overrides)
├── integration-tests/ # Consumer experience tests (WireMock + real LLM via NVIDIA/Ollama)
├── docker-compose.yml            # Prod: NVIDIA NIM + PostgreSQL
├── docker-compose.local.yml      # Local: Ollama + PostgreSQL
└── .env.example                  # Environment template
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
| **Embeddings** | `quarkus-langchain4j-pgvector` | Semantic memory (RAG) |
| **REST** | `quarkus-rest-jackson` | JSON + SSE chat endpoint |
| **Data** | `hibernate-orm-panache`, `jdbc-postgresql` | SOUL, History, Episodic |
| **Migrations** | `quarkus-flyway` | Schema evolution |
| **Security** | `elytron-security-properties-file`, `hibernate-validator` | Basic Auth, input validation |
| **Resilience** | `smallrye-fault-tolerance` | LLM call retry / timeout |
| **Messaging** | `qlawkus-messaging` (+ discord/telegram/slack/whatsapp) | Multi-provider chat, voice, formatting, chunking |
| **Voice** | `whisper` (STT) + OpenAI-compatible / ElevenLabs (TTS) | Speech in/out, agnostic router |
| **Google Auth** | `qlawkus-tools-google-auth` | Web/Loopback OAuth2 + CredentialVault |
| **Google Calendar** | `qlawkus-tools-google-calendar` | listEvents, createEvent, checkAvailability, suggestFocusTime |
| **Google Gmail** | `qlawkus-tools-google-gmail` | listEmails, sendEmail, searchEmails |
| **Google Drive** | `qlawkus-tools-google-drive` | listFiles, uploadFile, downloadFile, shareFile |
| **Google Sheets** | `qlawkus-tools-google-sheets` | readSheet, writeSheet, updateCell |
| **Google Storage** | `qlawkus-tools-google-storage` | listBuckets, uploadObject, downloadObject |

---

## Docker Compose

### Production (`docker-compose.yml`)

- **Qlawkus** — JVM container (or native with `--profile native`)
- **PostgreSQL 17 + pgvector** — persistent storage
- **Ollama** (+ `ollama-init`) — local LLM fallback
- **tts** (+ `tts-init`) — openedai-speech (Piper) for local text-to-speech, opt-in via `TTS_ENABLED`

Requires `NVIDIA_AI_API_KEY`. Exposes host port `8742` → container `8080` (the Google OAuth redirect URI must match this host port).

### Local (`docker-compose.local.yml`)

- **Qlawkus** — JVM container
- **PostgreSQL 17 + pgvector** — persistent storage
- **Ollama** — local LLM inference
- **ollama-init** — one-shot container that pulls models on first start

No API keys needed. Exposes ports `8080` (app) and `11434` (Ollama API).

### Volume Management

Ollama models are large (~7 GB). To avoid re-downloading:

```bash
# Stop and remove containers — keeps all volumes (models + data)
docker compose -f docker-compose.local.yml down

# Reset database only — keeps Ollama models
docker compose -f docker-compose.local.yml down && docker volume rm quarkusclaw_pgdata

# Reset everything — WARNING: models must be re-pulled (~25 min)
docker compose -f docker-compose.local.yml down -v
```

> **Dev mode** (`quarkus dev`) does NOT use Docker Compose — Dev Services auto-provisions containers as needed.

---

## API

| Endpoint | Method | Auth | Description |
|:---------|:-------|:-----|:------------|
| `/api/chat` | POST | Basic (`qlawkus`) | Send message, receive SSE stream |
| `/api/admin/memory` | GET | Basic (`qlawkus`) | Memory summary (working + semantic) |
| `/api/admin/journal` | GET | Basic (`qlawkus`) | Episodic journal entries |

**Example:**

```bash
curl -X POST http://localhost:8080/api/chat \
  -u qlawkus:qlawkus \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello, who are you?"}'
```

---

## Reference Links

- [Quarkus LangChain4j Documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/index.html)
- [Guide: Function Calling](https://docs.quarkiverse.io/quarkus-langchain4j/dev/quickstart-function-calling.html)
- [Guide: Simple RAG](https://docs.quarkiverse.io/quarkus-langchain4j/dev/quickstart-rag.html)
- [Guide: Guardrails](https://docs.quarkiverse.io/quarkus-langchain4j/dev/guardrails.html)
- [Guide: Fault Tolerance](https://docs.quarkiverse.io/quarkus-langchain4j/dev/guide-fault-tolerance.html)
- [Guide: Ollama Models](https://docs.quarkiverse.io/quarkus-langchain4j/dev/guide-ollama.html)
- [Dev Services — Automatic Testcontainers](https://quarkus.io/guides/dev-services)
- [SmallRye Fault Tolerance](https://quarkus.io/guides/smallrye-fault-tolerance)
- [GroovyClassLoader — Dynamic Compilation](https://docs.groovy-lang.org/latest/html/documentation/guide-integrating.html)

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
| **M8** | Self-Improvement Engine (Groovy) | Pending |
| **M9** | Observability & Native Build | Pending |
| **M10** | Autonomy & Job Orchestration | Pending |

---

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

---

> Built with Quarkus LangChain4j
