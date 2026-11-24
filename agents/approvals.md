# Owner approvals

The opt-in approval gate for tool calls.

Behavior and configuration are documented for users in `site/content/approvals.adoc`, which is the source of truth. This page keeps the implementation rationale and the traps worth knowing before changing the code.

## Owner approvals (`client/runtime/.../approval`)

**Owner approvals (`client/runtime/.../approval`)** - Opt-in gate for the `@QlawTool` tools named in `qlawkus.approval.tools` (empty by default, so nothing changes until one is named).
`ApprovalToolExecutor` sits in `QlawToolProvider`'s chain under the context bridge and under the meters: a gated call is recorded as an `ApprovalRequest` (canonical arguments, SHA-256 binding over tool name and arguments, redacted preview, requester and conversation) and the model is told it has NOT run.
`ApprovalService` owns the state machine: every transition is a compare-and-set through the `ApprovalStore` SPI, an approval re-checks the binding before running the stored arguments exactly once, a restart turns an `APPROVED` or `EXECUTING` request into `NEEDS_REVIEW` without replaying it, and a 60s sweep expires pending requests.
The default `FileApprovalStore` writes one JSON file per request under `qlawkus.agent.state.root/approvals`; `store.pg.PgApprovalStore` (the `approval_request` table, `V11`; the decision is a compare-and-set under a row lock in `ApprovalRequestRepository`) and `store.pg.HybridApprovalStore` (files decide, the table mirrors, and a mirror failure never undoes a recorded decision) follow `qlawkus.cognition.backend`.
`ApprovalGate.pendingCount()` is the read the console overview uses.
Decisions are owner-only and never reachable from a tool: `/approve` and `/deny` in messaging (read before the model, allowlisted), `/api/admin/approvals` (`@Authenticated`), and the console page.
Approval outcomes resume the originating conversation through `ApprovalResumeListener` on the interactive workload; expiry and review notify the chat without a model turn.
Tools declared on `AgentService` and remote MCP contributor tools are not gated (resume rebuilds `@QlawTool` executors by name only).
A tool is gated by `qlawkus.approval.tools` or by any `ApprovalRequirement` bean (`ApprovalPolicy` combines both); `qlawkus-tools-skill-hub` (`SkillHubApprovalRequirement`: install, update, publish) and `qlawkus-tools-mcp-client` (`McpApprovalRequirement`: `configureMcpConnection`) request it while their `approval-mode` is `hitl`, and those tools write only inside `ApprovedExecution` (a thread-local marker `ApprovalService.runTool` sets), so a model-supplied `confirm` is ignored.
Buttons: `CdiApprovalNotifier` fires `ApprovalPromptEvent` for PENDING requests, `messaging`'s `ApprovalPromptDeliveryListener` calls `MessagingProvider.sendApprovalPrompt` (Telegram inline keyboard, Discord `ActionRow`; default false falls back to the text notice), and a press becomes the typed `/approve <id>` or `/deny <id>` from the pressing user through `MessagingOrchestrator` (`ApprovalCallbacks`), so the allowlist and the compare-and-set apply and the model is never involved.
A Telegram webhook callback needs the configured secret header (403 otherwise).
Slack has no buttons (no interactivity endpoint).
Documented in `site/content/approvals.adoc`.
