package dev.omatheusmesmo.qlawkus.tools.mcp.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import dev.omatheusmesmo.qlawkus.config.SecretsConfig;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpConfig;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpConnection;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpRegistryException;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpTransport;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.runtime.configuration.DurationConverter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.config.SmallRyeConfigBuilder;
import jakarta.inject.Inject;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real Quarkus OAuth client requests against a free loopback provider, with actual PKCS12 storage. */
@QuarkusTest
@TestProfile(McpOAuthContractTest.Profile.class)
class McpOAuthContractTest {
    @Inject OidcClients clients;
    @TempDir Path root;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void clientCredentialsUseTheVaultAndCacheTokensUntilExpiry() throws Exception {
        try (var provider = new Provider()) {
            var fixture = fixture();
            var connection = connection(provider, McpOAuthGrant.CLIENT_CREDENTIALS);
            fixture.vault.setSecret(connection.oauth().clientSecretRef(), "fixture-client-secret");
            assertTrue(fixture.service.isAuthorized(connection));
            assertEquals("Bearer fixture-access-1", fixture.service.authorizationHeader(connection));
            assertEquals("fixture-client-secret", provider.requests.getFirst().get("client_secret"));
            assertEquals("client_credentials", provider.requests.getFirst().get("grant_type"));
            assertEquals("Bearer fixture-access-1", fixture.service.authorizationHeader(connection));
            assertEquals(1, provider.tokens.get());
            assertFalse(json.writeValueAsString(fixture.service.status(connection)).contains("fixture-access"));
            assertTrue(fixture.service.secretsForRedaction(connection).contains("fixture-access-1"));
        }
    }

    @Test
    void confidentialClientsCanUseBasicAuthenticationInsteadOfFormCredentials() throws Exception {
        try (var provider = new Provider()) {
            var fixture = fixture();
            var original = connection(provider, McpOAuthGrant.CLIENT_CREDENTIALS);
            var value = original.oauth();
            var connection = new McpConnection(original.id(), original.transport(), true, original.url(), List.of(), Map.of(), Map.of(), null,
                    new McpOAuth(value.grant(), value.tokenEndpoint(), null, null, value.clientId(), value.clientSecretRef(),
                            value.scopes(), null, value.resource(), McpOAuthClientAuthMethod.BASIC));
            fixture.vault.setSecret(value.clientSecretRef(), "fixture-client-secret");
            assertEquals("Bearer fixture-access-1", fixture.service.authorizationHeader(connection));
            assertEquals("Basic " + Base64.getEncoder().encodeToString("fixture-client:fixture-client-secret"
                    .getBytes(StandardCharsets.UTF_8)), provider.authorizationHeaders.getFirst());
            assertFalse(provider.requests.getFirst().containsKey("client_secret"));
        }
    }

