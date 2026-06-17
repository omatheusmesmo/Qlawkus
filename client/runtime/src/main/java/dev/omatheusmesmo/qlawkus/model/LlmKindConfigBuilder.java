package dev.omatheusmesmo.qlawkus.model;

import io.quarkus.runtime.configuration.ConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Registers {@link LlmKindConfigSourceFactory} with the Quarkus config. Wiring it through a
 * {@link ConfigBuilder} (registered as a build item for both static-init and run-time phases) is the
 * reliable way to add a custom config source factory in a Quarkus extension; {@code META-INF/services}
 * discovery alone does not guarantee the factory participates in the runtime config that recorders
 * validate.
 */
public class LlmKindConfigBuilder implements ConfigBuilder {

    @Override
    public SmallRyeConfigBuilder configBuilder(SmallRyeConfigBuilder builder) {
        return builder.withSources(new LlmKindConfigSourceFactory());
    }
}
