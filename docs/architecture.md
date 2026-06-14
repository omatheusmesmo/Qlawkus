# Architecture

This is an operator-level overview. For build internals, tool authoring, and CI/release, see [`AGENTS.md`](../AGENTS.md).

## Module layout

Multi-module Maven monorepo using the Quarkus extension pattern (each extension has `deployment/` + `runtime/`):

| Module | Role |
|--------|------|
| `client/` | Core extension: the agent, cognition/memory, REST, sandboxed shell. Not runnable on its own. |
| `tools/` | Optional tool extensions: Google Workspace (Gmail, Calendar, Drive, Sheets, Storage) and Brag. |
| `messaging/` | Messaging extension: core + Discord / Telegram (Slack / WhatsApp adapters exist but are not wired into `app/`). |
| `app/` | The deployable Quarkus app. Wires the extensions together. The only module you run. |
| `integration-tests/` | Per-feature integration test apps (smoke, cognition, google, terminal, brag, messaging, ...). |
| `testing-internal/` | Shared test utilities. |

## Request flow

```
HTTP /api/chat[/sync]  ┐
Discord / Telegram     ┼─> AgentService (@RegisterAiService, ReAct loop)
                       ┘     ├─ system prompt: SoulEngine (persona + owner profile + acting rules)
                             ├─ retrievalAugmentor: ActiveMemoryAugmentor (injects relevant facts)
                             ├─ tools: remember / search / transcripts / profile / shell / google / ...
                             └─ chat memory: PgWorkingMemoryStore (windowed, persistent)
```

The chat model is a `FallbackChatModel`: primary (NVIDIA) with an Ollama fallback behind a circuit breaker.

## Memory

The defining idea: **memory is injected into the prompt, not searched** - the agent does not have to decide to look things up.

- **Persona** - the agent's own `Soul` (mood, focus, identity), rendered into the system prompt.
- **Owner profile** - a `UserProfile` (the single person served) injected on every turn. Maintained by the `updateUserProfile` tool and refreshed nightly by the curation job.
- **Facts** - long-term facts in pgvector, each tagged with a source (`remember-tool`, `semantic-extractor`, `episodic-consolidator`, `transcript`).
- **Active memory** - before each reply, relevant facts are retrieved and injected (transcripts excluded to keep it clean).
- **Transcripts (session search)** - every message is archived and semantically searchable via the `searchTranscripts` tool, distinct from curated facts.
- **Episodic journals** - a nightly job summarizes each day; summaries are embedded and surfaced by active memory.

### Background jobs

| Job | Schedule | Manual trigger | What it does |
|-----|----------|----------------|--------------|
| Episodic consolidation | nightly | - | Summarizes the day into a journal. |
| Memory review | nightly | `POST /api/admin/memory/review` | Removes semantic near-duplicate facts. |
| Profile curation | nightly | `POST /api/admin/memory/curate` | Folds facts into the owner profile, reconciling contradictions. Facts are left untouched. |

### Operational notes

- Passive fact extraction (`semantic-extractor`) runs only on the REST chat path, **not** the messaging path. On Discord/Telegram, use explicit "remember ..." (the `rememberFact` tool) or the profile tool.
- The shared conversation (`AGENT_SHARED_CONTEXT=true`) means the owner is continuous across Discord, Telegram, and REST.
- To inspect or reset memory, use `GET` / `DELETE /api/admin/memory` (see [Configuration](configuration.md) and [Getting Started](getting-started.md)).

## Databases

Three PostgreSQL databases, Flyway-managed: `qlawkus` (soul, chat history, journals, pgvector embeddings), `qlawkus_google_auth` (OAuth), `qlawkus_brag` (career brag documents).
