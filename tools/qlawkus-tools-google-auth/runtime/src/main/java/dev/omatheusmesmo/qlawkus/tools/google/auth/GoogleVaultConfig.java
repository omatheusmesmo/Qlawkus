package dev.omatheusmesmo.qlawkus.tools.google.auth;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "qlawkus.google.vault")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface GoogleVaultConfig {

    /**
     * Whether the Google credential vault is enabled.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Passphrase used to derive the AES-256 encryption key via Argon2id.
     * Required when vault is enabled; ignored otherwise.
     */
    Optional<String> encryptionPassphrase();

    /**
     * Interval in seconds between access token renewal checks.
     */
    @WithDefault("3600")
    int renewalIntervalSeconds();
}
