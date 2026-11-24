# Skills

Procedural memory and the remote Skill Hub.

Behavior and configuration are documented for users in `site/content/skills.adoc`, which is the source of truth. This page keeps the implementation rationale and the traps worth knowing before changing the code.

Skills are the agent's **procedural** memory (how to do a recurring task), complementing the declarative memory in [cognition-memory.md](cognition-memory.md).
Each skill is a `SKILL.md` file (YAML frontmatter `name` + `description` + Markdown body).
Same discipline as memory: the index is **injected, not searched** - `SoulEngine` adds a `## Skills` block (name + description only, via `SkillIndexRenderer`, capped by `qlawkus.skills.max-injected`) every turn; the full body is loaded on demand with the `viewSkill` tool (progressive disclosure).

- **SPI**: `skill.SkillStore` (interface) with `Skill`/`SkillSummary` records.
  SKILL.md **parsing is delegated to the upstream `dev.langchain4j.skills` loaders** (`FileSystemSkillLoader`, agentskills.io spec, BOM-managed `dev.langchain4j:langchain4j-skills`); `SkillFrontmatter` owns only the **write** side (rendering a `Skill` back to frontmatter + body), which upstream does not provide.
  The backend is selected at **build time** via `@IfBuildProperty` on `qlawkus.cognition.backend` (`markdown` | `pgvector` | `hybrid`): `MarkdownSkillStore` (`@DefaultBean`, no DB, shared file logic in `MarkdownSkillFiles`), `store.pg.PgSkillStore` (the `skill` table, `V6` migration), `store.pg.HybridSkillStore` (files source-of-truth + pg mirror).
  Reads from all `qlawkus.skills.roots` (first wins); writes only to the first, owned root (default `~/.qlawkus/skills`).
- **Tools** (in `AgentService`): `ViewSkillTool` (load body), `ManageSkillTool` (`createOrUpdateSkill` / `deleteSkill`).
- **Auto-distill**: `SkillExtractorObserver` mines a reusable skill from each `ChatCompletedEvent` (gate `qlawkus.skills.extractor.enabled`), mirroring `SemanticExtractorObserver`.
- **Curation**: `SkillCurationJob` removes redundant skills (scheduled + `POST /api/admin/skills/curate`).
- **Lifecycle**: `viewSkill` calls `recordUse`; `SkillLifecycleJob` ages unused skills `ACTIVE -> STALE -> ARCHIVED` (config `qlawkus.skills.lifecycle.*`, scheduled + `POST /api/admin/skills/lifecycle`).
  Archived skills leave the injected index but stay loadable; pinned skills (`POST /api/admin/skills/{name}/pin`) never transition.
  Telemetry: pg columns (migration `V7`) for pgvector/hybrid, a `.qlawkus-usage.json` sidecar for markdown.
  `SkillUsageJson` maps the sidecar field by field through Jackson's tree model, so it needs no native reflection registration (a reflective read fails every time in the native image); `SkillUsageJsonTest` pins that every record component is covered and that files the reflective mapper wrote still read.
- **Admin REST**: `GET/DELETE /api/admin/skills`, `GET /api/admin/skills/{name}`, `POST /api/admin/skills/{curate,lifecycle}`, `POST /api/admin/skills/{name}/pin`.
- **Bundled skills (build-time)**: extensions/apps ship read-only skills as classpath resources under `META-INF/qlawkus-skills/<name>/SKILL.md`.
  `ClientProcessor.bundledSkills` (a `@BuildStep` + `SkillsRecorder`) scans and parses them (via the same `FileSystemSkillLoader`) at **augmentation** and bakes them into the synthetic `BundledSkills` bean - no runtime classpath scanning.
  Stores merge bundled (read-only) with owned skills; owned wins on a name clash.
  The bundling **machinery** lives in `client`; curated default skill **content** is a distribution concern and should ship from `app`, not `client`.
