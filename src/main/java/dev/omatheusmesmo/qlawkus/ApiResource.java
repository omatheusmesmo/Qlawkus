package dev.omatheusmesmo.qlawkus;

import dev.omatheusmesmo.qlawkus.agent.AgentService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
@Authenticated
public class ApiResource {

    @Inject
    AgentService agentService;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "hello";
    }

    @POST
    @Path("/chat")
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(String message) {
        return agentService.chat(message);
    }
}
