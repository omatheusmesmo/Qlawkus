package dev.omatheusmesmo.qlawkus.it;

import dev.omatheusmesmo.qlawkus.dto.SessionInfo;
import dev.omatheusmesmo.qlawkus.dto.SessionOutput;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tool.shell.InteractiveShellTool;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/api/test/pty")
@Authenticated
public class PtyTestResource {

    @Inject
    @ClawTool
    InteractiveShellTool interactiveShellTool;

    @POST
    @Path("/start")
    public Response startSession(@QueryParam("cmd") String cmd, @QueryParam("workdir") String workdir) {
        try {
            String sessionId = interactiveShellTool.startSession(cmd, workdir);
            return Response.ok("{\"sessionId\":\"" + sessionId + "\"}").build();
        } catch (UnsupportedOperationException e) {
            return Response.ok("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("/read/{sessionId}")
    public Response readSession(@PathParam("sessionId") String sessionId, @QueryParam("offset") Integer offset) {
        SessionOutput output = interactiveShellTool.readSession(sessionId, offset);
        return Response.ok(output).build();
    }

    @POST
    @Path("/send/{sessionId}")
    public Response sendInput(@PathParam("sessionId") String sessionId, @QueryParam("input") String input) {
        String result = interactiveShellTool.sendInput(sessionId, input);
        return Response.ok("{\"result\":\"" + result + "\"}").build();
    }

    @DELETE
    @Path("/close/{sessionId}")
    public Response closeSession(@PathParam("sessionId") String sessionId) {
        try {
            String result = interactiveShellTool.closeSession(sessionId);
            return Response.ok("{\"result\":\"" + result + "\"}").build();
        } catch (UnsupportedOperationException e) {
            return Response.ok("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("/list")
    public Response listSessions() {
        List<SessionInfo> sessions = interactiveShellTool.listSessions();
        return Response.ok(sessions).build();
    }
}
