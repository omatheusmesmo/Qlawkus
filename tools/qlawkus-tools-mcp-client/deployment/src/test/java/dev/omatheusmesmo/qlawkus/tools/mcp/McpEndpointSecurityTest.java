package dev.omatheusmesmo.qlawkus.tools.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpEndpointSecurityTest {
    @Test
    void localhostAndLiteralIpv4OrIpv6LoopbackCanCarryLocalCredentials() {
        for (String host : List.of("localhost", "LOCALHOST", "127.0.0.1", "127.2.3.4", "[::1]", "[0:0:0:0:0:0:0:1]")) {
            assertDoesNotThrow(() -> McpConnectionValidator.validate(connection("http://" + host + ":8080/mcp", true)), host);
        }
    }

    @Test
    void remoteCredentialsRequireHttpsAndHostnameLookalikesNeverReceiveTheLoopbackException() {
        for (String host : List.of("mcp.example", "localhost.example", "127.0.0.1.example", "192.168.1.10", "[2001:db8::1]", "0.0.0.0")) {
            assertThrows(McpRegistryException.class,
                    () -> McpConnectionValidator.validate(connection("http://" + host + ":8080/mcp", true)), host);
        }
        assertDoesNotThrow(() -> McpConnectionValidator.validate(connection("https://mcp.example/mcp", true)));
        assertDoesNotThrow(() -> McpConnectionValidator.validate(connection("http://mcp.example/mcp", false)),
                "anonymous endpoints do not transport credentials");
    }

    private static McpConnection connection(String url, boolean credentials) {
        return new McpConnection("docs", McpTransport.HTTP, false, url, List.of(),
                credentials ? Map.of("Authorization", "qlawkus.mcp.servers.docs.headers.authorization") : Map.of(), Map.of(), null);
    }
}
