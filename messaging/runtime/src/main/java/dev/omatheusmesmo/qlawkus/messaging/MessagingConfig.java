package dev.omatheusmesmo.qlawkus.messaging;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Map;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.messaging")
public interface MessagingConfig {

    /**
     * Per-provider configuration keyed by provider ID (e.g. telegram, discord).
     */
    Map<String, ProviderConfig> provider();

    /**
     * Configuration for a single messaging provider.
     */
    interface ProviderConfig {

        /**
         * Whether this provider is active. Defaults to true.
         */
        @WithDefault("true")
        boolean enabled();
    }
}
