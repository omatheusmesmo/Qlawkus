package dev.omatheusmesmo.qlawkus.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuth;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuthGrant;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpEndpointPolicyTest {
    private static final Map<String, String[]> DNS = Map.of(
            "localhost", new String[] {"127.0.0.1"},
            "internal.example", new String[] {"10.1.1.1"},
            "mixed.example", new String[] {"93.184.216.34", "192.168.0.9"},
            "mcp.example", new String[] {"93.184.216.34"});

    private static McpEndpointPolicy policy(boolean allowPrivate) {
        return new McpEndpointPolicy(allowPrivate, host -> {
            String[] addresses = DNS.get(host);
            if (addresses == null) throw new UnknownHostException(host);
            InetAddress[] result = new InetAddress[addresses.length];
            for (int i = 0; i < addresses.length; i++) result[i] = InetAddress.getByName(addresses[i]);
            return result;
        });
    }

    @Test
    void loopbackPrivateLinkLocalAndMetadataTargetsAreRefusedByDefault() {
        for (String url : List.of("http://127.0.0.1:8080/mcp", "http://localhost/mcp", "http://[::1]/mcp",
                "http://10.0.0.5/mcp", "http://172.16.0.1/mcp", "http://192.168.1.1/mcp",
                "http://169.254.169.254/latest/meta-data", "http://[fd00::1]/mcp", "http://[fe80::1]/mcp",
                "http://100.64.0.1/mcp", "http://0.0.0.0/mcp", "http://[::ffff:127.0.0.1]/mcp",
                "http://internal.example/mcp")) {
            assertThrows(McpRegistryException.class, () -> policy(false).require(url), url);
        }
    }

    @Test
    void aHostnameWithAnyPrivateAnswerOrNoAnswerIsRefused() {
        assertThrows(McpRegistryException.class, () -> policy(false).require("https://mixed.example/mcp"));
        assertThrows(McpRegistryException.class, () -> policy(false).require("https://missing.example/mcp"));
    }

    @Test
    void publicTargetsPassAndTheOptInLiftsTheRestrictionWithoutResolving() {
        assertDoesNotThrow(() -> policy(false).require("https://mcp.example/mcp"));
        assertDoesNotThrow(() -> policy(false).require("http://8.8.8.8/mcp"));
        assertDoesNotThrow(() -> new McpEndpointPolicy(true, host -> {
            throw new AssertionError("an allowed policy must not resolve names");
        }).require("http://internal.example/mcp"));
    }

    @Test
    void everyServerSideEndpointOfAConnectionIsChecked() {
        var oauth = new McpOAuth(
                McpOAuthGrant.CLIENT_CREDENTIALS,
                "https://mcp.example/token", null, "http://10.0.0.7/revoke", "client",
                "qlawkus.mcp.servers.docs.oauth.client-secret", List.of(), null, null);
        var connection = new McpConnection("docs", McpTransport.HTTP, true, "https://mcp.example/mcp", List.of(),
                Map.of(), Map.of(), null, oauth);
        assertThrows(McpRegistryException.class, () -> policy(false).require(connection));
        assertDoesNotThrow(() -> policy(true).require(connection));
    }
}
