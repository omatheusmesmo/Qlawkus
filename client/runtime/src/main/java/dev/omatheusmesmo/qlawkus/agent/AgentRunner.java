package dev.omatheusmesmo.qlawkus.agent;

import dev.omatheusmesmo.qlawkus.cognition.SoulEngine;
import dev.omatheusmesmo.qlawkus.logging.FailureSummary;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Thin core wrapper around the declarative {@link QlawkusAgent}. Computes the dynamic soul
 * (system message) via {@link SoulEngine} and feeds it to the agent, so callers (messaging,
 * OAuth retry) only need {@code chat(memoryId, message)} and stay decoupled from soul/@V details.
 * This is the entry point messaging takes, so it opens the turn that its log line describes.
 */
@ApplicationScoped
public class AgentRunner {

    @Inject
    QlawkusAgent agent;

    @Inject
    SoulEngine soulEngine;

    @Inject
    AgentDeliveryContext delivery;

    @Inject
    SecretRedactor redactor;

    public String chat(String memoryId, String message) {
        AgentTurn turn = AgentTurn.open(memoryId, AgentTurn.channelOf(memoryId, delivery), false);
        FailureSummary failure = null;
        try (AgentTurn.Binding ignored = turn.bind()) {
            String soul = soulEngine.getSystemMessage(memoryId).orElse("");
            return agent.chat(memoryId, soul, message);
        } catch (RuntimeException | Error e) {
            failure = FailureSummary.of(e, redactor::redact);
            throw e;
        } finally {
            turn.close(failure == null ? "ok" : "error", failure);
        }
    }
}
