package dev.omatheusmesmo.qlawkus.console;

import dev.omatheusmesmo.qlawkus.console.onboarding.SetupState;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Serves the Qlawkus admin console: a server-rendered landing frame and a live status fragment
 * that HTMX polls. The whole resource sits behind {@link Authenticated}, reusing the app's
 * configured mechanism (Basic + Argon2id admin), so the console carries no auth of its own.
 *
 * <p>This is the C0 scaffold: the frame and the live-refresh loop, ready for the management
 * screens (memory, skills, config, scheduling) to slot in.
 */
@Path("/console")
@Authenticated
public class ConsoleResource {

    private final Template index;
    private final Template status;
    private final ConsoleStatus consoleStatus;
    private final SetupState setupState;

    public ConsoleResource(@Location("console/index.html") Template index,
                           @Location("console/status.html") Template status,
                           ConsoleStatus consoleStatus,
                           SetupState setupState) {
        this.index = index;
        this.status = status;
        this.consoleStatus = consoleStatus;
        this.setupState = setupState;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        return index.data("needsSetup", setupState.needsSetup());
    }

    @GET
    @Path("/status")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance status() {
        ConsoleStatus.Snapshot snapshot = consoleStatus.snapshot();
        return status
                .data("version", snapshot.version())
                .data("posture", snapshot.posture())
                .data("capabilities", snapshot.capabilities())
                .data("serverTime", snapshot.serverTime());
    }
}
