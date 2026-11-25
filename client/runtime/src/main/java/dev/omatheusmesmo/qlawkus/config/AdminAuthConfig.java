package dev.omatheusmesmo.qlawkus.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Admin credential for the agent's {@code @Authenticated} endpoints, verified with Argon2id and no
 * database. The active verifier is the gated {@code Argon2AdminIdentityProvider}; turn it on with the
 * build-time flag {@code qlawkus.admin.argon2.enabled=true}. The password hash is sourced like any other
 * secret: put the PHC Argon2id hash in the keystore under the alias {@code qlawkus.admin.password-hash}
 * (or set {@code QLAWKUS_ADMIN_PASSWORD_HASH}). The keystore value outranks the environment (ordinal
 * 350), so a stored hash takes effect on restart. With no hash configured the app boots in setup mode:
 * it stays fail-closed, and the one-time first-run setup takes the password.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.admin")
public interface AdminAuthConfig {

    /**
     * Admin username presented over HTTP Basic. Defaults to {@code qlawkus}.
     */
    @WithDefault("qlawkus")
    String username();

    /**
     * Role granted to the authenticated admin identity. Defaults to {@code admin}.
     */
    @WithDefault("admin")
    String role();

    /**
     * PHC Argon2id hash of the admin password, e.g.
     * {@code $argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>}. Optional: when
     * {@code qlawkus.admin.argon2.enabled} is true and none is set, nothing authenticates and the
     * first-run setup (a one-time token printed in the agent log) takes the password and stores its hash.
     * To set it by hand instead, generate one as described in the Secrets guide and store it under the
     * keystore alias {@code qlawkus.admin.password-hash} or the environment variable
     * {@code QLAWKUS_ADMIN_PASSWORD_HASH}.
     */
    Optional<String> passwordHash();

    /**
     * Argon2id verifier toggle.
     */
    Argon2 argon2();

    interface Argon2 {

        /**
         * Turns on the database-free Argon2id admin {@code IdentityProvider}. Off by default so a
         * consumer using a different realm (for example the embedded properties realm) is untouched.
         * This is a build-time gate: it is read during augmentation to include or drop the provider
         * bean, so changing it needs a rebuild.
         */
        @WithDefault("false")
        boolean enabled();
    }
}
