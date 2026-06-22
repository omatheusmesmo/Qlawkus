package dev.omatheusmesmo.qlawkus.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Database-free secret store. Secrets live in an encrypted PKCS12 keystore on a persistent volume
 * instead of plain environment variables. Each keystore alias is the config property name it
 * supplies, so a stored secret transparently overrides the matching environment variable and the
 * consumer reads it as an ordinary {@code @ConfigProperty} with no code change. The keystore source
 * is lenient when the file is absent, so the agent boots normally until a keystore exists.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.secrets")
public interface SecretsConfig {

    /**
     * Filesystem path to the PKCS12 keystore that backs the secret store. Mount this path on a
     * persistent volume in a container so secrets survive restarts. Defaults to
     * {@code ~/.qlawkus/secrets.p12}.
     */
    @WithDefault("${user.home}/.qlawkus/secrets.p12")
    String keystorePath();

    /**
     * Password that unlocks the secret keystore. Provide it through the environment (for example
     * {@code QLAWKUS_SECRETS_KEYSTORE_PASSWORD}) and never commit it. Empty by default so a fresh
     * install with no keystore boots without requiring it.
     */
    Optional<String> keystorePassword();
}
