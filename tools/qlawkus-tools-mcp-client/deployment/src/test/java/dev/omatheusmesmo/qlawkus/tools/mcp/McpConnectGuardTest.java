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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The name check passes (the stub resolver reports a public address for {@code localhost}) while the HTTP
 * client really connects to loopback, which is what a DNS rebinding answer looks like to the policy. The
 * connection must be closed before any MCP request is written, for a server answering with plain JSON and
 * for one answering with a server-sent event stream.
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aConnectionThatLandsOnAPrivateAddressIsClosedBeforeAnyRequestIsSent(boolean eventStream) throws Exception {
        try (var server = new FakeMcpHttpServer(); var fixture = fixture(false)) {
            server.eventStreamReplies = eventStream;
            assertThrows(McpRegistryException.class, () -> fixture.registry.put(connection(server)));
            assertTrue(server.requests.isEmpty(), "no MCP request may reach a private address");
        }
    }

    @Test
    void aHostThatResolvesToAPrivateAddressIsRefusedBeforeAnyRequestIsSent() throws Exception {
        try (var server = new FakeMcpHttpServer()) {
            McpEndpointPolicy policy = new McpEndpointPolicy(false,
                    host -> new InetAddress[] {InetAddress.getByName("10.0.0.5")});
            try (var fixture = new McpFixture(root, policy, "qlawkus.mcp.allow-private-endpoints", "false")) {
                assertThrows(McpRegistryException.class, () -> fixture.registry.put(connection(server)));
                assertTrue(server.requests.isEmpty(), "no MCP request may reach a private address");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theOptInKeepsLoopbackServersUsable(boolean eventStream) throws Exception {
        try (var server = new FakeMcpHttpServer(); var fixture = fixture(true)) {
            server.eventStreamReplies = eventStream;
            assertEquals(McpConnectionState.READY, fixture.registry.put(connection(server)).state());
            assertTrue(server.count("tools/list") > 0, "discovery must reach the allowed loopback server");
        }
    }
}
