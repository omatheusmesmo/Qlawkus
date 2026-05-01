# Qlawkus

> Autonomous Personal Engineering Agent built with Quarkus LangChain4j

[![Status: Work in Progress](https://img.shields.io/badge/Status-Work%20in%20Progress-yellow?style=for-the-badge)](https://github.com/omatheusmesmo/Qlawkus/issues)

A single-user, production-ready autonomous agent with dynamic cognition, triple memory, and self-improvement capabilities.

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
| **default** | Ollama (Dev Services) | PostgreSQL (Dev Services) | `quarkus dev` |
| **dev** | Ollama (Dev Services) | PostgreSQL (Dev Services) | `quarkus dev` |
| **prod** | NVIDIA NIM (OpenAI-compatible) | External PostgreSQL | Docker / VM deployment |

### Environment Variables

All runtime config is via `.env` (gitignored) or shell exports. See [`.env.example`](.env.example) for the full list.

| Variable | Default | Purpose |
|:---------|:--------|:--------|
| `NVIDIA_AI_API_KEY` | — | Required for prod. Get yours at [build.nvidia.com](https://build.nvidia.com/) |
| `NVIDIA_CHAT_MODEL` | `z-ai/glm-5.1` | Chat model on NVIDIA NIM |
| `NVIDIA_EMBEDDING_MODEL` | `nvidia/nv-embedqa-e5-v5` | Embedding model (1024 dims) |
| `EMBEDDING_DIMENSION` | `1024` | Must match embedding model output |
| `OLLAMA_MODEL` | `gemma4:e2b` | Ollama chat model (local only) |
| `OLLAMA_EMBEDDING_MODEL` | `mxbai-embed-large` | Ollama embedding model (local only) |
| `API_USER_PASSWORD` | `qlawkus` | Basic auth password for `qlawkus` user |
| `POSTGRES_DB` | `qlawkus` | Database name (Docker Compose) |
| `POSTGRES_USER` | `quarkus` | Database user (Docker Compose) |
| `POSTGRES_PASSWORD` | `quarkus` | Database password (Docker Compose) |

---

## Design Principles

- **Isolation by Design**: Single-tenant architecture — credentials and sandbox run in 100% isolated environment
- **Dynamic Cognition (SOUL)**: Mutable "Soul" (Core Identity + Current State + Mood) — the agent adapts its behavior in real-time
- **Triple Memory**: Working (session), Episodic (daily background journal), and Semantic (facts and preferences via pgvector)
- **Autonomous Engineering**: Deep integration with `git` and `gh cli` for cloning, local testing, and autonomous code reviews
- **Safe Self-Improvement**: The agent writes, validates (AST), and compiles Groovy scripts at runtime to create new skills, gated by strict Quarkus Security policies
- **Lightweight**: Native compilation via GraalVM for ultra-fast startup, designed to run cheaply on small instances
- **Extensible**: Add tools by implementing `@ClawTool` beans — discovered automatically via CDI

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
│ Working: PG (Session)     │  │ Life: Google Calendar    │
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
│ Google SDK      │  │ ProcessManager                 │  │ AST Validator       │
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
├── client/                       # Quarkus extension (library)
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
│           └── tool/             # @ClawTool qualifier, ClawToolProvider, ClawToolProviderSupplier
├── app/                          # Deployable application (packaging = quarkus)
│   └── src/main/
│       ├── docker/               # Dockerfiles (JVM + native) + entrypoint.sh
│       └── resources/            # application.properties (profile overrides)
├── integration-tests/            # Consumer experience tests (real LLM via Ollama Dev Services)
├── docker-compose.yml            # Prod: NVIDIA NIM + PostgreSQL
├── docker-compose.local.yml      # Local: Ollama + PostgreSQL
└── .env.example                  # Environment template
```

### Adding Custom Tools

Create a CDI bean annotated with `@ClawTool` — it's auto-discovered by the agent:

```java
@ClawTool
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

---

## Docker Compose

### Production (`docker-compose.yml`)

- **Qlawkus** — JVM container (or native with `--profile native`)
- **PostgreSQL 17 + pgvector** — persistent storage

Requires `NVIDIA_AI_API_KEY`. Exposes port `8080`.

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
| **M3** | Productivity Integration (Google Calendar) | Pending |
| **M4** | Engineering Integration (GitHub & Git) | Pending |
| **M5** | Career Engine (Brag Document) | Pending |
| **M6** | Sandbox & Code Review | Pending |
| **M7** | Self-Improvement Engine (Groovy) | Pending |
| **M8** | Messaging Interface (Telegram) | Pending |
| **M9** | Observability & Native Build | Pending |
| **M10** | Autonomy & Job Orchestration | Pending |

---

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

---

> Built with Quarkus LangChain4j
