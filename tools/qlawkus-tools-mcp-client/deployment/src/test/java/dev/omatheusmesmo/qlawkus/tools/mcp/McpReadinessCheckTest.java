package dev.omatheusmesmo.qlawkus.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuth;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuthGrant;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The readiness check reports what the registry already knows. It never opens a connection, never goes
 * DOWN because a remote server is away, and publishes nothing but connection ids and a count.
 */
@QuarkusTest
class McpReadinessCheckTest {
    private static final String UNREACHABLE = "http://127.0.0.1:1/mcp";

    @TempDir
    Path root;

    @Test
    void anEmptyRegistryIsUpWithNothingDegraded() throws Exception {
        try (var fixture = new McpFixture(root)) {
            HealthCheckResponse response = new McpReadinessCheck(fixture.registry).call();

            assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
            assertEquals(McpReadinessCheck.NAME, response.getName());
            assertFalse(response.getData().orElseThrow().containsKey("degraded"));
            assertEquals(0L, response.getData().orElseThrow().get("connections"));
        }
    }

    @Test
    void aConnectionNobodyHasContactedYetIsNotDegraded() throws Exception {
        try (var fixture = new McpFixture(root, "qlawkus.mcp.servers.docs.url", UNREACHABLE)) {
            HealthCheckResponse response = new McpReadinessCheck(fixture.registry).call();

            assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
            assertFalse(response.getData().orElseThrow().containsKey("degraded"));
            assertEquals(1L, response.getData().orElseThrow().get("connections"));
        }
    }

    @Test
    void aConnectionThatFailedIsDegradedByIdWhileTheCheckStaysUp() throws Exception {
        try (var fixture = new McpFixture(root, "qlawkus.mcp.servers.docs.url", UNREACHABLE)) {
            fixture.registry.test("docs");

            HealthCheckResponse response = new McpReadinessCheck(fixture.registry).call();

            assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
            assertEquals("docs unreachable", response.getData().orElseThrow().get("degraded"));
        }
    }

    @Test
    void aWorkingConnectionIsNotDegraded() throws Exception {
        try (var server = new FakeMcpHttpServer(); var fixture = new McpFixture(root)) {
            fixture.registry.put(new McpConnection("docs", McpTransport.HTTP, true, server.url(), List.of(),
                    Map.of(), Map.of(), null));

            HealthCheckResponse response = new McpReadinessCheck(fixture.registry).call();

            assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
            assertFalse(response.getData().orElseThrow().containsKey("degraded"));
        }
    }

    @Test
    void aDisabledConnectionIsTheOwnersChoiceNotADegradation() throws Exception {
        try (var fixture = new McpFixture(root)) {
            fixture.registry.put(new McpConnection("docs", McpTransport.HTTP, false, UNREACHABLE, List.of(),
                    Map.of(), Map.of(), null));

            HealthCheckResponse response = new McpReadinessCheck(fixture.registry).call();

            assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
            assertFalse(response.getData().orElseThrow().containsKey("degraded"));
        }
    }

    @Test
    void aConnectionWaitingForTheOwnersSignInIsDegradedByName() throws Exception {
        var oauth = new McpOAuth(McpOAuthGrant.AUTHORIZATION_CODE, "https://mcp.example/token",
                "https://mcp.example/authorize", null, "client", null, List.of(), "https://agent.example/api/admin/mcp/oauth/callback",
                null);
        try (var fixture = new McpFixture(root)) {
            fixture.registry.put(new McpConnection("sso", McpTransport.HTTP, true, "https://mcp.example/mcp",
                    List.of(), Map.of(), Map.of(), null, oauth));

            HealthCheckResponse response = new McpReadinessCheck(fixture.registry).call();

            assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
            assertEquals("sso needs sign-in", response.getData().orElseThrow().get("degraded"));
        }
    }

    @Test
    void readingTheCheckNeverContactsAServer() throws Exception {
        try (var server = new FakeMcpHttpServer();
                var fixture = new McpFixture(root, "qlawkus.mcp.servers.docs.url", server.url())) {
            McpReadinessCheck check = new McpReadinessCheck(fixture.registry);
            check.call();
            check.call();
            check.call();

            assertTrue(server.requests.isEmpty(), "a readiness poll must not open a connection");
        }
    }

    @Test
    void onlyIdsAndACountAreEverPublished() throws Exception {
        String alias = "qlawkus.mcp.servers.docs.headers.authorization";
        String token = "Bearer fixture-readiness-secret";
        try (var server = new FakeMcpHttpServer();
                var fixture = new McpFixture(root, "qlawkus.mcp.servers.gone.url", UNREACHABLE)) {
            server.requiredAuthorization = token;
            fixture.secrets.setSecret(alias, token);
            fixture.registry.put(new McpConnection("docs", McpTransport.HTTP, true, server.url(), List.of(),
                    Map.of("Authorization", alias), Map.of(), null));
            fixture.registry.test("gone");

            HealthCheckResponse response = new McpReadinessCheck(fixture.registry).call();

            Map<String, Object> data = response.getData().orElseThrow();
            assertEquals(Set.of("connections", "degraded"), data.keySet());
            String published = data.toString();
            assertFalse(published.contains(token));
            assertFalse(published.contains(alias));
            assertFalse(published.contains(UNREACHABLE));
            assertFalse(published.contains(server.url()));
        }
    }
}
