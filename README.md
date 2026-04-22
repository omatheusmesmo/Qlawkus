# Qlawkus

> Autonomous Personal Engineering Agent built with Quarkus LangChain4j like OpenClaw, but with Quarkus

[![Status: Work in Progress](https://img.shields.io/badge/Status-Work%20in%20Progress-yellow?style=for-the-badge)](https://github.com/omatheusmesmo/Qlawkus/issues)

**This project is currently in active development.** The architecture is planned but not yet implemented. See [Issues](https://github.com/omatheusmesmo/Qlawkus/issues) for progress tracking.

---

## Overview

A single-user, production-ready autonomous agent capable of managing your schedule, analyzing code, automating Brag Documents, and creating its own tools at runtime.

---

## Design Principles

- **Isolation by Design**: Single-tenant architecture ensuring credentials and sandbox run in a 100% isolated environment
- **Dynamic Cognition (SOUL)**: The agent possesses a mutable "Soul" (Core Identity + Current State) that it can modify, allowing mood and context adaptation
- **Triple Memory**: Working (session), Episodic (daily background journal), and Semantic (facts and preferences via pgvector)
- **Autonomous Engineering**: Deep integration with `git` and `gh cli` for cloning, local testing, and autonomous code reviews
- **Safe Self-Improvement**: The agent writes, validates (AST), and compiles Groovy scripts at runtime to create new skills, gated by strict Quarkus Security policies
- **Lightweight**: Native compilation via GraalVM for ultra-fast startup, designed to run cheaply on small instances

---

## System Architecture

```
┌────────────────────────────────────────────────────────┐
│             Interface Layer (Single-User)              │
│       Telegram Webhook (Authenticated via User ID)     │
└────────────────────────┬───────────────────────────────┘
                         │
┌────────────────────────▼───────────────────────────────┐
│      Brain (ReAct Orchestrator) + SOUL Engine          │
│   Builds dynamic System Prompt based on "State"        │
└────────────┬────────────────────────────┬──────────────┘
             │                            │
┌────────────▼──────────────┐ ┌──────────▼───────────────┐
│    Cognition & Memory     │ │      ToolRegistry (CDI)  │
│  Working: PG (Session)    │ │   Life: Google Calendar  │
│  Episodic: Daily Job      │ │   Career: Brag Docs      │
│  Semantic: pgvector       │ │   Engineering: Git / CLI │
└───────────────────────────┘ │   Dynamic: Groovy Engine │
                              └──────────┬───────────────┘
                                         │
         ┌───────────────────────────────┼─────────────────────┐
         │                               │                     │
┌────────▼────────┐ ┌────────────────────▼──────────┐ ┌────────▼────────────┐
│   Integrations  │ │        Execution              │ │  Self-Improvement   │
│    OAuth2       │ │        Sandbox                │ │   CodeGenerator     │
│   GitHub SDK    │ │        Test Runner            │ │   Groovy Compiler   │
│   Google SDK    │ │        ProcessManager         │ │   AST Validator     │
└─────────────────┘ └───────────────────────────────┘ └─────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│      LLM Engine (Ollama in Dev / OpenAI in Prod)        │
└─────────────────────────────────────────────────────────┘
```

---

## Operation Flows

### Cognition & Action Flow (Code Review)

**User:** "Review my last open PR in repository X."

1. `SoulEngine` assembles the prompt: *"You are QuarkusClaw... Current focus: Engineering... User prefers clean code."*
2. `AgentService` initiates the ReAct loop:
   - *Thought:* Need to find the last PR number.
   - *Action:* `GitHubTool.getMyOpenPRs("repo-X")` → *Obs:* PR #42.
   - *Thought:* I'll checkout and run tests before analyzing.
   - *Action:* `GitTool.cloneAndCheckout("repo-X", "pr-42")` → *Obs:* Cloned to `/workspace/repo-X`.
   - *Action:* `ReviewTool.runLocalTests()` → *Obs:* Tests passed.
   - *Action:* `GitHubTool.getPRDiff("repo-X", 42)` → *Obs:* Diff returned.
   - *Thought:* Good code, but violates SOLID in file Y. I'll comment.
   - *Action:* `ReviewResponseTool.postComment(42, "I suggest extracting the interface...")`
3. **Final Answer:** "PR #42 cloned and tested (success). I left a comment on GitHub suggesting a refactoring in file Y."
4. `AchievementProcessor` (Background): Detects the action and suggests adding to Brag Document.

### Self-Improvement Flow (Groovy)

**User:** "Format tomorrow's schedule in an ASCII table."

1. Agent checks `ToolRegistry` — can list the schedule, but doesn't know how to draw ASCII tables.
2. *Thought:* I'll create a tool for drawing tables.
3. *Action:* `CodeGeneratorTool.createTool(spec: "AsciiTableTool")`
   - LLM generates Groovy code.
   - `ToolValidatorService` verifies AST (blocking reflection).
   - `ToolCompilerService` compiles to Java class.
   - Registers in `ToolRegistry`.
4. *Action:* `AsciiTableTool.draw(calendarData)` → *Obs:* Table generated.
5. **Final Answer:** Returns the rendered table.

---

## Package Structure

```
src/main/java/dev/omatheusmesmo/qlawkus/
├── ApiResource.java              # Protected SSE chat endpoint (POST /api/chat)
├── agent/
│   ├── AgentService.java         # @RegisterAiService with SoulEngine + tools
│   └── AgentLogInterceptor.java  # CDI interceptor for invocation logging
├── cognition/
│   ├── Soul.java                 # Persisted mental state (name, coreIdentity, currentState, mood)
│   ├── SoulEngine.java           # SystemMessageProvider — builds dynamic identity prompt
│   ├── Mood.java                 # Enum with 8 behavioral moods + descriptions
│   └── UpdateSelfStateTool.java  # @Tool methods for agent self-modification
└── dto/
    └── ChatRequest.java          # Input DTO for chat endpoint
```
src/main/java/dev/quarkusclaw/
├── agent/
│   ├── AgentService.java
│   └── AgentOrchestrator.java           # ReAct loop control
├── cognition/
│   ├── SoulEngine.java                  # Dynamic identity prompt builder
│ ├── Soul.java # Persisted mental state, mood, focus
│   ├── EpisodicConsolidatorJob.java     # Late-night chat summarizer
│   └── SemanticExtractorInterceptor.java # Background fact extraction
├── memory/
│   ├── ChatMessageEntity.java
│   ├── PersistentMemoryStore.java
│   └── VectorFactStore.java             # RAG with pgvector
├── tools/
│   ├── life/
│   │   └── CalendarTool.java            # Google Calendar OAuth2
│   ├── career/
│   │   └── BragDocumentTool.java        # Impact report generator
│   ├── engineering/
│   │   ├── GitHubTool.java              # gh cli wrapper
│   │   ├── GitTool.java                 # clone, checkout
│   │   └── CodeReviewTool.java          # Local test execution
│   └── selfimprovement/
│       ├── CodeGeneratorTool.java
│       ├── ToolCompilerService.java     # GroovyClassLoader
│       └── ToolValidatorService.java    # SecureASTCustomizer
├── security/
│   ├── CredentialVaultService.java      # AES encryption for local tokens
│   └── LocalSecurityPolicy.java         # Process allowlist
├── sandbox/
│   └── ProcessManager.java              # Isolate maven/npm execution
└── messaging/
    └── TelegramWebhook.java             # User ID validation
```

---

## Stack & Essential Dependencies

| Component | Extension / Technology | Primary Use |
|:----------|:----------------------|:------------|
| **LLM Core** | `quarkus-langchain4j-openai` (Prod) / `ollama` (Dev) | ReAct Engine |
| **REST** | `quarkus-rest-jackson` | JSON + SSE chat endpoint |
| **Data & Memory** | `hibernate-orm-panache`, `jdbc-postgresql` | SOUL, History |
| **Security** | `elytron-security-properties-file`, `hibernate-validator` | Basic Auth, input validation |

---

## Reference Links

- [Quarkus LangChain4j Documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/index.html)
- [Guide: Function Calling](https://docs.quarkiverse.io/quarkus-langchain4j/dev/quickstart-function-calling.html)
- [Guide: Simple RAG](https://docs.quarkiverse.io/quarkus-langchain4j/dev/quickstart-rag.html)
- [Guide: Guardrails](https://docs.quarkiverse.io/quarkus-langchain4j/dev/guardrails.html)
- [Guide: Fault Tolerance](https://docs.quarkiverse.io/quarkus-langchain4j/dev/guide-fault-tolerance.html)
- [Guide: Ollama Models](https://docs.quarkiverse.io/quarkus-langchain4j/dev/guide-ollama.html)
- [Dev Services — Automatic Testcontainers](https://quarkus.io/guides/dev-services)
- [SmallRye Fault Tolerance](https://quarkus.io/guides/smallrye-fault-tolerance)
- [GroovyClassLoader — Dynamic Compilation](https://docs.groovy-lang.org/latest/html/documentation/guide-integrating.html)

---

## Roadmap

> See [Issues](https://github.com/omatheusmesmo/Qlawkus/issues) for detailed task tracking

| Milestone | Description | Status |
|:----------|:------------|:-------|
| **M1** | Foundation, SOUL & Single-User Security | Done |
| **M2** | Cognition (Memory Engine) | Pending |
| **M3** | Productivity Integration (Google Calendar) | Pending |
| **M4** | Engineering Integration (GitHub & Git) | Pending |
| **M5** | Career Engine (Brag Document) | Pending |
| **M6** | Sandbox & Code Review | Pending |
| **M7** | Self-Improvement Engine (Groovy) | Pending |
| **M8** | Messaging Interface (Telegram) | Pending |
| **M9** | Observability & Native Build | Pending |
| **M10** | Autonomy & Job Orchestration | Pending |

---

## License

MIT License - See [LICENSE](LICENSE) for details.

---

> Built with Quarkus LangChain4j
