package dev.omatheusmesmo.qlawkus.rest;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.cognition.ChatCompletedEvent;
import dev.omatheusmesmo.qlawkus.dto.ChatRequest;
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

  static final String DEFAULT_MEMORY_ID = "default";

  @Inject
  AgentService agentService;

  @Inject
  ChatMemoryStore memoryStore;

  @Inject
  Event<ChatCompletedEvent> eventEmitter;

  @POST
  @Path("/chat")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public Multi<String> chat(@Valid ChatRequest request) {
    return agentService.chat(request.message())
        .onCompletion().invoke(() -> {
          try {
            List<ChatMessage> messages = memoryStore.getMessages(DEFAULT_MEMORY_ID);
            eventEmitter.fire(new ChatCompletedEvent(messages));
          } catch (Exception e) {
            // best effort — never block the response
          }
        });
  }
}
