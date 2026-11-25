# Cognition and memory

The persona, owner, facts, working memory and episodic stores.

Behavior and configuration are documented for users in `site/content/memory.adoc` and `site/content/storage.adoc`, which is the source of truth. This page keeps the implementation rationale and the traps worth knowing before changing the code.

The memory subsystem (`client/runtime/.../cognition` + `.../store`) is the core of the agent.
Its guiding principle: **relevant memory is injected into the prompt automatically each turn, so recall never depends on the model remembering to search** - and the stores stay fully searchable on demand (RAG feeds the automatic injection; the `session_search` tool lets the model deliberately query transcripts).
Automatic injection is the reliable default; tool-based search is available on top.
Understanding it means reading several files together:

Three layers, all injected each turn via `SoulEngine` (the agent's `systemMessageProviderSupplier`) and the retrieval augmentor:

- **Persona** - `Soul` (a plain singleton domain object) rendered into the system message by `SoulEngine`.
  Persisted behind the `SoulStore` SPI, backend selected by the same `qlawkus.cognition.backend` switch: `store.markdown.MarkdownSoulStore` (`@DefaultBean`, a `soul.md` file seeded from `META-INF/qlawkus/default-soul.md`, no DB), `store.pg.PgSoulStore` (the `soul` table via `SoulEntity`), `store.pg.HybridSoulStore` (reuses pg).
- **Owner** - `UserProfile` (a plain singleton domain object; the single user this agent serves) injected every turn by `SoulEngine`.
  Maintained by `UpdateUserProfileTool`, refreshed by `MemoryCurationJob`.
  Persisted behind the `UserProfileStore` SPI: `MarkdownUserProfileStore` (`@DefaultBean`, an `owner.md` file), `PgUserProfileStore` (the `user_profile` table via `UserProfileEntity`), `HybridUserProfileStore`.
  Roots for the markdown soul/profile files: `qlawkus.agent.state.root` (default `~/.qlawkus/state`).
- **Facts** - `FactStore` SPI, backend selected **when the app boots** from `qlawkus.cognition.backend` (`markdown` | `pgvector` | `hybrid`) by `store.pg.CognitionStoreSelector`, one `@Produces` per SPI (see the "Runtime backend" section below): `store.markdown.MarkdownFactStore` (`@DefaultBean`; `.md` files + in-process `InMemoryEmbeddingStore` with a JSON embedding cache, no DB; shared file logic in `MarkdownFactFiles`), `store.pg.PgFactStore` (pgvector; only present when the `cognition-pgvector` extension is on the classpath), `store.pg.HybridFactStore` (files source-of-truth + pg mirror).
  Root: `qlawkus.agent.facts.root` (default `~/.qlawkus/facts`).
  The markdown stores are `@DefaultBean` across the module split, so a build without the pgvector extension is database-free whatever the `backend` value says. With the extension present, `CognitionStoreSelector` picks the store from the boot-time `backend` value; the non-chosen implementations are `@Typed(Self.class)` and never instantiated.
  Every embedding is tagged with a `MemorySource` (`remember-tool`, `semantic-extractor`, `episodic-consolidator`, `transcript`) - the enum exists so producers and purges never drift.
  **Facts are chunked before embedding** (`store.FactChunker`, char-budget `qlawkus.agent.facts.chunk-*`, default ~1200 chars ≈ 400 tokens): a fact over the embedding model's token limit (512 for `nv-embedqa-e5-v5`) is split into overlapping segments, so one fact becomes N vector rows sharing a parent `factHash` (in metadata; segment 0 also carries the whole `factText` for pg→files reconstruction).
  Identity/dedup is the `factHash`, not the row text - the pg `embeddings.content_hash` is generated from `metadata->>'factHash'` (not unique, since N segments share it), so `existsByContentHash` dedups at fact granularity.
  Without this, an oversized fact fails to embed: in `hybrid` the `.md` is written before the embed, so it orphans file-only and `CognitionReconciler.reconcileFacts` aborts the whole 6-store union every boot (the reconcile now also try/catches per fact, so one bad fact never aborts the union).

Stores & retrieval:

- **Active Memory** - `ActiveMemoryAugmentor` is the AiService `retrievalAugmentor`; it runs an `EmbeddingStoreContentRetriever` before each reply and injects query-relevant facts.
  The index comes from `FactStore.embeddingStore()` on the resolved `FactStore` (markdown: its in-process index; pgvector/hybrid: the pg table), never from a lookup of a concrete store class: in hybrid the `@DefaultBean` `MarkdownFactStore` stays in the build whenever anything keeps it (`@Unremovable`, `quarkus.arc.remove-unused-beans=false`), and recall would then read its in-process index, which never sees a fact `HybridFactStore` writes after it loads (pinned by `HybridActiveMemoryTest`).
  It filters OUT `source=transcript` so curated facts stay clean.
- **Working memory** - `WorkingMemoryStore` SPI (also a langchain4j `ChatMemoryStore`), windowed to 40 messages.
  `updateMessages` is append-only (preserves `createdAt`; never reset it) and fires `MessagesAppendedEvent` for every new message on any channel.
  Backends: `store.markdown.MarkdownWorkingMemoryStore` (`@DefaultBean`, one append-only `<memoryId>.jsonl` per conversation under `qlawkus.agent.working-memory.root`, default `~/.qlawkus/working-memory`), `store.pg.PgWorkingMemoryStore` (the `chat_message_entity` table), `store.pg.HybridWorkingMemoryStore` (reuses pg - the rolling chat log is transient, not file-versioned).
  The markdown store is the `@DefaultBean`; because upstream ships a `@DefaultBean InMemoryChatMemoryStore`, `ClientProcessor` vetoes it (`ExcludedTypeBuildItem`) so there is a single default and the non-default pg/hybrid beans still override when the pgvector extension is present.
- **Transcripts (session_search)** - `TranscriptArchiveObserver` embeds each message as `source=transcript`; `SearchTranscriptsTool` searches only those.
- **Episodic** - `EpisodicConsolidatorJob` (nightly) summarizes each day into journals (also embedded through the `FactStore` as `source=episodic-consolidator`).
  The journal record is an `EpisodicStore` SPI, backend selected at boot by the same `qlawkus.cognition.backend`: `store.markdown.MarkdownEpisodicStore` (`@DefaultBean`; dated `<date>.md` files, one per day, no DB; file logic in `MarkdownEpisodicFiles`), `store.pg.PgEpisodicStore` (the `Journal` table), `store.pg.HybridEpisodicStore` (files source-of-truth + pg mirror).
  Pg/hybrid share Panache access via `JournalRepository` and live in the `cognition-pgvector` extension.
  Root: `qlawkus.agent.episodic.root` (default `~/.qlawkus/journals`).

Scheduled background jobs, each with a manual `POST /api/admin/memory/*` trigger:

- `MemoryReviewJob` - semantic dedup of near-duplicate facts (`/review`).
  Distance only nominates a pair; `FactStore.purgeNearDuplicates(maxCosineDistance, sameFact)` asks `cognition.review.DuplicateJudges` whether the two texts are the same fact before removing the later one, so a correction ("no longer prefers dark theme") is never purged as a duplicate.
  Unlike the turn gate this **fails safe**: a throw or timeout keeps both facts (a deletion cannot be undone, a surviving duplicate is retried next run), counted in `qlawkus.turn.gate{question=duplicate-fact}`.
  The `@DefaultBean` `PassThroughDuplicateJudge` answers yes (distance alone, as before); `decision.nvidia` supplies `NvidiaDuplicateJudge` (`qlawkus.decision.nvidia.duplicate-threshold`, default 0.9).
  The pg and hybrid stores pair facts, not rows: rows sharing a `factHash` are one fact, so the segments of a chunked fact are never judged against each other. They read the pairs in one transaction, judge each fact's whole text (segment 0's `factText`) outside any transaction, and delete every segment of a confirmed duplicate in a second (`NearDuplicatePurge`), so a connection never waits on a model call.
