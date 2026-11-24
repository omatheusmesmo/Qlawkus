# Secrets

The keystore-backed secret store.

Behavior and configuration are documented for users in `site/content/secrets.adoc`, which is the source of truth. This page keeps the implementation rationale and the traps worth knowing before changing the code.

Database-free secret store, owned by `client` (no separate module, no custom crypto).
Secrets live in a PKCS12 keystore named by the convention `qlawkus.secrets.keystore-path` (default `~/.qlawkus/secrets.p12`), unlocked by `qlawkus.secrets.keystore-password`.
The built-in SmallRye keystore source is fixed at ordinal 100 (below env) with no knob to raise it, so `client` ships its own `secrets.KeystoreSecretConfigSourceFactory` (a `ConfigSourceFactory` registered via `META-INF/services`) that reads the keystore with the plain JDK `KeyStore` API (`getKey().getEncoded()` as UTF-8 - exactly how `keytool -importpass` stores it) and republishes every entry at `qlawkus.secrets.keystore-ordinal` (default 350).
Knobs are documented by `SecretsConfig` (`@ConfigMapping(prefix="qlawkus.secrets")`), rendered into the config reference under "Secrets".

- **Alias = property name.** Each keystore alias is the config property it supplies; consumers read a plain `@ConfigProperty` (no code change). Onboard with `keytool -importpass -alias '<config.property.name>' -keystore <path> -storetype PKCS12`.
- **Write side (no keytool).** `secrets.KeystoreSecretWriter` (`@ApplicationScoped`) onboards a secret programmatically via the JDK `KeyStore` API, creating the file on first write and storing entries identically to `keytool -importpass` (PBE `SecretKeyEntry`).
  The read/write PKCS12 convention is centralized in `secrets.KeystoreSecrets` (the `ConfigSourceFactory` now delegates its read to it, so reader and writer can't drift).
  REST surface `rest.SecretsAdminResource` (`@Authenticated`, `/api/admin/secrets`): `PUT` (body `{alias,value}`, never logs the value), `GET` (alias names only), `DELETE ?alias=`.
  A missing `keystore-password` returns `409` (won't create an unprotected store); a written secret takes effect on next boot.
  Covered by `markdown-only` `SecretWriteEndpointTest` (the read-back asserts keytool-compatibility) + `SecretWriteNoPasswordTest` (the 409 guard).
- **Auth is fail-closed and pluggable.** `app/` drops the old `${API_USER_PASSWORD:qlawkus}` fallback - the bundled distro refuses to boot without a real credential (env, or the matching keystore alias); `%dev` keeps a known password for local dev only.
  Auth mechanism is the integrator's choice via standard Quarkus security: endpoints use `@Authenticated`, so adding `quarkus-oidc` / `quarkus-smallrye-jwt` to the consuming app swaps the mechanism with zero Qlawkus code (no custom auth extension - that would reinvent `quarkus-oidc`).
- **Authoritative precedence.** Published at ordinal 350: `sysprops (400) > keystore (350) > env (300) > .env (295) > application.properties (250)`.
  A stored secret wins over the matching env var, so the rule is "drop it in the keystore and it takes effect" with no stray env default able to shadow it; only `-D` system properties still override.
  Lower `qlawkus.secrets.keystore-ordinal` below 300 to let env override instead.
  Validated by `integration-tests/markdown-only` `KeystoreSecretTest` (`keystoreSecretOutranksApplicationProperties` + the absent-keystore leniency contract), which boots with no datasource.
- **Lenient.** Resolves the keystore on the filesystem then the classpath; absent file contributes nothing, so a fresh install boots on `.env` and the feature stays inert until a keystore exists.
- **Migrating consumers.** No consumer change is needed: the alias is the property the consumer already reads, and the keystore (350) outranks the `application.properties` `=${ENV:}` line (250).
  The catalog of secret properties lives in `site/content/secrets.adoc` ("What to store").
  The one non-obvious case is the LLM/embedding key: `model.LlmKindConfigSourceFactory` runs at ordinal 90 and reads `quarkus.langchain4j.openai."primary".api-key` / `LLM_API_KEY` back from the context (it only honours sources out-ranking 90), so a keystore-stored key flows through it unchanged.
  `KeystoreSecretTest.llmApiKeyResolvesFromKeystoreThroughKindFactory` pins this end to end (the value reaches the real OpenAI client's Authorization header).
- **Container.** Mount the keystore path on a persistent volume; pass `QLAWKUS_SECRETS_KEYSTORE_PASSWORD` via env. File and password stay out of the image.
- **The agent's shell never inherits the process environment.** `tool.shell.ChildEnvironment` is the single builder both `ShellTool` and `PtySessionManager` use: it starts from an empty env and adds only `qlawkus.shell.env-allowlist` names (trailing `*` = prefix, default `PATH,HOME,USER,LANG,LC_*,TZ,TERM,SHELL,JAVA_HOME,TMPDIR`), then the workspace env file, then the `qlawkus.shell.secret-env."<NAME>"` pass-through.
  That map is `Map<String, Optional<String>>` on purpose: the app wires `GH_TOKEN=${GH_TOKEN:}`, and a plain `String` value makes an unset variable an empty-string boot failure (SRCFG00040).
  Passing `GH_TOKEN` also sets command-scope git config through `GIT_CONFIG_COUNT` (an empty `credential.helper` to drop `store`/`cache`, then `!gh auth git-credential` for github.com), so nothing is written to disk.
  `tool.shell.SecretRedactor` masks every pass-through value of 8+ chars and any GitHub-token-shaped string in command output, PTY output read back and the `SHELL_CMD` audit log; use it for any new path that returns command output.
  `SecretPropertyCatalog` treats the whole `qlawkus.shell.secret-env` prefix as secret.
  Redaction stops accidental leaks only: a process that can use a credential can read it, so the token must be minimal and expiring (see `site/content/secrets.adoc`, "What reaches the agent's shell").
- **Managed alternative.** `quarkus-vault` on the classpath contributes a `CredentialsProvider` and resolves secrets the same way (ordinary config); use it when secrets are managed centrally.
- **Gotcha.** The `.p12` test fixture is a real PKCS12 keystore. The global `pre-commit` hook strips trailing whitespace on non-blacklisted files and will corrupt it (DER `EOFException` at boot); commit keystore binaries with `git commit --no-verify`.

User-facing guide: `site/content/secrets.adoc`.
