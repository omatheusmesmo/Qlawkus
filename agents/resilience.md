# Resilience, workloads, shutdown and single instance

Fault tolerance, model workloads, the shutdown drain and the instance lock.

Behavior and configuration are documented for users in `site/content/operations.adoc` and `site/content/architecture.adoc`, which is the source of truth. This page keeps the implementation rationale and the traps worth knowing before changing the code.

## Resilience & Health

Fault tolerance is `smallrye-fault-tolerance` throughout; no hand-written retry or breaker remains in the reactor. Where a guard is applied matters far more than which annotation it uses, and there is one hard rule.

**Never put `@Retry`, `@CircuitBreaker` or `@Fallback` on `AgentService`.** The upstream quarkus-langchain4j fault-tolerance guide shows exactly that pattern, and it is wrong for a method that declares `tools`: the annotation wraps the whole ReAct loop, so a transient failure on a later turn replays tool calls that already succeeded on an earlier one, silently duplicating their side effects (quarkiverse/quarkus-langchain4j#2744).
Guards belong on the underlying `ChatModel`/`StreamingChatModel`, where a retry replays only the one failed model call.
The rule is repeated in the `AgentService` javadoc, but that copy only reaches someone already editing the file the annotation must stay out of.

**Model resilience is programmatic, not annotated.** `model/PrimaryChatGuard` and `model/EmbeddingGuard` build a `TypedGuard` instead of using `@CircuitBreaker`, because a CDI interceptor cannot serve the streaming path: it is callback-based, so the failure never crosses the interceptor boundary.
Both are `@Startup`, so the breaker is registered before first use, and both name their breaker through `ModelFallbackConfig.CIRCUIT_BREAKER_CHAT` / `_EMBEDDING`.
That name is the lookup key `ModelReadinessCheck` reads.

The programmatic API is also the only caller of `SpiAccess.get()`, and that has a native-image consequence worth knowing before touching either guard.
`SpiAccess$Holder` resolves the fault-tolerance SPI through `ServiceLoader` and keeps only an implementation whose `applies()` returns true, which the CDI one answers by calling `CDI.current()`.
Quarkus runs `STATIC_INIT` (Arc startup included) at image build time in native, so the holder usually initializes there and the instance is baked in - but whether the analysis reaches it before Arc is up varies between builds, which showed up as an intermittently red native build.
`ClientProcessor` therefore pins the holder to runtime initialization **and** registers the `Spi` service provider; both are required, since deferring it without the registration moves the lookup into an image that resolves services only from explicit registrations, and the app then fails at boot.
The annotation-driven path never hits any of this: it compiles to generated interceptors that do not call `SpiAccess`.

**Outbound HTTP goes through Quarkus REST Client by default.** A REST Client interface (`@RegisterRestClient`, typed request and response records, Jackson) gives timeouts, TLS, proxy, redirects, metrics and tracing through `quarkus.rest-client.<key>.*`, and maps statuses with a `@ClientExceptionMapper` that returns `HttpRetryClassifier.classify(response)`.
Hand-written HTTP (`java.net.http`, a raw Vert.x client) needs a stated reason in the code or here, such as a per-call URL policy that REST Client cannot express.
Still on hand-written HTTP only where a thin policy layer must run before connect and on each redirect hop: `OutboundPolicy` for skill-hub fetches (the GET itself is `SkillHubFetchClient` via `@Url`) and `TelemetryHttp` until the REST Client migration lands.
Inbound messaging media uses `MediaDownloadClient`.
Telegram Bot API calls (including `sendAudio`) go through `TelegramBotClient`.
A base URL that is configuration per call (each TTS provider has its own) is passed with `@Url`; the interface's configured URL is then only the mandatory default.

**HTTP clients retry on one exception type.** Every outbound client (the 6 Google modules, Skill Hub, the messaging clients and media downloader, code review) throws `http.TransientHttpException` for a 429, a 5xx, or a network-level failure, and retries only that:

```java
@Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
       jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
       retryOn = TransientHttpException.class)
@ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
```

Two rules keep that correct rather than decorative.
It goes on **one HTTP call, not one logical operation**, so a multi-request `install` cannot re-run its first request because the third failed.
And a permanent status (401, 403, 404) propagates immediately, since retrying something that will never resolve only delays surfacing it.

**Health checks go through the store SPIs, not through storage.** `health/CognitionReadinessCheck` reads through the SPIs and calls each store's `verifyWritable()`, so one check is correct on `markdown`, `pgvector` and `hybrid` with no conditional: each backend resolves its own store.
Only the turn path blocks (persona, owner and skill index reads, working-memory write); the other writes report `degraded` and stay UP, because the agent is single-instance and DOWN means nobody answers.
Skill probes run only while `qlawkus.skills.enabled` is on, and an instance already DOWN skips the write probes (they cannot change the verdict, and against a hanging database each would wait out a connection timeout).
`verifyWritable()` must prove a write without making one, since readiness polls it every few seconds: markdown writes one byte to a fixed-name dotfile and deletes it (`store.markdown.WriteProbe`; fixed so a root where deletes fail keeps one file, not one per poll), pg asks the catalog (`store.pg.PgWriteProbe`: `transaction_read_only` plus `has_table_privilege`) rather than running a zero-row `INSERT`, which still takes a `RowExclusiveLock` and fires statement triggers.
Each `PgWriteProbe.Table` lists exactly the DML its store issues (working memory and journals are INSERT/DELETE only): demanding more takes a correctly least-privileged instance DOWN, and on a single instance that means nobody answers.
Never give a real store an empty `verifyWritable()`: that is the vacuous pass this check exists to remove (only the in-memory test doubles may).
`health/ModelReadinessCheck` reads both breaker states through `CircuitBreakerMaintenance.currentState`, a read-only lookup, so polling readiness can never promote a breaker state as a side effect; it reports DOWN only when a breaker is OPEN **and** no fallback is configured.
A check that is meaningful on only one backend belongs in that backend's own module, never as an `if` inside a shared check.

**A permanent model error is invisible to the breaker, so readiness reads it from `model/PermanentFailures`.** A 401, 403, 404 or 410 from the primary (a revoked key, a wrong model name, a retired model) is on the `abortOn`/`skipOn` list of both guards on purpose: retrying cannot heal it and a fallback would hide it.
The same choice keeps the breaker CLOSED while every turn fails, so `ModelReadinessCheck` used to stay UP on a provider that answered 410 forever (#358).
`PermanentFailures.observe` wraps each attempt against the primary, inside the guard and also on the fallback-disabled path (`callPrimaryOnly`), and counts consecutive failures per surface and workload, classified by the `HttpException` status langchain4j keeps as a cause.
A success resets the streak, a transient failure neither counts nor resets, and the statuses are `qlawkus.model.permanent-failure.statuses` (default `401,403,404,410`; a plain 400 describes one request, not the provider).
After `qlawkus.model.permanent-failure.threshold` (default 3) consecutive failures on the **interactive** workload the check reports DOWN with a `chatError`/`embeddingError` naming the status, whatever the fallback setting, since a permanent failure never reaches the fallback.
The verdict lapses after `qlawkus.model.permanent-failure.window` (default 300s) without a new failure, so an instance held out of rotation can receive the traffic whose success clears it; one more permanent failure takes it straight back DOWN.
Other workloads are reported as data and never flip the status, and reading the streak has no side effect.
A permanent **embedding** failure also never reaches the fallback: its vector would come from another model and land in another vector space, so it fails loudly instead of degrading recall silently.
Pinned by `ModelReadinessPermanentFailureTest`, `PermanentFailureWindowTest`, `FallbackEmbeddingModelTest` and `EmbeddingGuardTest`.

**Readiness and liveness answer different questions.** Liveness deliberately does not consult the model: killing the pod because the provider is down would turn their incident into an outage here, and the restarted pod would meet the same provider.
`examples/redeploy/k8s/deployment.yaml` carries the probe trio (the startup probe absorbs the order-of-magnitude gap between JVM and native boot), and the Compose stacks poll the same `/q/health/ready`, so both deploy paths agree on what "serving" means.

## Tool quarantine

`tool.quarantine.ToolQuarantine` counts consecutive failures per tool and `QlawToolProvider` leaves a quarantined tool out of the catalog on every turn.
It is not a SmallRye breaker on purpose: a breaker rejects the call, but the model would still be offered the tool and keep choosing it, and hiding it from the catalog is the point.
`QuarantineToolExecutor` sits under the approval gate so a refused or pending call never counts, and it ignores a call the model got wrong (`IllegalArgumentException`, `ToolArgumentsException`, `ConstraintViolationException`, anywhere in the cause chain), or a model that misuses `readFile` five times would lose the tool for the cooldown.
After the cooldown the streak resumes one failure short of the threshold, which is the probation.
State is in memory: a restart is a fresh start for the dependencies as well.
Pinned by `ToolQuarantineTest`, `QuarantineToolExecutorTest`, `QlawToolProviderTest` and `ToolQuarantineAdminResourceTest`; the owner-facing page is `site/content/operations.adoc`.

## Model workloads

`ReplyLanguageResolver` is the single language policy for text and voice. `AgentStream`,
`AgentRunner`, direct `AgentService` calls and the voice session share it. `PromptReplyLanguageDecision`
uses the same registered `ChatModel` as fact extraction when `qlawkus.reply-language.enabled` is true
(the reference app enables it; other distributions default to prompt-only inference). The existing
NVIDIA/secondary/Ollama chain remains authoritative; no parallel provider or credential is registered.
Classification has no tools, chat memory or RAG, uses the isolated `decision` workload, and has its own
timeout, bulkhead and breaker. The resolver's bounded 256-conversation cache stores language codes only.
Explicit language choices bypass classification; an already prepared voice turn never classifies twice.
TTS provider availability must not constrain the language of the answer. See `site/content/voice.adoc`.

`AgentReplyStyle` is presentation metadata on `AgentTurn`, never text appended to the owner's message.
`SoulEngine` applies it to the system prompt for a streamed voice turn and applies the current-request
language policy on every channel. `console-voice` alone owns CommonMark speech rendering: the screen
keeps the original response, while the TTS path removes formatting and omits code blocks, tables and
images. It buffers complete blocks up to 64 KiB, so a long single paragraph can delay its first audio.
The transcript has a bounded scroll area that follows new text only while the reader is at the bottom.

The agent talks to one provider for two different reasons: answering the owner, and doing background work about the owner.
Sharing fault-tolerance state between them is the defect - a nightly job that trips the provider's rate limit opens the breaker the owner's next message goes through, so the agent answers from the fallback because it was busy talking to itself.

Workloads are named in configuration (`qlawkus.model.workload.<name>.*`, `RUN_TIME`, so reachable from `/api/admin/runtime-toggles`), keyed like `MessagingConfig`.
`interactive` and `batch` always exist; more can be declared without code.
`PrimaryChatGuard` and `EmbeddingGuard` build **one `TypedGuard` per workload** at startup, and that is what makes the isolation structural rather than conventional: SmallRye gives each guard object its own instance of every strategy, so two guards cannot share breaker state.

**A `TypedGuard`, not `@ApplyGuard`.** The reusable-guard mechanism is the more elegant fit for named policies, but `Guard` has no `withFallback()` - only `TypedGuard` does - and the fallback to the secondary provider is why these guards exist.

**A thread-scoped marker, not a CDI qualifier.** `WorkloadContext.runAs("batch", ...)` selects the workload.
A qualifier member has to be known when beans are wired, which would fix the set of workloads at build time and defeat declaring them in config.
The marker is safe for the same reason the per-call fallback bridge is: SmallRye runs a synchronous action and its fallback handler on the calling thread.
Work that hops threads must set the marker on the thread that actually calls the model.

**A synchronous bulkhead rejects; it does not queue.** SmallRye queues excess callers only for asynchronous actions, and a chat completion is synchronous here, so exceeding `max-concurrent` raises `BulkheadException` immediately.
That is why `fallback-on-reject` exists and is `false` for batch: without it, every throttled job would quietly re-target the fallback provider and do the work anyway, which is the opposite of throttling.
Abandoning the run is safe because the jobs are re-runnable, audited when shutdown began abandoning them.
`@Scheduled(concurrentExecution = SKIP)` covers the other half - a job overlapping *itself* - which the bulkhead does not address.

**Readiness stays tied to `interactive`.** Other workloads' breakers are reported as data but never flip the status. Letting a background breaker take the instance out of rotation would rebuild, in the health layer, exactly the coupling this removes.

Two findings behind this are recorded as executable tests in `BulkheadSemanticsProbeTest`, because the documentation does not state them: a bulkhead does not offload the fallback off the calling thread, and **duplicate circuit breaker names are accepted silently** on smallrye-fault-tolerance 6.11.2.
The second corrects an earlier assumption in this project that the library rejects them; uniqueness is our invariant, which is why `WorkloadGuards.breakerName` derives it instead of accepting one.

## Shutdown

A SIGTERM has to drain what the process is holding, and the drain has to be *waited on*.
Both messaging adapters already had `@Observes ShutdownEvent` before this was addressed, and neither waited: Discord called `logout().subscribe(...)` and returned, Telegram interrupted its poll thread without joining it.
Code that looks like a drain but returns immediately is worse than none, because it reads as solved.

Drains are registered as `ShutdownListenerBuildItem` from each module's `*-deployment`, never as CDI observers.
The platform then supplies what would otherwise be hand-written per subsystem: two ordered phases, an acknowledgement callback, and a single budget (`quarkus.shutdown.timeout`) covering all of them.
A `@Observes ShutdownEvent` would need its own timeout in every bean, and they would drift.

The phases carry meaning.
`preShutdown` refuses new work (Telegram stops polling); `shutdown` releases resources (PTY sessions closed, Discord logout awaited, poll thread joined).
Two rules keep this correct: **`done()` belongs in a `finally`** - a listener that throws before acknowledging costs the full timeout on every rollout, silently - and each listener swallows its own exception, so one broken drain cannot stop the others.

The listener instance is built during augmentation, before any container exists, so it holds no bean reference and resolves what it needs from `Arc.container()` at shutdown, guarded by `isRunning()`.

**Running jobs are abandoned, not awaited.** A nightly consolidation makes model calls and cannot fit any sane grace period, so the scheduler simply stops.
That is only safe because the jobs are re-runnable, which was audited rather than assumed: purge, curation, skill lifecycle and cleanup all converge on a second run.
`EpisodicConsolidatorJob` was the exception and was fixed here - it wrote the journal *before* embedding it while `existsForDate` guarded on the journal alone, so anything landing between the two steps (a SIGTERM, or an embed failure that only warned) left an entry unreachable by retrieval and skipped that day for good.
The guard now re-asserts the embedding, which the fact store dedups by content hash.

The budget is chosen end to end, not per layer: `quarkus.shutdown.delay=3s` plus `quarkus.shutdown.timeout=20s` in `app/`, against `terminationGracePeriodSeconds: 30` in `examples/redeploy/k8s/deployment.yaml`.
Raising one without the other converts a clean drain into a kill.
The timeout also caps in-flight HTTP, and `/api/chat` is an SSE ReAct turn that can run for minutes, so it deliberately cuts a long turn rather than holding the rollout; working memory is append-only, so a cut turn loses a reply, not state.

## Single instance

Qlawkus is single-instance by product decision (#297): one agent, one owner, and the scheduled jobs assume they are the only writer.
`instance.InstanceLock` (`@Startup`) enforces it with an exclusive `FileChannel` lock on `<qlawkus.agent.state.root>/.instance.lock`, so a second process on the same state fails at boot naming the holder; the reference k8s Deployment uses `strategy: Recreate` because a surge rollout is exactly that second process.
Two traps behind its shape: POSIX locks are per process and **closing any descriptor of the file drops them all**, so a refused instance in the same JVM is turned away by a claim in `System.getProperties()` before it ever opens the file (otherwise its close would release the lock it was refused) - not a static field, which exists once per Quarkus app classloader; it is also `@Startup(PLATFORM_BEFORE)` so no other startup observer runs in a refused instance; and every test app needs its own `%test.qlawkus.agent.state.root`, or it collides with a running agent.
The lock is per host, so it does not cover instances on different hosts sharing only a database.
