# Scheduled tasks and heartbeat

Agent-created tasks and the owner-configured heartbeat.

Behavior and configuration are documented for users in `site/content/scheduler.adoc`, which is the source of truth. This page keeps the implementation rationale and the traps worth knowing before changing the code.

## Scheduled tasks

**Scheduled tasks** (`client/runtime/.../task`) - `TaskSchedulerTool` (`@QlawTool`: `scheduleTask`, `listScheduledTasks`, `cancelScheduledTask`) lets the agent schedule owner-requested reminders.
`DynamicTaskScheduler` keeps each agent-created task as a record in a `store.ScheduledTaskStore` (`store.markdown.MarkdownScheduledTaskStore` by default, one JSON file per task under `scheduled-tasks` in the state root; `store.pg.PgScheduledTaskStore` on the `scheduled_task` table for pgvector; `store.pg.HybridScheduledTaskStore` keeps files plus a table mirror) and each task is a Quartz job under a namespaced `qlawkus-task-` id, cancellable only by those ids.
A save happens before the task is reported as scheduled, and a cancel or one-time retirement removes the record.
`DynamicTaskRestorer` (`@Startup`, after `InstanceLock`) registers the stored tasks again at boot: a one-time task that was due while the agent was down is marked `MISSED` and never run, a cron the scheduler cannot run is `INVALID`, and both stay listed until cancelled, never retried.
A firing restores the origin conversation and the destination around `AgentRunner` on the interactive workload, then fires `WorkflowResultReadyEvent`.
Tasks carry no chat ids on the tool surface: the destination is the origin chat from a messaging conversation, else a provider name resolved through `task.OwnerChatDirectory` (the owner's most recent accepted messaging chat per provider, fed by `MessagingOrchestrator`, persisted by `store.OwnerChatStore`: a `<provider>.json` under `owner-chats` for markdown, the `owner_chat` table for pgvector, and both in hybrid.
Pgvector imports legacy files.
The file stores map `ScheduledTask` and `OwnerChat` field by field through Jackson's tree model (`ScheduledTaskJson`, `OwnerChatJson`), so those records need no native reflection registration; `PersistedStateJsonTest` pins that every component is carried and that files the reflective mapper wrote still read.
Hybrid reconciles each provider’s latest observation on startup.
Accepted Discord `/qlawkus verbose` commands record the chat too).
REST and console callers with no provider deliver to the most recent chat on any provider, and get a plain-words refusal when there is none.
The user-facing page is `site/content/scheduler.adoc`.

## Routines

`routine/` in `client` holds the weekly report and the Monday briefing (`qlawkus.routines.*`, user page section "Weekly report and Monday briefing" in `scheduler.adoc`).
They are two `@Scheduled` methods in `RoutineJobs`, not `DynamicTaskScheduler` tasks: a routine is configuration, not an agent-created record, so it needs no store, restore or cancel, and the fixed `routine-*` identities keep it apart from the `qlawkus-task-` jobs.
Both methods are always registered and `RoutineRunner.runScheduled` checks `enabled` on each firing, so the switch is a runtime toggle while the cron and zone (property expressions in the annotation) are fixed at startup.
The run mirrors a task firing: request context activated, `AgentDeliveryContext` bound to the owner's most recent chat, then `AgentRunner.chat` on the **batch** workload (nobody waits on it, and a routine must not open the breaker the owner's next message goes through).
The memory id is the one messaging uses (`ConversationId.SHARED` when shared context is on, else `provider:chatId`), so the owner's "yes, record those" lands in the conversation that holds the report.
The reply contract in `RoutinePrompts` is not replaceable by an owner prompt: a routine must say what it could not read, and a run that is blank or throws sends a notice (exception class only, never the message) rather than failing silently.
A failed run is not retried, since the scheduler does not retry and a retry could repeat tool calls that already ran.
`POST /api/admin/routines/{weekly|morning}/run` runs one now regardless of `enabled`.
Pinned by `RoutineRunnerTest`, `RoutinePromptsTest` and `RoutinesDisabledByDefaultTest`.

## heartbeat/

**`heartbeat/`** - Optional extension (runtime and deployment, capability `heartbeat`) that runs an owner-configured, bounded health check.
It persists settings and a notification ledger under the agent state root.
Its decision model receives aggregate schedule and curated-memory observations and chooses from a fixed set of read-only checks and known-owner notifications.
An optional notification SPI in `client` is implemented by `messaging`.
The Schedule console configures it through `/api/admin/heartbeat`.
See `site/content/scheduler.adoc`.
