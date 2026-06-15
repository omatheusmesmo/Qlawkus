# Contributing to Qlawkus

Thanks for your interest in Qlawkus. It is a multi-module Quarkus project: a set of extensions (`client`, `tools/*`, `messaging/*`) plus a deployable distribution (`app`). This guide is for human contributors; `AGENTS.md` has the deeper build/architecture detail used by automated agents, and the documentation site (`site/`, source under `site/content`) is the user-facing reference.

## Prerequisites

- JDK 25 and Maven
- Docker (for Dev Services in dev mode, and for the container run modes)
- An `NVIDIA_AI_API_KEY` only if you want to run against a real LLM (dev mode and local Docker use Ollama)

## Project layout

| Module | Role |
|--------|------|
| `client/` | Core extension: agent, cognition/memory, REST, sandboxed shell. Not runnable alone. |
| `tools/*` | Optional tool extensions (Google Workspace, brag). |
| `messaging/*` | Messaging extension + Discord/Telegram/Slack/WhatsApp adapters. |
| `app/` | The deployable distribution that wires extensions together. The only module you run. |
| `integration-tests/*` | Per-feature integration tests. |
| `docs/` + `site/` | Config-reference generation and the Roq documentation site. |

## Build and run

`client/`, `tools/`, and `messaging/` are Quarkus **extensions**, so live reload only applies to `app/`. After changing an extension, reinstall it and restart:

```bash
mvn install -pl client -am -DskipTests   # rebuild the changed extension(s)
cd app && mvn quarkus:dev                # http://localhost:8080
```

Full build: `mvn clean install`. See `docs/` (or the site) for the Docker run modes.

## Testing

```bash
# All tests in a module
mvn test -pl client/deployment

# A single test class (the failIfNoSpecifiedTests flag avoids errors in sibling modules)
mvn test -pl client/deployment -am -Dtest=UserProfileTest -Dsurefire.failIfNoSpecifiedTests=false
```

- `*Test` = `@QuarkusTest` (in-JVM, Dev Services / WireMock).
- `*IT` = `@QuarkusIntegrationTest` (packaged + native artifact over HTTP).
- LLM-dependent tests are gated on `QlawkusTestUtils#usesLLM` and auto-skip without a real `NVIDIA_AI_API_KEY`.

Please add meaningful tests (business logic, edge cases, integration points), not trivial ones.

## Conventions

- **Configuration**: prefer typed config (`@ConfigMapping` interfaces) with a JavaDoc comment on each method. The JavaDoc becomes the description in the generated configuration reference, so well-documented config is documented config.
- **Adding a tool**: annotate a CDI bean with `@ClawTool` + `@ApplicationScoped`; `@Tool` methods are auto-discovered at build time. New tool modules must also be added to `app/pom.xml`.
- **Code style**: constructor injection for required dependencies; DTOs at API boundaries; self-documenting code over comments.
- **Commits**: Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, ...), small and atomic.
- **Branches/merges**: feature branches; integrate by **rebase** (no merge commits).

## Documentation

User-facing docs live in `site/content/*.adoc`. The per-module configuration reference is generated from `@ConfigMapping` JavaDoc:

```bash
mvn install -DskipTests                                   # so config metadata is available
mvn -f docs/pom.xml package -DskipTests                  # generate site/content/_includes
cd site && mvn quarkus:dev                                # preview at http://localhost:8080
```

If you change configuration, regenerate the reference so the docs match the code.

## Submitting changes

Open a pull request with a clear description of the change and its motivation. Keep PRs focused. CI builds the project and runs the test matrix.
