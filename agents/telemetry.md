# Telemetry

Meters, traces, logs and self-observation.

Behavior and configuration are documented for users in `site/content/operations.adoc`, which is the source of truth. This page keeps the implementation rationale and the traps worth knowing before changing the code.

Model and AI-service meters come from upstream and need no code: they appear whenever a registry is present (see the `observability` capability). What `client` instruments itself is the cognition subsystem, which nothing upstream can see.

`metrics/AgentMeters` is the only class that touches a `MeterRegistry` and the one place every metric name is written down.
Names are constants there because a metric name is a public contract - it becomes a time series, then a panel, then an alert - so a literal spread across call sites would drift.
`client` depends on `io.micrometer:micrometer-core`, which is API plus in-memory accumulators: it opens no socket, so it is not egress and does not belong behind the capability.
The registry and exporter that do leave the machine stay in `qlawkus-observability`.
With that extension absent the `Instance<MeterRegistry>` is unsatisfied and every meter call is a no-op.

Where each measurement attaches, and why, is the part worth knowing before adding more:

- **Retrieval** - `MeteredContentRetriever` wraps the retriever built in `ActiveMemoryAugmentor.get()`. Scores come from `ContentMetadata.SCORE`. This is the meter that answers "did memory work this turn?", which otherwise requires reading a log.
- **Tools** - decorated in `QlawToolProvider`, **not** intercepted.
  That provider calls `ClientProxy.unwrap` before building each `DefaultToolExecutor`, so tool calls never cross a CDI proxy and no interceptor, including the project's own `@Logged`, can observe them.
  `MeteredToolExecutor` overrides *both* `execute` and `executeWithContext`, since `DefaultToolExecutor` implements each separately.
  Only `@QlawTool` extension tools flow through here; the tools declared on `@RegisterAiService(tools = {...})` are not counted.
  The same seam gives each call its span: `tracing.TracedToolExecutor` wraps the metered executor whenever a `Tracer` bean resolves (the `observability` capability), copying the name (`langchain4j.tools.<name>`) and GenAI attributes of upstream `ToolSpanWrapper`, which only wraps its own `QuarkusToolExecutor` and so never sees these tools.
  Keep the two in step: a different name would split one ReAct loop across two queries.
  `client` declares only `opentelemetry-api`, for the micrometer-core reason.
- **Stores** - the opposite case: stores are injected as their SPI and reached through a proxy, so interception works.
  `ClientProcessor.meterCognitionStores` adds `@Metered` at build time to every implementor of the six store SPIs, which covers the `cognition-pgvector` backends without that extension knowing telemetry exists.
  Annotating by hand would mean a new backend silently reporting nothing.
  `StoreMeterInterceptor` derives the `backend` tag from the implementation's name prefix, reporting `Pg*` as `pgvector` to match `qlawkus.cognition.backend`.
- **Jobs** - `AgentMeters.timeJob` wraps the body of each `@Scheduled` method. The `job` tag reuses the existing `@Scheduled(identity = ...)`, so metric and console schedule page name the same thing.
- **Fallback** - counted in the guards' fallback handler, which is the exact moment the primary is abandoned, so a switch is recorded even when the breaker never opens.
  Breaker state is a gauge bound to the `@ApplicationScoped` guard, because Micrometer holds only a weak reference to the gauged object.
