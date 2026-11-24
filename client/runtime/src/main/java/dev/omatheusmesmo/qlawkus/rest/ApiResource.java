package dev.omatheusmesmo.qlawkus.rest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentEvent;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.agent.AgentStream;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.agent.ToolProgress;
import dev.omatheusmesmo.qlawkus.attachment.AttachmentChat;
import dev.omatheusmesmo.qlawkus.attachment.AttachmentRejected;
import dev.omatheusmesmo.qlawkus.attachment.ChatAttachment;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.dto.ChatRequest;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

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
    static final String REST_PROVIDER = "rest";

    @Inject
    AgentService agentService;

    @Inject
    AgentStream agentStream;

    @Inject
    AgentConfig agentConfig;

    @Inject
    Instance<AttachmentChat> attachmentChat;

    @Inject
    AgentDeliveryContext delivery;

    @Inject
    SecurityIdentity identity;

    @Inject
    ObjectMapper json;

    @POST
    @Path("/chat/sync")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String chatSync(@Valid ChatRequest request) {
        String conversation = conversationId();
        AgentDeliveryContext.Snapshot previous = delivery.snapshot();
        delivery.restore(restOrigin(conversation));
        try {
            return agentService.chatSync(conversation, request.message());
        } finally {
            delivery.restore(previous);
        }
    }

    @POST
    @Path("/chat")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<OutboundSseEvent> chat(@Valid ChatRequest request, @Context Sse sse) {
        ToolProgress mode = request.effectiveToolProgress();
        String conversation = conversationId();
        return agentStream.stream(conversation, request.message(), restOrigin(conversation))
                .onItem().transformToIterable(event -> frame(event, mode, sse));
    }

    @POST
    @Path("/chat")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Blocking
    public Multi<OutboundSseEvent> chatWithAttachments(
            @HeaderParam("X-Qlawkus-Attachments") String attachmentRequest,
            @RestForm("message") String message,
            @RestForm("toolProgress") String toolProgress,
            @RestForm("attachments") List<FileUpload> uploads,
            @Context Sse sse) {
        // Multipart is a browser-simple content type. Require an explicit, non-simple header
        // so a cross-origin form cannot submit this request with cached Basic authorization.
        if (!"1".equals(attachmentRequest)) {
            throw attachmentError(Response.Status.BAD_REQUEST, "Set X-Qlawkus-Attachments to 1.");
        }
        requestedToolProgress(toolProgress);
        if (!attachmentChat.isResolvable()) {
            throw attachmentError(Response.Status.SERVICE_UNAVAILABLE, "Attachment analysis is unavailable in this distribution.");
        }
        if (message != null && message.length() > ChatAttachment.MAX_QUESTION_CHARS) {
            throw attachmentError(Response.Status.BAD_REQUEST, "The attachment question can contain up to 16,000 characters.");
        }
        if (uploads == null || uploads.isEmpty() || uploads.size() > ChatAttachment.MAX_COUNT) {
            throw new AttachmentRejected("Send between one and three attachments per message.");
        }
        List<ChatAttachment> attachments = uploads.stream()
                .map(upload -> new ChatAttachment(upload.contentType(), upload.size(), () -> readUpload(upload)))
                .toList();
        long start = System.nanoTime();
        String reply = attachmentChat.get().answer(message, attachments);
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return Multi.createFrom().items(plain(sse, reply), named(sse, DONE,
                doneNode(new AgentEvent.Done(null, null, 0, duration))));
    }

    @ServerExceptionMapper
    public Response attachmentRejected(AttachmentRejected failure) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity(failure.getMessage())
                .build();
    }

    /**
     * Reads the optional {@code toolProgress} form field with the vocabulary the JSON body uses, so an
     * unknown name is refused the same way. The analysis runs outside the tool loop and emits no tool
     * events yet, so the mode has nothing to hide today; reading it keeps both request shapes one
     * contract for the day this path streams tool activity.
     */
    private static ToolProgress requestedToolProgress(String name) {
        try {
            return ToolProgress.parse(name);
        } catch (IllegalArgumentException unknown) {
            throw attachmentError(Response.Status.BAD_REQUEST,
                    "toolProgress must be one of off, status, preview or verbose.");
        }
    }

    private static byte[] readUpload(FileUpload upload) {
        try (var stream = Files.newInputStream(upload.uploadedFile())) {
            return ChatAttachment.readBounded(stream);
        } catch (IOException unavailable) {
            throw new AttachmentRejected("Could not read the uploaded attachment.");
        }
    }

    private static WebApplicationException attachmentError(Response.Status status, String message) {
        return new WebApplicationException(Response.status(status).type(MediaType.TEXT_PLAIN).entity(message).build());
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
        } catch (JacksonException e) {
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

    private AgentDeliveryContext.Snapshot restOrigin(String conversation) {
        return new AgentDeliveryContext.Snapshot(conversation, REST_PROVIDER, null, identity.getPrincipal().getName());
    }

    private String conversationId() {
        return agentConfig.sharedContext().enabled() ? ConversationId.SHARED : ConversationId.REST;
    }
}
