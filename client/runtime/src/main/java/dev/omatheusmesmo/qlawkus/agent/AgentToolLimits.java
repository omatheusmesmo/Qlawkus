package dev.omatheusmesmo.qlawkus.agent;

/**
 * The single ceiling on sequential tool calls in one turn. {@link AgentService} takes it as an annotation
 * attribute; the declarative {@link QlawkusAgent} cannot, so it reads the same value through
 * {@link #MAX_TOOL_EXECUTIONS_PROPERTY}, which {@link AgentToolLimitConfigSourceFactory} publishes from this
 * constant. Both entry points therefore share one number.
 */
public final class AgentToolLimits {

    public static final int MAX_SEQUENTIAL_TOOL_INVOCATIONS = 100;

    public static final String MAX_TOOL_EXECUTIONS_PROPERTY = "quarkus.langchain4j.ai-service.max-tool-executions";

    private AgentToolLimits() {
    }
}
