package dev.omatheusmesmo.qlawkus.agent;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.cognition.VoiceResponsePreference;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@QlawTool
@ApplicationScoped
public class StreamedContextProbe {

    static final List<Observation> SEEN = new CopyOnWriteArrayList<>();
    public static final List<DeliveryObservation> DELIVERY_SEEN = new CopyOnWriteArrayList<>();

    @Inject
    AgentDeliveryContext delivery;

    @Inject
    VoiceResponsePreference voice;

    @Tool("Test probe: records the context this tool runs in. Call it when asked to probe the context.")
    public String probeContext() {
        boolean active = Arc.container().requestContext().isActive();
        SEEN.add(new Observation(delivery.chatId(), active, active && voice.isRequested()));
        return "recorded";
    }

    @Tool("Test probe: asks for a Portuguese voice reply.")
    public String requestPortugueseVoice() {
        voice.request("pt");
        return "requested";
    }

    @Tool("Test probe: records the delivery origin this tool runs with. Call it when asked to probe the delivery.")
    public String probeDelivery() {
        DELIVERY_SEEN.add(new DeliveryObservation(delivery.memoryId(), delivery.providerId(), delivery.chatId()));
        return "recorded";
    }

    record Observation(String chatId, boolean requestActive, boolean voiceRequested) {
    }

    public record DeliveryObservation(String memoryId, String providerId, String chatId) {
    }
}
