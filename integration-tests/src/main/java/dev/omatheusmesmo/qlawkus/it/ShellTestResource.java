package dev.omatheusmesmo.qlawkus.it;

import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.dto.EnvironmentResult;
import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tool.shell.ShellTool;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.Map;

@Path("/api/test/shell")
@Authenticated
public class ShellTestResource {

    @Inject
    @ClawTool
    ShellTool shellTool;

    @GET
    @Path("/run")
    public Response runCommand(@QueryParam("cmd") String cmd) {
        CommandResult result = shellTool.runCommand(cmd, null, null);
        return Response.ok(result).build();
    }

    @GET
    @Path("/env")
    public Response listEnvironment() {
        EnvironmentResult result = shellTool.listEnvironment(false);
        return Response.ok(result).build();
    }

    @GET
    @Path("/security")
    public Response checkSecurity(@QueryParam("cmd") String cmd) {
        SecurityResult result = shellTool.checkSecurity(cmd, null);
        return Response.ok(result).build();
    }

    @GET
    @Path("/available")
    public Response isCommandAvailable(@QueryParam("cmd") String cmd) {
        boolean available = shellTool.isCommandAvailable(cmd);
        return Response.ok(Map.of("available", available)).build();
    }
}
