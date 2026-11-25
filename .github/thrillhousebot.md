## Review priorities
1. A claim that something fails at runtime must name a caller that reaches it. Trace who
   injects or calls the code before reporting it, and say which backend or profile it runs in.
2. Behavior changes need a test that failed first and pins the exact bug.
3. Docs ship in the same commit as the change they describe.

## Known gotchas
- The cognition stores are chosen at boot by `CognitionStoreSelector`. The Postgres and hybrid
  stores are `@Typed(Self.class)`, so on a markdown boot they are never created. Code that is
  only reachable from them does not run without a database. Beans that can exist on every boot
  inject `Instance<...>` and call `get()` lazily, or guard on `CognitionBackendState.usesPostgres()`.
- `quarkus.datasource.active`, `quarkus.hibernate-orm.active` and `quarkus.flyway.active` are
  emitted by `StoreBackendConfigSourceFactory`: false for markdown, true for pgvector and hybrid.
  A value that is not a backend is deliberately left alone so the enum conversion fails first.
- `docs/modules/ROOT/**` and `site/content/includes/**` are generated. Only `site/content/`
  is edited by hand.
- Java comments are Javadoc only (no `//`), with no inline fully qualified class names.
- Prose avoids em and en dashes.
