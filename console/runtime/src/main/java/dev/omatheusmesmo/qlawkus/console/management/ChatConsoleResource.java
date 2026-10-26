package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.console.ConsoleConfig;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The chat page: a server-rendered shell whose script talks to the existing {@code POST /api/chat}
 * SSE endpoint from the browser. Nothing about the conversation is held here, so the page shares
 * whatever working memory that endpoint resolves (the shared conversation when
 * {@code qlawkus.agent.shared-context} is on, the REST one otherwise).
 *
 * <p>The tool-progress mode comes from {@code qlawkus.console.chat.tool-progress}. The page sends it
 * with each message, so the endpoint itself leaves out whatever the mode hides.
 */
@Path("/console/chat")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class ChatConsoleResource {

    static final String CHAT_ENDPOINT = "/api/chat";

    private final Template chatTemplate;
    private final ConsoleConfig config;

    public ChatConsoleResource(@Location("console/chat.html") Template chatTemplate, ConsoleConfig config) {
        this.chatTemplate = chatTemplate;
        this.config = config;
    }

    @GET
    public TemplateInstance page() {
        return chatTemplate.data("active", "chat")
                .data("endpoint", CHAT_ENDPOINT)
                .data("toolProgress", config.chat().toolProgress().wireName());
    }
}
