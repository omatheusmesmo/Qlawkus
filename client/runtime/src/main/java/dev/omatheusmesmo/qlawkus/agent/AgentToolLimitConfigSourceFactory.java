package dev.omatheusmesmo.qlawkus.agent;

import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.PropertiesConfigSource;

/**
 * Publishes {@link AgentToolLimits#MAX_SEQUENTIAL_TOOL_INVOCATIONS} as the upstream sequential tool-call limit at
 * a low ordinal, so any explicit setting in application properties, a file or the environment overrides it.
 */
public final class AgentToolLimitConfigSourceFactory implements ConfigSourceFactory {

    private static final String NAME = "QlawkusAgentToolLimits";
    private static final int ORDINAL = 100;

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        Map<String, String> limits = Map.of(AgentToolLimits.MAX_TOOL_EXECUTIONS_PROPERTY,
                String.valueOf(AgentToolLimits.MAX_SEQUENTIAL_TOOL_INVOCATIONS));
        return List.of(new PropertiesConfigSource(limits, NAME, ORDINAL));
    }
}
