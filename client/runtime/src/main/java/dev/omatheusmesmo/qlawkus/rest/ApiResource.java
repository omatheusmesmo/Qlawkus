package dev.omatheusmesmo.qlawkus.rest;

import dev.langchain4j.data.message.ChatMessage;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.cognition.ChatCompletedEvent;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.dto.ChatRequest;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api")
@Authenticated
public class ApiResource {

    @Inject
    AgentService agentService;

    @Inject
    WorkingMemoryStore memoryStore;

    @Inject
    Event<ChatCompletedEvent> eventEmitter;

    @Inject
    AgentConfig agentConfig;

    @POST
    @Path("/chat/sync")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String chatSync(@Valid ChatRequest request) {
        return agentService.chatSync(conversationId(), request.message());
    }

    @POST
    @Path("/chat")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> chat(@Valid ChatRequest request) {
        String conversationId = conversationId();
        return agentService.chat(conversationId, request.message())
            .onCompletion().invoke(() -> {
                try {
                    List<ChatMessage> messages = memoryStore.getMessages(conversationId);
                    Log.infof("Firing ChatCompletedEvent with %d messages from memoryId=%s",
                            messages.size(), conversationId);
                    eventEmitter.fireAsync(new ChatCompletedEvent(messages));
                } catch (Exception e) {
                    Log.errorf(e, "Failed to fire ChatCompletedEvent");
                }
            });
    }

    private String conversationId() {
        return agentConfig.sharedContext().enabled() ? ConversationId.SHARED : ConversationId.REST;
    }
}
