# Qlawkus Documentation

Qlawkus is a single-user, self-hosted autonomous agent built as a set of Quarkus extensions (`client/`, `tools/`, `messaging/`) wired together by a deployable app (`app/`). It has dynamic cognition (a "soul"), a layered long-term memory, Google Workspace tools, multi-provider messaging (Discord, Telegram) with voice, and a sandboxed shell.

## Documentation index

| Doc | What it covers |
|-----|----------------|
| [Getting Started](getting-started.md) | Prerequisites and the three ways to run it: dev mode, local Docker (Ollama), prod Docker (NVIDIA). |
| [Configuration](configuration.md) | Full environment-variable reference: models, databases, memory tuning, messaging, voice, Google, vault. |
| [Architecture](architecture.md) | Module layout and the cognition/memory subsystem at an operator level. |

For contributor/agent-facing detail (build internals, tool authoring, CI/release, gotchas) see [`AGENTS.md`](../AGENTS.md) at the repo root.

## TL;DR

```bash
# Dev mode (auto-provisions Postgres + Ollama via Dev Services, no .env needed)
mvn install -pl client -am -DskipTests
cd app && mvn quarkus:dev          # http://localhost:8080

# Talk to it
curl -X POST http://localhost:8080/api/chat/sync -u qlawkus:qlawkus \
  -H 'Content-Type: application/json' -d '{"message":"Hello, who are you?"}'
```
