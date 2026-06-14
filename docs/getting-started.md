# Getting Started

## Prerequisites

- JDK 25 and Maven (the build uses the Quarkus 3.x platform).
- Docker (for Dev Services in dev mode, and for the containerized run modes).
- An `NVIDIA_AI_API_KEY` (from [build.nvidia.com](https://build.nvidia.com/)) for the production profile. Not needed for dev mode or local Docker, which use Ollama.

## Important: this is an extension + app monorepo

`client/` (and `tools/`, `messaging/`) are Quarkus **extensions**, not runnable apps. Only `app/` runs. Quarkus live reload applies to `app/` only, so **any change under `client/`, `tools/`, or `messaging/` requires reinstalling the extension and restarting**:

```bash
mvn install -pl client -am -DskipTests   # rebuild the changed extension(s)
cd app && mvn quarkus:dev                # restart
```

## 1. Dev mode (recommended for development)

Dev Services auto-provision PostgreSQL (pgvector) and Ollama via Testcontainers. No `.env` required.

```bash
mvn install -pl client -am -DskipTests
cd app && mvn quarkus:dev
```

The agent starts on `http://localhost:8080`. In dev, Flyway `clean-at-start` wipes all databases on every restart.

## 2. Local Docker (Ollama + PostgreSQL)

Self-contained, no external API key. Uses `docker-compose.local.yml`.

```bash
mvn clean install -DskipTests        # produces app/target/quarkus-app (the image copies this)
docker compose -f docker-compose.local.yml up --build
```

Exposes `http://localhost:8080`. An `ollama-init` one-shot container pulls the models on first start.

## 3. Production Docker (NVIDIA NIM + PostgreSQL)

Uses `docker-compose.yml`. Requires `NVIDIA_AI_API_KEY`.

```bash
export NVIDIA_AI_API_KEY=nvapi-xxxx
mvn clean install -DskipTests        # rebuild artifacts FIRST; the Dockerfile only copies them
docker compose up -d --build
```

The container maps host port **8742** to container `8080` (the Google OAuth redirect URI expects 8742). For the native image (faster startup, lower memory):

```bash
mvn clean install -DskipTests -Dnative
docker compose --profile native up -d --build
```

> The JVM/native Dockerfiles `COPY target/quarkus-app/...`; they do **not** compile inside the image. Always run `mvn install`/`package` before `docker compose --build`, or you will containerize a stale jar.

## Authentication and endpoints

All HTTP endpoints use Basic auth (`quarkus` / `qlawkus` by default, override with `API_USER_PASSWORD`).

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/chat` | POST | Chat, Server-Sent Events stream |
| `/api/chat/sync` | POST | Chat, blocking plain-text reply |
| `/api/admin/memory` | GET / DELETE | Memory summary / purge (`?all=true`, `?source=`, `?includeJournals=true`) |
| `/api/admin/memory/review` | POST | Run semantic dedup of facts now |
| `/api/admin/memory/curate` | POST | Refresh the owner profile from facts now |

```bash
curl -X POST http://localhost:8742/api/chat/sync -u qlawkus:qlawkus \
  -H 'Content-Type: application/json' -d '{"message":"Remember that my GitHub handle is omatheusmesmo."}'
```

## Messaging (optional)

To run the Discord bot, set `DISCORD_BOT_TOKEN` + `DISCORD_APP_ID`, enable the **Message Content Intent** in the Discord developer portal, and invite the bot with the `bot` + `applications.commands` scopes. See [Configuration](configuration.md) for the full set.