- **Redeploy** - the loop restarts the very process that receives its report, so in-memory counters would reset to zero exactly when a successful run should increment them.
  `RedeployHistoryService` therefore keeps the totals in the persisted history and `AgentMeters.redeployRuns` / `redeployPhase` / `redeployLastSuccessAge` publish them as function meters and a gauge over it: `qlawkus.redeploy.runs` (`outcome`, `failed_phase`; `none` on a success), `qlawkus.redeploy.phase.duration` (`phase`, `outcome`, where `outcome` is that phase's own, so a build that failed is not a slow successful one), and `qlawkus.redeploy.last.success.age` (seconds, `NaN` before the first success).
  Every series is registered at boot, so a restarted instance publishes the totals its predecessor reached.
- **Embedding** - `AgentMeters.embedFact` wraps the embed loop in each of the three `FactStore` implementations, so the outcome recorded is the embed's own.
  `FactChunker` stays a pure function: chunking runs before anything reaches the model and has no outcome to report, so metering there could only ever record a success.
  The convenience store constructors used by tests pass `AgentMeters.disabled()`.

**Logs.** `qlawkus-observability` brings `quarkus-logging-json`, so the console is JSON in prod; its `microprofile-config.properties` turns that off for `%dev` and `%test`.
It also sets `quarkus.otel.logs.enabled=true` (fixed at build time, inert while the SDK is disabled), so a deployment that turns the SDK on ships log records over OTLP next to its spans.
Correlation needs no code: quarkus-opentelemetry writes `traceId`/`spanId` into the MDC and the formatter emits it.
Note that on Quarkus 4 `quarkus.otel.sdk.disabled=true` (the extension default; the Compose stacks turn it off) yields no-op spans with all-zero ids and nothing in the MDC, so log and trace correlation starts only once the SDK is on; a test that compares MDC ids to a span's runs with the SDK enabled and every exporter `none` (`LogTraceCorrelationTest`) and must also assert the span is valid, or two all-zero ids would pass it.
At INFO the agent logs one `Agent turn` line per turn (from `AgentTurn`, bound by `AgentLogInterceptor`, `AgentRunner` and `AgentStream`), one `Tool call` line per `@QlawTool` call (from `MeteredToolExecutor`), and job start/end lines (`JobLog`), with `channel`, `conversation`, `workload` and `tool` in the MDC; message, fact, prompt, skill and tool content is logged at DEBUG only, so a new INFO line must carry identifiers and counts, never text.
The failure fields are the one exception to that rule and are bounded and redacted: an `outcome=error` `Agent turn` line ends with `error` (root cause class) and `cause` (root message, one line, 200 characters at most, passed through `SecretRedactor` via `FailureSummary`), a throwing tool call is a WARN `Tool call` line with the same two fields and no stack trace, and `MessagingOrchestrator`'s fallback warning carries them too.
A failed reply delivery adds `httpStatus`, `providerCode` and `providerMessage` when the platform reported them (`DeliveryFailure`, read through `MessagingProvider.deliveryFailure`, Discord implemented).
Existing field names are a contract: add fields, never rename them.
Messaging adds one line per accepted or rejected inbound message, per reply delivered or failed, and per voice transcription or synthesis; its Uni stages hop threads, so `MessagingLog` sets `channel` and `conversation` on each line rather than once per message.
The `dev.langchain4j.agentic.agent.AgentInvocationHandler` category is held at ERROR in `client`'s `META-INF/microprofile-config.properties`, pinned by `AgentInvocationHandlerLogLevelTest`.
The operator-facing field reference is the "How to read an agent turn" section of `site/content/operations.adoc`.

**Metrics stay off the public port.** `app/` enables the management interface and keeps health on the main one (`quarkus.smallrye-health.management.enabled=false`), so `/q/metrics` is served only on 9000, unauthenticated, and the public port answers 404.
The same port serves plain HTML at `/q/dashboard` (tool catalog and a small metrics slice via `ops.OpsDashboard`).
Not basic auth on the scrape: every authenticated identity is the admin, so a scrape credential would be the admin password in plaintext.
Health stays public because the Compose healthcheck, the k8s probes and the redeploy loop read it without credentials.

**The `io.opentelemetry.usage` logger is silenced for an upstream bug.** OpenTelemetry API 1.62 logs one WARNING per process when a null context reaches `Span.fromContext`, and on Quarkus 3.x the Prometheus exemplar sampler and the MicroProfile context propagation provider both do that whenever no span is current, which includes boot (quarkusio/quarkus#55855; fixed on main for 4.0 only).
`qlawkus-observability` therefore sets the category to `ERROR`, pinned by `OpenTelemetryUsageWarningIT` on the packaged artifact.
Because that also hides a misuse of ours, `TracedToolExecutorTest.aToolCallReportsNoOpenTelemetryApiUsageIssue` captures the logger at `ALL` around the tool span: new code that drives the OpenTelemetry API directly needs the same guard.
Drop the silencing once the platform carries both fixes.
To see the stack traces, set the category `level` and `min-level` to `ALL`.

**Reading it back.** `qlawkus-tools-self-observe` (package `tools.selfobserve`, config root `qlawkus.self-observe.*`, `enabled` default `false`) gives the agent four read-only `@QlawTool`s over the backends the telemetry lands in: `queryMetrics` (Prometheus), `findTraces` / `getTrace` (Tempo), `queryLogs` (Loki).
Its rules are the point of the module, each pinned by a test in its `deployment/`: `TelemetryHttp.get` is the only HTTP call, a `GET` that never follows redirects, with the `TransientHttpException` retry contract in [resilience.md](resilience.md), except that a request **timeout is permanent** (against a query API it means the query is too expensive, so retrying multiplies the load); base URLs come only from config and the model's query is one URL-encoded parameter value, a trace id must be hex; `QueryWindow` clamps the lookback to `max-window` and `QueryRangeGuard` refuses a PromQL/LogQL `[range]` or `offset` longer than it (and the `@` modifier), since those read past any request window; results are capped at `max-results`, a log line at 1000 chars, output at `max-chars`, a response at 4 MiB.
Every result is wrapped by `TelemetryFrame` in a `<telemetry-data source="...">` block that opens with a treat-as-data warning, with any frame marker inside the data defused, because logs carry text from chat channels; reuse it for any tool that returns third-party text.
`getTrace` summarizes spans through an attribute allow-list (`TempoResults.ATTRIBUTES`), so tool arguments never reach the model even when tracing records them.
The Compose stacks turn it on against the `lgtm` service.

User-facing catalog: `site/content/operations.adoc` under "What the agent measures about itself" and "The agent reading its own telemetry".
The reference Grafana dashboard is `examples/observability/grafana/qlawkus-agent.json`; `DashboardMetricsTest` fails when a panel queries an unpublished series or a `qlawkus.*` constant in `AgentMeters` is charted nowhere, so **adding a meter means adding a panel**.
