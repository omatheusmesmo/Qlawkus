package dev.omatheusmesmo.qlawkus.it.langfuse;

import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.agent.AgentTurn;
import io.opentelemetry.api.trace.Tracer;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/test/turn")
public class TurnFixture {

    @Inject
    AgentService agent;

    @Inject
    Tracer tracer;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String turn(@QueryParam("id") String id) {
        String conversation = "private-conversation-fixture-" + id;
        AgentTurn turn = AgentTurn.open(conversation, "test", false);
        try (var binding = turn.bind()) {
            var span = tracer.spanBuilder("agent fixture").startSpan();
            try (var scope = span.makeCurrent()) {
                return agent.chatSync(conversation, "private-prompt-fixture");
            } finally {
                span.end();
            }
        } finally {
            turn.close("finished");
        }
    }
}
