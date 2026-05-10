package dev.omatheusmesmo.qlawkus.it.terminal;

import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.dto.EnvironmentResult;
import dev.omatheusmesmo.qlawkus.dto.ProcessInfo;
import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tool.shell.ShellTool;
import dev.omatheusmesmo.qlawkus.tool.shell.WorkspaceConfinement;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/api/test/shell")
@Authenticated
public class ShellTestResource {

    @Inject
    @ClawTool
    ShellTool shellTool;

    @Inject
    WorkspaceConfinement workspaceConfinement;

    @GET
    @Path("/run")
    public Response runCommand(@QueryParam("cmd") String cmd, @QueryParam("timeout") Integer timeout) {
        CommandResult result = shellTool.runCommand(cmd, null, timeout);
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

    @GET
    @Path("/processes")
    public Response listActiveProcesses() {
        List<ProcessInfo> processes = shellTool.listActiveProcesses();
        return Response.ok(processes).build();
    }

    @DELETE
    @Path("/kill")
    public Response killProcess(@QueryParam("pid") long pid) {
        CommandResult result = shellTool.killProcess(pid);
        return Response.ok(result).build();
    }

    @GET
    @Path("/audit")
    public Response triggerAuditLog(@QueryParam("cmd") String cmd, @QueryParam("exitCode") int exitCode, @QueryParam("durationMs") long durationMs) {
        shellTool.auditLog(cmd, null, exitCode, durationMs);
        return Response.ok(Map.of("logged", true)).build();
    }

    @GET
    @Path("/workspace-env")
    public Response getWorkspaceEnv() {
        Map<String, String> env = workspaceConfinement.getWorkspaceEnv();
        return Response.ok(env).build();
    }

    @GET
    @Path("/reload-env")
    public Response reloadEnv() {
        Map<String, String> env = workspaceConfinement.reloadEnv();
        return Response.ok(env).build();
    }

    @GET
    @Path("/running-count")
    public Response getRunningCount() {
        int count = shellTool.getRunningCount();
        return Response.ok(Map.of("runningCount", count)).build();
    }
}