- `MemoryCurationJob` - folds facts into the owner profile, reconciling contradictions; leaves facts untouched (`/curate`).
- `SemanticExtractorObserver` - extracts facts from a conversation on `ChatCompletedEvent`.
  The event is emitted channel-agnostically by `ChatCompletionEmitter`, which observes the store-level `MessagesAppendedEvent` and fires once per turn (when a final assistant message - one without tool-execution requests - is appended).
  **Passive fact extraction works on every channel** (REST, Discord, Telegram); gate it with `qlawkus.agent.semantic-extractor.enabled` (default `true`).
  The same event also drives the Brag `AchievementProcessor`, so both run on all channels.
- **Turn gate** - each post-turn observer (`SemanticExtractorObserver`, `SkillExtractorObserver`, `AchievementProcessor`) asks `cognition.gate.TurnGates` before its generation, with a yes/no `TurnQuestion`.
  The SPI is `TurnGate`; the `@DefaultBean` `PassThroughTurnGate` answers yes to everything, so behaviour is unchanged.
  `TurnGates` calls the gate on the `batch` workload and fails open (a throw runs the generation, logged by exception class only), counting each decision in `qlawkus.turn.gate{question,outcome}` (`pass`|`skip`|`error`).
  Only the `onChatCompleted` path is gated; the public `extractAndStore` methods are not.

