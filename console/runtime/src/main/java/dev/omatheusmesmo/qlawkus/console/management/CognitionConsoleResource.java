package dev.omatheusmesmo.qlawkus.console.management;

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
 * The cognition management page. This is the decoupled case: reconcile and migrate live in the
 * optional {@code cognition-pgvector} extension, which the console must not depend on. So the page
 * only drives the {@code /api/admin/cognition} endpoints over HTTP, and it decides whether to show
 * them by reading the baked composition manifest (via {@link SetupState}), not by importing anything
 * from that module - {@code cognition.pgvector} in the manifest means the endpoints are compiled in.
 */
@Path("/console/cognition")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class CognitionConsoleResource {

    private static final String CAPABILITY = "cognition.pgvector";

    private final Template cognition;
    private final SetupState setupState;

    public CognitionConsoleResource(@Location("console/cognition.html") Template cognition, SetupState setupState) {
        this.cognition = cognition;
        this.setupState = setupState;
    }

    @GET
    public TemplateInstance page() {
        return cognition
                .data("active", "cognition")
                .data("available", setupState.capabilityBaked(CAPABILITY));
    }
}
