package dev.omatheusmesmo.qlawkus.agent;

import dev.omatheusmesmo.qlawkus.cognition.SoulEngine;
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

    public String chat(String memoryId, String message) {
        AgentTurn turn = AgentTurn.open(memoryId, AgentTurn.channelOf(memoryId, delivery), false);
        String outcome = "error";
        try (AgentTurn.Binding ignored = turn.bind()) {
            String soul = soulEngine.getSystemMessage(memoryId).orElse("");
            String reply = agent.chat(memoryId, soul, message);
            outcome = "ok";
            return reply;
        } finally {
            turn.close(outcome);
        }
    }
}
