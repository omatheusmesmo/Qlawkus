package dev.omatheusmesmo.qlawkus.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The name check passes (the stub resolver reports a public address) while the HTTP client really
 * connects to loopback, which is what a DNS rebinding answer looks like to the policy.
 */
@QuarkusTest
class McpConnectGuardTest {
    @TempDir
    Path root;

    private McpFixture fixture(boolean allowPrivate) throws Exception {
        McpEndpointPolicy policy = new McpEndpointPolicy(allowPrivate,
                host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});
        return new McpFixture(root, policy,
                "qlawkus.mcp.allow-private-endpoints", String.valueOf(allowPrivate));
    }

    private static McpConnection connection(FakeMcpHttpServer server) {
        return new McpConnection("docs", McpTransport.HTTP, true,
                "http://localhost:" + server.server.actualPort() + "/mcp", List.of(), Map.of(), Map.of(), null);
    }

    @Test
    void aConnectionThatLandsOnAPrivateAddressIsClosedBeforeAnyRequestIsSent() throws Exception {
        try (var server = new FakeMcpHttpServer(); var fixture = fixture(false)) {
            assertThrows(McpRegistryException.class, () -> fixture.registry.put(connection(server)));
            assertTrue(server.requests.isEmpty(), "no MCP request may reach a private address");
        }
    }

    @Test
    void theOptInKeepsLoopbackServersUsable() throws Exception {
        try (var server = new FakeMcpHttpServer(); var fixture = fixture(true)) {
            assertEquals(McpConnectionState.READY, fixture.registry.put(connection(server)).state());
        }
    }
}
