# LLM configuration

Model tiers, providers and embedding configuration.

Behavior and configuration are documented for users in `site/content/quickstart.adoc` and `site/content/config-reference.adoc`, which is the source of truth. This page keeps the implementation rationale and the traps worth knowing before changing the code.

Two named model configs via `@ModelName`, both provider-agnostic:
- **`primary`** - Primary model (any OpenAI-compatible provider; default endpoint: NVIDIA NIM, default chat model `nvidia/nemotron-3-ultra-550b-a55b`; the previous default, `z-ai/glm-5.3`, reached its end of life on NIM on 2026-10-28).
  Pointed via the neutral `LLM_*` env vars (`LLM_BASE_URL`, `LLM_API_KEY`, `LLM_CHAT_MODEL`, `LLM_EMBEDDING_MODEL`, `LLM_TIMEOUT`).
- **`secondary`** - Same-provider chat model (openai provider, chat only).
  For the NVIDIA kind it is `nvidia/nemotron-3.5-lightning-30b-a3b` on the primary's endpoint and key (chosen over faster candidates for its rate limit: a tier that takes every turn while the primary fails must not throttle first), emitted by `LlmKindConfigSourceFactory` from `ProviderKind.secondaryChatModel()`, which also sets `qlawkus.model.secondary-enabled`.
  Overridable via `LLM_SECONDARY_CHAT_MODEL`, `LLM_SECONDARY_BASE_URL`, `LLM_SECONDARY_API_KEY`.
  A kind with no same-provider model leaves the tier disabled.
  It has its own breaker per workload (`SecondaryChatGuard`, one retry).
- **`fallback`** - Ollama, the local last resort, unchanged from before the secondary existed.
  Reached through `PrimaryChatGuard`/`EmbeddingGuard` once the primary has exhausted its retries and the circuit breaker opens, and through `SecondaryChatGuard` when the secondary fails.
  A same-provider tier does not survive a provider outage, which is why Ollama stays last; see [resilience.md](resilience.md).
- The chain is primary, then secondary (when enabled), then Ollama. `fallback-on-reject=false` (batch) stops the chain at the primary when the bulkhead rejects; a permanent (#358) failure on the primary still moves to the next tier.

Dev mode: Dev Services auto-provisions Ollama (no `.env` needed).
Prod: Requires `LLM_API_KEY` in `.env`.

Embedding dimension comes from the provider kind (`ProviderKind`; NVIDIA `nemotron-3.5-embed-1b` is 2048, the others 1024) and can be overridden with `EMBEDDING_DIMENSION`.
The pgvector column follows it: the repeatable `R__embedding_dimension` migration is keyed on the dimension and the model name through Flyway placeholders (a repeatable migration is checksummed after placeholder replacement, so either change re-applies it), records the model that produced the vectors in `embedding_model_marker`, keeps vectors that still match both and clears the rest (a new model at the same dimension included, #527), and `store.pg.EmbeddingBackfill` re-embeds the cleared rows from their text after boot, on the `batch` workload.
The markdown backend keys its `.embeddings.json` cache on the model name (`.embeddings.model` sidecar), so a model change re-embeds instead of loading incomparable vectors.
NVIDIA retired the previous default, `nv-embedqa-e5-v5`, on 2026-08-25 (#357): every turn failed with 410, since active memory embeds the query first.
Its successor `nemotron-3-embed-1b` was retired on 2026-11-23 and failed the same way (#527).

Cost estimation is opt-in via `qlawkus.cost.enabled`.
`model/TokenPriceCostEstimator` prices the token counts quarkus-langchain4j already reports, which is what turns on its `gen_ai.client.estimated_cost` metric.
Off by default on purpose: an estimate is indistinguishable from a measurement once it reaches a dashboard, so a default price would be a confident wrong number.
A distribution with tiered or committed pricing supplies its own `CostEstimator` at the default priority and is consulted first.

## Dependency versions (do NOT conflate the two langchain4j namespaces)

| What | Version | Where set |
|------|---------|-----------|
| Quarkus platform | `3.40.1.1` | `quarkus.platform.version` in root `pom.xml` |
| quarkus-langchain4j (Quarkiverse extension) | `1.14.2` | root `pom.xml` property `quarkus-langchain4j.version`, ahead of the platform (see below) |
| Upstream `dev.langchain4j` core (transitive) | `1.20.2` | same BOM - do not set |
| Upstream beta modules (`langchain4j-skills`, `langchain4j-pgvector`, `langchain4j-agentic`) | `1.20.2-beta30` | same BOM - do not set |

The Quarkiverse **extension** version (`1.14.2`) and the upstream **library** version (`1.20.2`) are different namespaces: `1.14.2` is NOT a langchain4j-core version.
The `quarkus-bom` manages neither.
The root pom imports `io.quarkiverse.langchain4j:quarkus-langchain4j-bom` at `quarkus-langchain4j.version` first, so it wins over the platform's copy (the platform ships `1.13.3` with `3.40.1.1`); the platform's `quarkus-langchain4j-bom` import stays after it for everything else the platform aligns.
That Quarkiverse BOM transitively imports the `langchain4j-bom`, which pins every `dev.langchain4j:*` artifact.
So when adding an upstream module (e.g. `langchain4j-skills`, or `dev.langchain4j:langchain4j` for `InMemoryEmbeddingStore`), add it with **no `<version>`** - the BOM resolves it.

The override is temporary: drop the Quarkiverse import (and the property) once the Quarkus platform ships quarkus-langchain4j >= 1.14.2. To move ahead again, bump `quarkus-langchain4j.version`; declaration order decides which BOM wins. Re-verify any time with:

```bash
mvn dependency:tree -pl client/runtime -Dincludes='dev.langchain4j'
```
