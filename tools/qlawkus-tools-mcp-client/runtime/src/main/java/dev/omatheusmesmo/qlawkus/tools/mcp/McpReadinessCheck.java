package dev.omatheusmesmo.qlawkus.tools.mcp;

import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

/**
 * Reports which MCP connections cannot serve tools right now. It reads the registry's cached view and
 * never opens a connection: readiness is polled every few seconds, and a probe that dialled remote
 * servers would turn monitoring into traffic.
 *
 * <p>The check is always UP. The MCP servers are optional tooling, so one being away is degraded, not
 * unable: taking the only agent out of rotation because an external server is down would turn that
 * server's incident into an outage here. A connection that failed, or that waits for the owner's
 * sign-in, is named under {@code degraded} as {@code <id> <reason>}, the entry the console's Health card
 * already renders. Only ids and a count are published, never an address, header, alias or token.
 *
 * <p>A connection nobody has used yet is not degraded: the registry connects lazily on the first turn,
 * so a freshly started instance has not failed anything it has not tried.
 */
@Readiness
public class McpReadinessCheck implements HealthCheck {

    static final String NAME = "qlawkus-mcp-client";

    private final McpRegistry registry;

    public McpReadinessCheck(McpRegistry registry) {
        this.registry = registry;
    }

    @Override
    public HealthCheckResponse call() {
        List<McpConnectionHealth> connections = registry.health();
        HealthCheckResponseBuilder response = HealthCheckResponse.named(NAME).up()
                .withData("connections", connections.size());
        String degraded = connections.stream()
                .filter(connection -> connection.condition().degrades())
                .map(connection -> connection.id() + " " + connection.condition().phrase())
                .collect(Collectors.joining(","));
        if (!degraded.isEmpty()) {
            response.withData("degraded", degraded);
        }
        return response.build();
    }
}
