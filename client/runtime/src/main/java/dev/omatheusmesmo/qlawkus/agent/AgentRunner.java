package dev.omatheusmesmo.qlawkus.agent;

import dev.omatheusmesmo.qlawkus.cognition.SoulEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Thin core wrapper around the declarative {@link QlawkusAgent}. Computes the dynamic soul
 * (system message) via {@link SoulEngine} and feeds it to the agent, so callers (messaging,
 * OAuth retry) only need {@code chat(memoryId, message)} and stay decoupled from soul/@V details.
 */
@ApplicationScoped
public class AgentRunner {

    @Inject
    QlawkusAgent agent;

    @Inject
    SoulEngine soulEngine;

    public String chat(String memoryId, String message) {
        String soul = soulEngine.getSystemMessage(memoryId).orElse("");
        return agent.chat(memoryId, soul, message);
    }
}