- **Remote registry (Skill Hub)**: everything above is **local** (read/write SKILL.md files, offline).
  The **outward-facing** registry client lives in the optional `qlawkus-tools-skill-hub` extension (package `tools.skillhub`), kept out of `client` so a locked-down distribution can omit the network capability at the module level (not just a config flag).
  SPI `SkillHub { search(query, limit); install(source); discover(source); installCandidate(candidate); publish(skill) }`, default impl `HttpSkillHub` (`@DefaultBean`; plain `java.net.http` + Jackson, **no external CLI** - jskills/skills.sh both need extra tooling installed, so the behavior is replicated natively instead).
  Tools `searchSkillHub`/`installSkill`/`listSkillsInSource`/`installSkillsFromSource`/`installSkillFromText`/`updateSkill`/`listSkillSources`/`publishSkill` are `@QlawTool` (discovered cross-module by `ClientProcessor`'s `@QlawTool` scan).
  Config root `qlawkus.skill-hub.*`.
  - **Search**: hits `<base-url>/api/search` plus, for each `well-known-hosts` entry, the `/.well-known/agent-skills/index.json` index (agentskills.io standard, fallback to legacy `/.well-known/skills/`). Best-effort across sources, deduped by source, capped by `max-results`.
  - **Install**: resolves an `owner/repo` slug to the raw GitHub SKILL.md (tries `main`/`master`), an `owner/repo/skill` reference (skills.sh hits carry the skill folder name as `skillId`; tries `skills/<skill>/SKILL.md`, `<skill>/SKILL.md`, then one GitHub tree lookup per branch for a nested `<skill>/SKILL.md`, and the root SKILL.md last, only when it declares the named skill) or takes a direct URL (well-known hits are direct URLs), validates the name against path traversal, saves via `SkillStore` into the owned root - so it enters the injected index next turn.
Search drops hits with no installable source (a bare host).
A failed install throws, so the tool returns an error result and `MeteredToolExecutor` counts `outcome=error`; `HttpSkillHub` logs the source and the paths tried at WARN.
Only the SKILL.md text is fetched; executable assets are never pulled or run (inert by omission).
  - **Sources**: `SkillSourceParser` normalizes whatever was pasted into a `SkillSource` (a name, a slug, a GitHub blob/tree/raw/repo link, a GitLab or Bitbucket file or folder link, a skills.sh page, a gist, a `.zip`/`.tar.gz` archive, or any https SKILL.md).
`discover` lists the skills a folder, repository, archive or gist holds; `installCandidate` saves one; `install` is the single-skill path and refuses a source that holds several.
`ArchiveSkillReader` reads zip (central directory, symlinks refused) and tar.gz entirely in memory and returns only SKILL.md text; caps come from `qlawkus.skill-hub.archive.*`, `max-skill-bytes` and `max-installs-per-call`.
Pasted text goes through `SkillMarkdown.parsePasted`.
Every network path passes `OutboundPolicy` (https only, no credentials, every resolved address public, checked again on each of up to three redirect hops); a `@DefaultBean` with a protected lenient constructor that test profiles swap in with a `@Alternative` (WireMock is plain http on loopback).
The response is read under a size cap through one `@Retry`-annotated `exchange` call.
  - **Provenance**: `SkillStore.recordOrigin`/`originOf` (defaults are no-ops) store `SkillOrigin(source, candidate, installedEpochMs)` in the `.qlawkus-usage.json` sidecar for markdown and hybrid (`MarkdownSkillFiles`).
`pgvector` stores none yet: it needs a `skill` column (a migration, not written).
`save` clears the origin, so an agent-authored rewrite drops the recorded source.
  - **REST**: `SkillInstallResource` (`@Authenticated`, `/api/admin/skills/install[/list|/selection|/text|/upload/list|/upload/selection|/update/{name}]`) is the admin install path; the request is the approval (no HITL round-trip), with the same caps.
Uploads are multipart and read in memory; the hub's `discoverUpload` applies `archive.max-bytes` and `max-skill-bytes`.
Failures carry a `kind` (`refused`, `limit`, `unreachable`, `disabled`); a cap refusal is `SkillLimitException`.
`SkillUpdates` is the one update path, shared by the `updateSkill` tool and the REST endpoint; an uploaded skill (`upload:` source) is refused.
The console never calls these in-process: its install panel is the separate `console-skill-hub` extension (see [console.md](console.md)), which depends on the hub and the console, never the reverse.
  - **Publish**: `publishSkill` renders an owned skill to `<publish.dir>/<name>/SKILL.md` (reusing `SkillFrontmatter.render`, now `public`).
It **never runs git** - in a container, mount `publish.dir` to a host git working copy and the owner pushes from the host (keeps credentials out of the container and honors the manual-push rule).
Opt-in via `publish.enabled` (default false).
  - **Approval**: install (every path, including a plain name and pasted text), update and publish honor `qlawkus.skill-hub.approval-mode` (`yolo` acts immediately; `hitl` holds the call as a durable owner approval, see Owner approvals; the `confirm` argument is ignored).
The approval notice names the tool and arguments before anything is written.

Config knobs: `qlawkus.cognition.backend`, `qlawkus.skills.*`, `qlawkus.skill-hub.*`.
Facts, episodic journals, skills, working memory, the persona (`Soul`) and the owner profile (`UserProfile`) are all pluggable on this same switch, and the entire Postgres backend now lives in the optional `cognition-pgvector` extension - so a build without it is database-free (validated by `integration-tests/markdown-only`).
The remote-registry side of skills is the optional `qlawkus-tools-skill-hub` extension (above).

Backend migration (in `cognition-pgvector`, so only in pgvector/hybrid builds): `store.pg.reconcile.CognitionReconciler` does a bidirectional, idempotent **union** of files <-> pgvector across all six stores (collections keyed by content hash/date/name/conversation id; singletons filled only when one side is empty, never clobbered).
It runs once at hybrid boot via `HybridReconcileStartup` (gate `qlawkus.cognition.reconcile-at-start`, default `true`) and on `POST /api/admin/cognition/reconcile`.
`CognitionMigrator` (`POST /api/admin/cognition/migrate?direction=files-to-pg|pg-to-files`) is the explicit one-directional copy that **does** overwrite the destination singletons.
This is a Qlawkus capability, not an upstream langchain4j one - it's only possible because every store keeps a parallel markdown representation as the bridge.
Covered by `CognitionReconcileTest`.
