package dev.omatheusmesmo.qlawkus.agent;

/**
 * CDI event fired by {@link AgentStream} for every tool call of a streamed turn, so a channel that
 * does not consume the stream itself (a messaging adapter showing progress) can observe it. It is
 * fired on the thread that runs the turn and in order, so an observer must return quickly.
 *
 * @param conversationId the memory id of the conversation the turn belongs to
 * @param event the {@link AgentEvent.ToolStart} or {@link AgentEvent.ToolEnd} that happened
 */
public record ToolActivityEvent(String conversationId, AgentEvent.ToolEvent event) {
}
