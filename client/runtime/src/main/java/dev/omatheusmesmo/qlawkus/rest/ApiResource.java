package dev.omatheusmesmo.qlawkus.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.omatheusmesmo.qlawkus.agent.AgentEvent;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.agent.AgentStream;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.agent.ToolProgress;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.dto.ChatRequest;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.util.List;

/**
 * Chat endpoints. {@code POST /api/chat} streams server-sent events: the reply text as plain unnamed
 * {@code data:} events, exactly as before, plus named events a client may ignore - {@code tool-start}
 * and {@code tool-end} around each tool call and {@code done} at the end of the turn. Their payloads
 * are single-line JSON, and a tool's result is never in them.
 */
@Path("/api")
@Authenticated
public class ApiResource {

    static final String TOOL_START = "tool-start";
    static final String TOOL_END = "tool-end";
    static final String DONE = "done";

    @Inject
    AgentService agentService;

    @Inject
    AgentStream agentStream;

    @Inject
    AgentConfig agentConfig;

    @Inject
    ObjectMapper json;

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
    public Multi<OutboundSseEvent> chat(@Valid ChatRequest request, @Context Sse sse) {
        ToolProgress mode = request.effectiveToolProgress();
        return agentStream.stream(conversationId(), request.message())
                .onItem().transformToIterable(event -> frame(event, mode, sse));
    }

    private Iterable<OutboundSseEvent> frame(AgentEvent event, ToolProgress mode, Sse sse) {
        return switch (event) {
            case AgentEvent.Text text -> List.of(plain(sse, text.chunk()));
            case AgentEvent.ToolStart start when mode.showsTools() -> List.of(named(sse, TOOL_START,
                    toolNode(start, mode.detail(start.arguments()))));
            case AgentEvent.ToolEnd end when mode.showsTools() -> List.of(named(sse, TOOL_END,
                    toolEndNode(end)));
            case AgentEvent.Done done -> List.of(named(sse, DONE, doneNode(done)));
            case AgentEvent.ToolEvent ignored -> List.of();
        };
    }

    private OutboundSseEvent plain(Sse sse, String chunk) {
        return sse.newEventBuilder().data(chunk).build();
    }

    private OutboundSseEvent named(Sse sse, String name, ObjectNode payload) {
        try {
            return sse.newEventBuilder().name(name)
                    .data(json.writeValueAsString(payload)).build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the " + name + " event", e);
        }
    }

    private ObjectNode toolNode(AgentEvent.ToolStart start, String detail) {
        ObjectNode node = json.createObjectNode().put("id", start.id()).put("name", start.name());
        if (detail != null) {
            node.put("arguments", detail);
        }
        return node;
    }

    private ObjectNode toolEndNode(AgentEvent.ToolEnd end) {
        return json.createObjectNode().put("id", end.id()).put("name", end.name())
                .put("ok", end.ok()).put("durationMs", end.durationMs());
    }

    private ObjectNode doneNode(AgentEvent.Done done) {
        ObjectNode node = json.createObjectNode().put("toolCalls", done.toolCalls())
                .put("durationMs", done.durationMs());
        if (done.inputTokens() != null) {
            node.put("inputTokens", done.inputTokens());
        }
        if (done.outputTokens() != null) {
            node.put("outputTokens", done.outputTokens());
        }
        return node;
    }

    private String conversationId() {
        return agentConfig.sharedContext().enabled() ? ConversationId.SHARED : ConversationId.REST;
    }
}
