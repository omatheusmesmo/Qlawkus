package dev.omatheusmesmo.qlawkus.tools.google.auth;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class GoogleAuthDeliveryContext {

    @Inject
    Instance<AgentDeliveryContext> agentDeliveryContext;

    public String memoryId() {
        if (agentDeliveryContext.isUnsatisfied()) {
            return null;
        }
        return agentDeliveryContext.get().memoryId();
    }

    public String providerId() {
        if (agentDeliveryContext.isUnsatisfied()) {
            return null;
        }
        return agentDeliveryContext.get().providerId();
    }

    public String chatId() {
        if (agentDeliveryContext.isUnsatisfied()) {
            return null;
        }
        return agentDeliveryContext.get().chatId();
    }

    public boolean hasDeliveryInfo() {
        if (agentDeliveryContext.isUnsatisfied()) {
            Log.debug("GoogleAuthDeliveryContext: AgentDeliveryContext not available");
            return false;
        }
        return agentDeliveryContext.get().hasDeliveryInfo();
    }
}
