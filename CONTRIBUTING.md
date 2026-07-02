# Contributing to Qlawkus

Thanks for your interest in Qlawkus. It is a multi-module Quarkus project: a set of extensions (`client`, `tools/*`, `messaging/*`) plus a deployable distribution (`app`). This guide is for human contributors; `AGENTS.md` has the deeper build/architecture detail used by automated agents, and the documentation site (`site/`, source under `site/content`) is the user-facing reference.

## Prerequisites

- JDK 25 and Maven
- Docker (for Dev Services in dev mode, and for the container run modes)
- An `NVIDIA_AI_API_KEY` only if you want to run against a real LLM (dev mode and local Docker use Ollama)

## Versions

| What | Version |
|------|---------|
| Java | 25 |
| Quarkus platform | 3.33.2 |
| quarkus-langchain4j (Quarkiverse extension + BOM) | 1.11.2 |
| Upstream `dev.langchain4j` (transitive, BOM-managed) | 1.16.2 (beta modules `langchain4j-skills`/`-pgvector`/`-agentic`: 1.16.2-beta26) |

The Quarkiverse **extension** version (`1.11.2`) and the upstream **library** version (`1.16.2`) are **different namespaces** - they are not the same number by coincidence; they track separately. The platform and extension versions are set in the root `pom.xml` (`quarkus.platform.version`, `quarkus-langchain4j.version`); the upstream `dev.langchain4j:*` versions are managed transitively by `quarkus-langchain4j-bom`, so add those dependencies **without** a `<version>`. Verify with `mvn dependency:tree -pl client/runtime -Dincludes='dev.langchain4j'`.

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

Full build: `mvn clean install`. To run the containerized stack instead, `./run.sh <local|prod|native>` builds the whole reactor in-container (no host JDK/Maven needed) and starts it; see `./run.sh --help` or the site for details.

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
- **Adding a tool**: annotate a CDI bean with `@QlawTool` + `@ApplicationScoped`; `@Tool` methods are auto-discovered at build time. New tool modules must also be added to `app/pom.xml`.
- **Declaring a capability**: an optional extension that the `agent.yml` manifest should be able to compose in or out must announce its capability name in its `src/main/resources/META-INF/quarkus-extension.yaml`:

  ```yaml
  metadata:
    qlawkus:
      capability: "messaging.discord"
  ```

  The `qlawkus-maven-plugin` reads that key to map the capability to the module's Maven coordinates. Omit it and the module is treated as always-present skeleton, so the manifest can never toggle it. Namespace names where natural, and reuse the same name across modules that should move as a unit (e.g. the Google modules all declare `google-workspace`).
- **Code style**: constructor injection for required dependencies; DTOs at API boundaries; self-documenting code over comments.
- **Commits**: Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, ...), small and atomic.
- **Branches/merges**: feature branches; integrate by **rebase** (no merge commits).

## Working on the extensions

`client/`, `tools/*`, and `messaging/*` are full Quarkus extensions (a `runtime/` + `deployment/` pair each), not plain libraries. A few rules keep that pattern healthy:

- `deployment/` may depend on `runtime/`; **`runtime/` must never depend on `deployment/`** (the build enforces this).
- Build-time work (annotation scanning, bytecode generation) lives in `deployment/` `@BuildStep` processors and reads the Jandex index instead of loading classes. Keep runtime startup thin.
- Anything reflective in native mode (DTOs, models) must be registered for reflection - `client`'s `ClientProcessor` already does this for `dev.omatheusmesmo.qlawkus.dto`; new reflective types follow the same path. Validate native builds with `mvn verify -Pnative`.
- Before adding a custom build item, check whether one already exists: [all build items](https://quarkus.io/guides/all-builditems).

If you only need to share CDI beans, typed `@ConfigMapping` config, or basic reflection and no build-time augmentation, a plain JAR with Jandex indexing may be enough - prefer that over a new full extension when it fits.

`AGENTS.md` ("Extension Development") has the deeper rule set (bootstrap phases, recorders, build items, CDI registration).

### Reference links

Official Quarkus documentation:

- [Writing Your Own Extension](https://quarkus.io/guides/writing-extensions) - canonical guide
- [All build items reference](https://quarkus.io/guides/all-builditems) - the extension SPI
- [Adding extensions to the Quarkus ecosystem / Quarkiverse](https://github.com/quarkusio/quarkus/wiki/Adding-extensions-to-the-Quarkus-ecosystem)

Community tutorials and walkthroughs:

- [Quarkus: Greener, Better, Faster, Stronger](https://jtama.github.io/posts/quarkus-greener-better-faster-stronger/) (Jérôme Tama) - Dev Services, annotation transform, recorders ([source](https://github.com/jtama/quarkus-extension-demo))
- [How NOT to Create a Quarkus Extension](https://www.loicmathieu.fr/wordpress/informatique/quarkus-tip-comment-ne-pas-creer-une-extension-quarkus/) (Loïc Mathieu) - when a simple JAR + Jandex is enough instead
- [Developing a Quarkus Extension](https://matheuscruz.dev/2024/01/12/developing-a-quarkus-extension/) (Matheus Cruz) - recorders, Gizmo, Jandex scanning ([source](https://github.com/mcruzdev/quarkus-useful))
- [Creating a Quarkus Extension](https://blog.sebastian-daschner.com/entries/creating-a-quarkus-extension) (Sebastian Daschner) - video walkthrough ([source](https://github.com/sdaschner/blink-extension))
- [How to Implement a Quarkus Extension](https://www.baeldung.com/quarkus-extension-java) (Baeldung) - Liquibase extension tutorial

Resource collections:

- [Quarkus Extensions Resources](https://hollycummins.com/quarkus-extensions-resources/) (Holly Cummins) - curated guides, talks, and posts

## Documentation

There is **one source of truth and one generated mirror**:

- `site/content/*.adoc` - the hand-written pages (Roq site): `index`, `architecture`, `config-reference`, `quickstart`, `messaging`, `voice`, `google-workspace`. **Edit these.**
- `site/content/includes/*.adoc` - the per-module configuration reference, **generated** from the `@ConfigMapping` JavaDoc by `quarkus-config-doc-maven-plugin` (configured in `docs/pom.xml`). Do not edit by hand; they are overwritten on every build.
- `docs/modules/ROOT/pages/*` - the Antora copy. It is a **generated mirror** of `site/content/` (the `sync-site-to-antora` execution in `docs/pom.xml` copies the whole directory at the `verify` phase). **Never edit `docs/` directly** - your change is overwritten by the next build.

So the rule is: edit only `site/content/`, then build to regenerate the includes and the Antora mirror.

```bash
mvn install -DskipTests                  # build the extensions so config metadata is available
mvn -f docs/pom.xml verify -DskipTests   # generate site/content/includes/ AND mirror site/content -> docs/
cd site && mvn quarkus:dev               # preview at http://localhost:8080
```

`config-reference.adoc` is itself hand-written: each module's generated table is surfaced with an `include::includes/<artifact>[_<prefix>].adoc[]` directive. A `@ConfigMapping(prefix = "qlawkus.foo")` interface produces a separate `qlawkus-client_qlawkus.foo.adoc` file, so **adding a new config root means adding a matching `include::` line** under the relevant section, or it will not appear on the site.

If you change configuration, run the commands above so the includes regenerate and the Antora mirror stays in sync.

## Submitting changes

Open a pull request with a clear description of the change and its motivation. Keep PRs focused. CI builds the project and runs the test matrix.
