package dev.omatheusmesmo.qlawkus;

import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.dto.ChatRequest;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
@Authenticated
public class ApiResource {

  @Inject
  AgentService agentService;

  @POST
  @Path("/chat")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public Multi<String> chat(@Valid ChatRequest request) {
    return agentService.chat(request.message());
  }
}
