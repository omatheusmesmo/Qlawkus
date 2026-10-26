package dev.omatheusmesmo.qlawkus.agent;

/**
 * One step of an agent turn as a channel sees it: reply text, the start or end of a tool call, and
 * the closing summary. {@link AgentStream} produces them in the order they happen.
 */
public sealed interface AgentEvent {

    /** A piece of the reply text, exactly as the model produced it. */
    record Text(String chunk) implements AgentEvent {
    }

    /** A tool call, which is always a {@link ToolStart} followed by a {@link ToolEnd}. */
    sealed interface ToolEvent extends AgentEvent permits ToolStart, ToolEnd {

        String id();

        String name();
    }

    /**
     * The agent is about to run a tool. {@code arguments} is already capped at
     * {@link ToolProgress#VERBOSE_CHARS}; a channel narrows it further with its {@link ToolProgress}.
     */
    record ToolStart(String id, String name, String arguments) implements ToolEvent {
    }

    /** The tool finished. Its result is deliberately not carried. */
    record ToolEnd(String id, String name, boolean ok, long durationMs) implements ToolEvent {
    }

    /**
     * The turn finished without error. Token counts are {@code null} when the provider did not report
     * them.
     */
    record Done(Integer inputTokens, Integer outputTokens, int toolCalls, long durationMs) implements AgentEvent {
    }
}