Config knobs: `qlawkus.agent.memory.*`, `qlawkus.agent.active-memory.*`, `qlawkus.agent.transcript-*`, `qlawkus.agent.semantic-extractor.*`, `qlawkus.memory-review.*`, `qlawkus.memory-curation.*`.
Admin REST: `GET/DELETE /api/admin/memory` (summary / purge), `POST /api/admin/memory/{review,curate}`.
End-to-end check: `scripts/memory-benchmark.sh` (needs a running instance + real LLM).

Gotcha when testing recall: working memory and the system prompt are both sources, so to prove a fact came from the vector store, `TRUNCATE chat_message_entity` first.
The NVIDIA embedding model scored a relevant query/passage pair around 0.88 and an unrelated one around 0.61 (langchain4j's `(cos+1)/2`) when it was `nemotron-3-embed-1b`, so `0.7` is the default margin; the current default, `nemotron-3.5-embed-1b`, has not been re-measured.
Re-measure that gap whenever the embedding model changes: a new model with a lower distribution silently stops recall.

## Runtime backend

One build serves the files-only quickstart and a Postgres deployment, so the store is chosen when the app boots (`site/content/storage.adoc` is the user-facing page).

- `store.pg.StoreBackendConfigSourceFactory` (ordinal 90) resolves `qlawkus.cognition.backend`: an explicit value wins, else a configured `quarkus.datasource.jdbc.url` means `pgvector`, else `markdown`. It always emits `quarkus.datasource.active`, `quarkus.hibernate-orm.active` and `quarkus.flyway.active`: `false` on `markdown`, which keeps the extension from migrating a database that is not there, and `true` otherwise. The explicit `true` is not redundant: Quarkus defaults them to inactive when no URL was known at build time, which is the published image's case, so a URL that arrives from the keystore or the environment at boot would otherwise leave Hibernate deactivated and Flyway idle (the boot succeeds and the first store call fails with `InactiveBeanException`). Only a packaged image shows it; the tests build with a Dev Services URL. It reads the keystore through `KeystoreSecretConfigSourceFactory.withStoredSecrets`, like `LlmKindConfigSourceFactory`, because a plain factory cannot see another factory's output (the wizard's Storage step writes both keys there). The app config must therefore not carry a default `quarkus.datasource.jdbc.url`, and anything that boots the extension with a dev service and no URL (dev, tests) has to set the backend explicitly: `app` does for `%dev` and `%test`, the compose files set `hybrid`, and `integration-tests/cognition` sets `pgvector`.
- `store.pg.CognitionStoreSelector` holds one `@Produces @ApplicationScoped` method per SPI, choosing among `Instance`s of the markdown, `Pg*` and `Hybrid*` classes. The `Pg*` and `Hybrid*` stores are `@Typed` to their own class so they never answer the SPI themselves, and an `Instance` that is not chosen is never created, so a markdown boot never builds anything that needs a datasource. A new store implementation needs both the `@Typed` and a line in the selector, and `RuntimeBackendSwitchTest` fails when one is missing.
- `CognitionBackendState` is what every database-touching bean that runs without being asked (a `StartupEvent` or event observer, a `@Scheduled` method, a health check) consults before it works: `EmbeddingBackfill`, `HybridReconcileStartup`, `PgDebugJournal`, `DebugHistoryRecorder` and the reconcile endpoints do.
- Trap: ArC fails the boot with `InactiveBeanException` when a bean it creates at startup injects an inactive `DataSource`, `AgroalDataSource` or `EntityManager` directly. Inject `Instance<DataSource>` (and call `get()` only after the backend check) in anything in this extension.
- `google-workspace` (its `google-auth` datasource) and `brag` own named datasources that `StoreBackendConfigSourceFactory` does not touch. `NamedDatasourceConfigSource` (client, one thin `ConfigSourceFactory` per extension) switches each one's datasource, Hibernate ORM unit and Flyway on only when `QUARKUS_DATASOURCE_<NAME>_JDBC_URL` is given, and off otherwise, so the published image can carry them. Outside `prod` it leaves a missing URL alone while dev services may still supply one. Trap: an `@Transactional` method opens its transaction before its body runs, and that throws on an inactive persistence unit, so these extensions ask `BragDatabase` or `GoogleAuthDatabase` first and call `inTransaction` themselves. The Google sign-in state falls back to memory without a database; anything new that persists in either must do the same or say it needs one.