    @Test
    void authorizationCodeUsesSingleUseOwnerBoundStateAndPkceThenSurvivesServiceRestart() throws Exception {
        try (var provider = new Provider()) {
            var fixture = fixture();
            var connection = connection(provider, McpOAuthGrant.AUTHORIZATION_CODE);
            assertFalse(fixture.service.isAuthorized(connection));
            var login = fixture.service.begin(connection, "owner");
            Map<String, String> parameters = form(URI.create(login.authorizationUrl()).getRawQuery());
            assertEquals("S256", parameters.get("code_challenge_method"));
            assertFalse(parameters.containsKey("code_verifier"));
            assertEquals("fixture-client", parameters.get("client_id"));
            assertEquals("https://resource.example/mcp", parameters.get("resource"));
            fixture.service.complete(parameters.get("state"), "fixture-code", "owner", connection);
            var tokenRequest = provider.requests.getFirst();
            assertEquals("authorization_code", tokenRequest.get("grant_type"));
            assertEquals("fixture-code", tokenRequest.get("code"));
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(tokenRequest.get("code_verifier").getBytes(StandardCharsets.UTF_8)));
            assertEquals(parameters.get("code_challenge"), challenge);
            assertTrue(fixture.service.isAuthorized(connection));
            assertThrows(McpRegistryException.class,
                    () -> fixture.service.complete(parameters.get("state"), "fixture-code", "owner", connection));
            var restarted = new McpOAuthService(clients, fixture.vault, json, fixture.config);
            assertEquals("Bearer fixture-access-1", restarted.authorizationHeader(connection));
            assertEquals(1, provider.tokens.get(), "a restart must use the encrypted token bundle without another login");
        }
    }

    @Test
    void expiredAccessTokensAreRefreshedThroughTheUpstreamClient() throws Exception {
        try (var provider = new Provider()) {
            var fixture = fixture();
            var connection = connection(provider, McpOAuthGrant.AUTHORIZATION_CODE);
            authorize(fixture, connection);
            String alias = "qlawkus.mcp.servers.docs.oauth.tokens";
            var stored = (ObjectNode) json.readTree(fixture.vault.readSecret(alias).orElseThrow());
            stored.put("expires_at", 0);
            fixture.vault.setSecret(alias, stored.toString());
            assertEquals("Bearer fixture-access-2", fixture.service.authorizationHeader(connection));
            assertEquals("refresh_token", provider.requests.getLast().get("grant_type"));
            assertEquals("fixture-refresh", provider.requests.getLast().get("refresh_token"));
        }
    }

    @Test
    void wrongOwnersAndChangedDefinitionsAreRejectedBeforeTokenRequests() throws Exception {
        try (var provider = new Provider()) {
            var fixture = fixture();
            var original = connection(provider, McpOAuthGrant.AUTHORIZATION_CODE);
            var login = fixture.service.begin(original, "owner");
            var state = form(URI.create(login.authorizationUrl()).getRawQuery()).get("state");
            assertThrows(McpRegistryException.class, () -> fixture.service.complete(state, "code", "other", original));
            var second = fixture.service.begin(original, "owner");
            var changed = new McpConnection(original.id(), original.transport(), original.enabled(),
                    "https://different-resource.example/mcp", original.command(), original.headerSecretRefs(),
                    original.environmentSecretRefs(), original.protocolVersion(), original.oauth());
            String secondState = form(URI.create(second.authorizationUrl()).getRawQuery()).get("state");
            assertThrows(McpRegistryException.class, () -> fixture.service.complete(secondState, "code", "owner", changed));
            assertEquals(0, provider.tokens.get());
        }
    }

    @Test
    void disconnectClearsLocalAuthorizationAndAttemptsBothTokenRevocations() throws Exception {
        try (var provider = new Provider()) {
            var fixture = fixture();
            var connection = connection(provider, McpOAuthGrant.AUTHORIZATION_CODE);
            authorize(fixture, connection);
            var result = fixture.service.disconnect(connection);
            assertTrue(result.disconnected());
            assertTrue(result.remoteRevoked());
            assertEquals(2, provider.revocations.size());
            assertFalse(fixture.service.isAuthorized(connection));
            assertTrue(fixture.vault.readSecret("qlawkus.mcp.servers.docs.oauth.tokens").isEmpty());
        }
    }

    @Test
    void aProviderRejectedRefreshGrantRequiresNewConsentWithoutLeakingItsDescription() throws Exception {
        try (var provider = new Provider()) {
            var fixture = fixture();
            var connection = connection(provider, McpOAuthGrant.AUTHORIZATION_CODE);
            authorize(fixture, connection);
            String alias = "qlawkus.mcp.servers.docs.oauth.tokens";
            var stored = (ObjectNode) json.readTree(fixture.vault.readSecret(alias).orElseThrow());
            stored.put("expires_at", 0);
            fixture.vault.setSecret(alias, stored.toString());
            provider.fail = true;
            provider.errorCode = "invalid_grant";
            var failure = assertThrows(McpRegistryException.class, () -> fixture.service.authorizationHeader(connection));
            assertFalse(failure.toString().contains("PRIVATE"));
            assertEquals("AUTH_REQUIRED", fixture.service.status(connection).state());
            assertTrue(fixture.vault.readSecret(alias).isEmpty());
        }
    }

    @Test
    void providerFailuresDoNotExposeItsResponseOrCredentials() throws Exception {
        try (var provider = new Provider()) {
            provider.fail = true;
            var fixture = fixture();
            var connection = connection(provider, McpOAuthGrant.CLIENT_CREDENTIALS);
            fixture.vault.setSecret(connection.oauth().clientSecretRef(), "fixture-client-secret");
            var failure = assertThrows(McpRegistryException.class, () -> fixture.service.authorizationHeader(connection));
            assertFalse(failure.toString().contains("PRIVATE"));
            assertFalse(failure.toString().contains("fixture-client-secret"));
            assertNull(failure.getCause());
        }
    }

    @Test
    void vaultReferencesCannotPointAtAnotherConnectionsOrTheLlmSecret() throws Exception {
        try (var provider = new Provider()) {
            var original = connection(provider, McpOAuthGrant.CLIENT_CREDENTIALS);
            for (String alias : List.of("qlawkus.mcp.servers.other.oauth.client-secret", "quarkus.langchain4j.openai.primary.api-key")) {
                var oauth = original.oauth();
                var invalid = new McpConnection("docs", McpTransport.HTTP, true, original.url(), List.of(), Map.of(), Map.of(), null,
                        new McpOAuth(oauth.grant(), oauth.tokenEndpoint(), null, null, oauth.clientId(), alias, List.of(), null, null));
                assertThrows(McpRegistryException.class, () -> McpOAuthValidator.validate(invalid));
            }
            assertEquals(0, provider.tokens.get());
        }
    }

    private void authorize(Fixture fixture, McpConnection connection) {
        String state = form(URI.create(fixture.service.begin(connection, "owner").authorizationUrl()).getRawQuery()).get("state");
        fixture.service.complete(state, "fixture-code", "owner", connection);
    }

    @Test
    void aTokenEndpointOnAPrivateAddressIsNeverContactedUnlessAllowed() throws Exception {
        try (var provider = new Provider()) {
            var fixture = fixture(false);
            var connection = connection(provider, McpOAuthGrant.CLIENT_CREDENTIALS);
            fixture.vault.setSecret(connection.oauth().clientSecretRef(), "fixture-client-secret");
            assertThrows(McpRegistryException.class, () -> fixture.service.authorizationHeader(connection));
            assertEquals(0, provider.tokens.get(), "the client secret must not be sent to a refused endpoint");
        }
    }

    private Fixture fixture() {
        return fixture(true);
    }

    private Fixture fixture(boolean allowPrivate) {
        var mapping = new SmallRyeConfigBuilder().withMapping(McpConfig.class).withMapping(SecretsConfig.class)
                .withConverter(Duration.class, 100, new DurationConverter())
                .withDefaultValue("qlawkus.secrets.keystore-path", root.resolve("secrets.p12").toString())
                .withDefaultValue("qlawkus.secrets.keystore-password", "fixture-vault-password")
                .withDefaultValue("qlawkus.mcp.allow-private-endpoints", Boolean.toString(allowPrivate))
                .withDefaultValue("qlawkus.mcp.discovery-timeout", "3s").build();
        McpConfig config = mapping.getConfigMapping(McpConfig.class);
        var vault = new KeystoreSecretWriter(mapping.getConfigMapping(SecretsConfig.class));
        return new Fixture(new McpOAuthService(clients, vault, json, config), vault, config);
    }

    private static McpConnection connection(Provider provider, McpOAuthGrant grant) {
        boolean code = grant == McpOAuthGrant.AUTHORIZATION_CODE;
        return new McpConnection("docs", McpTransport.HTTP, true, "https://resource.example/mcp", List.of(), Map.of(), Map.of(), null,
                new McpOAuth(grant, provider.url("/token"), code ? provider.url("/authorize") : null, provider.url("/revoke"),
                        "fixture-client", code ? null : "qlawkus.mcp.servers.docs.oauth.client-secret", List.of("read"),
                        code ? "http://localhost:8742/api/admin/mcp/oauth/callback" : null, "https://resource.example/mcp"));
    }

    private static Map<String, String> form(String query) {
        Map<String, String> values = new HashMap<>();
        for (String pair : query.split("&")) {
            String[] field = pair.split("=", 2);
            values.put(URLDecoder.decode(field[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(field.length == 2 ? field[1] : "", StandardCharsets.UTF_8));
        }
        return values;
    }

    private record Fixture(McpOAuthService service, KeystoreSecretWriter vault, McpConfig config) {}

    private static final class Provider implements AutoCloseable {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final AtomicInteger tokens = new AtomicInteger();
        final List<Map<String, String>> requests = new CopyOnWriteArrayList<>();
        final List<Map<String, String>> revocations = new CopyOnWriteArrayList<>();
        final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
        volatile boolean fail;
        volatile String errorCode = "provider_error";

        Provider() throws Exception {
            server.setExecutor(workers);
            server.createContext("/token", exchange -> {
                requests.add(form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                authorizationHeaders.add(authorization == null ? "" : authorization);
                int number = tokens.incrementAndGet();
                String response = fail ? "{\"error\":\"" + errorCode + "\",\"error_description\":\"PRIVATE-provider-data\"}"
                        : "{\"access_token\":\"fixture-access-" + number
                        + "\",\"refresh_token\":\"fixture-refresh\",\"expires_in\":300,\"token_type\":\"Bearer\"}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(fail ? 400 : 200, bytes.length);
                try (var body = exchange.getResponseBody()) { body.write(bytes); }
            });
            server.createContext("/revoke", exchange -> {
                revocations.add(form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
        }

        String url(String path) { return "http://127.0.0.1:" + server.getAddress().getPort() + path; }
        @Override public void close() { server.stop(0); workers.shutdownNow(); }
    }

    public static class Profile implements QuarkusTestProfile {
        @Override public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.keycloak.devservices.enabled", "false", "quarkus.http.test-port", "0", "quarkus.management.test-port", "0",
                    "qlawkus.agent.state.root", "target/oauth-contract/state", "qlawkus.agent.facts.root", "target/oauth-contract/facts",
                    "qlawkus.agent.episodic.root", "target/oauth-contract/journals", "qlawkus.agent.working-memory.root", "target/oauth-contract/working",
                    "qlawkus.skills.roots", "target/oauth-contract/skills", "quarkus.langchain4j.openai.api-key", "test-key");
        }
    }
}
