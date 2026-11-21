package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.console.ConsoleFeatures;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** The browser uses the optional extension's admin API; this module has no MCP dependency. */
@Path("/console/mcp")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class McpConsoleResource {
    private final Template template;
    private final ConsoleFeatures features;

    public McpConsoleResource(@Location("console/mcp.html") Template template, ConsoleFeatures features) {
        this.template = template;
        this.features = features;
    }

    @GET
    public TemplateInstance page() {
        if (!features.isMcp()) {
            throw new NotFoundException();
        }
        return template.data("active", "mcp");
    }
}
