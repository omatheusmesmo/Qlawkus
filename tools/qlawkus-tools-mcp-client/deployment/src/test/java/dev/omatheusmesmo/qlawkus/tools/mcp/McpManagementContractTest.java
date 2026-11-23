package dev.omatheusmesmo.qlawkus.tools.mcp;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuth;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuthGrant;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real CDI/provider and authenticated admin paths share one mutable registry. */
@QuarkusTest
@TestProfile(McpManagementContractTest.Yolo.class)
class McpManagementContractTest {
    public static class Yolo implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("qlawkus.mcp.approval-mode", "yolo");
        }
    }

    private static final String BASE = "/api/admin/mcp/connections";
    private static final String USER = "qlawkus";
    private static final String PASSWORD = "qlawkus-test";

    @Inject
    McpRegistry registry;
    @Inject
    QlawToolProvider provider;
    @Inject
    KeystoreSecretWriter secrets;

    @BeforeEach
    @AfterEach
    void cleanConnections() {
        registry.list().forEach(connection -> registry.remove(connection.id()));
    }

    @Test
    void agentCanConfigureDiscoverInvokeAndRemoveWithoutRestart() throws Exception {
        try (var server = new FakeMcpHttpServer()) {
            ToolProviderResult initial = provider.provideTools(null);
            assertFalse(has(initial, "mcp_docs_echo"));
            var specification = initial.tools().keySet().stream()
                    .filter(spec -> spec.name().equals("configureMcpConnection")).findFirst().orElseThrow();
            assertEquals(List.of("connection", "confirm"), specification.parameters().required());
            var connection = new McpConnection("docs", McpTransport.HTTP, true, server.url(), List.of(), Map.of(), Map.of(), null);
            var args = FakeMcpProtocol.JSON.createObjectNode().put("confirm", true);
            args.set("connection", FakeMcpProtocol.JSON.valueToTree(connection));
            String configured = McpRegistryContractTest.executor(initial, "configureMcpConnection").execute(
                    ToolExecutionRequest.builder().id("configure").name("configureMcpConnection")
                            .arguments(args.toString()).build(), "mcp-contract");
            assertEquals("READY", FakeMcpProtocol.JSON.readTree(configured).path("state").asText(), configured);
            ToolProviderResult updated = provider.provideTools(null);
            assertTrue(has(updated, "mcp_docs_echo"));
            assertEquals("echo:owner input", McpRegistryContractTest.body(McpRegistryContractTest.executor(updated, "mcp_docs_echo")
                    .execute(McpRegistryContractTest.request("mcp_docs_echo", "owner input"), "mcp-contract")));
            given().auth().preemptive().basic(USER, PASSWORD).get(BASE).then().statusCode(200)
                    .body("[0].id", equalTo("docs"));
            String removed = McpRegistryContractTest.executor(updated, "removeMcpConnection").execute(
                    ToolExecutionRequest.builder().id("remove").name("removeMcpConnection")
                            .arguments("{\"id\":\"docs\"}").build(), "mcp-contract");
            assertEquals("MCP connection removed.", removed);
            assertFalse(has(provider.provideTools(null), "mcp_docs_echo"));
        }
    }

    @Test
    void adminApiRequiresAuthenticationAndUsesTheSameRegistry() throws Exception {
        given().get(BASE).then().statusCode(401);
        try (var server = new FakeMcpHttpServer()) {
            var connection = new McpConnection("docs", McpTransport.HTTP, true, server.url(), List.of(), Map.of(), Map.of(), null);
            given().auth().preemptive().basic(USER, PASSWORD).contentType("application/json")
                    .body(connection).put(BASE + "/docs").then().statusCode(200).body("state", equalTo("READY"));
            assertTrue(has(provider.provideTools(null), "mcp_docs_echo"));
            given().auth().preemptive().basic(USER, PASSWORD).post(BASE + "/docs/test").then()
                    .statusCode(200).body("toolCount", equalTo(4));
            given().auth().preemptive().basic(USER, PASSWORD).contentType("application/json")
                    .body(connection).put(BASE + "/mismatch").then().statusCode(400);
            given().auth().preemptive().basic(USER, PASSWORD).delete(BASE + "/docs").then().statusCode(204);
            assertFalse(has(provider.provideTools(null), "mcp_docs_echo"));
        }
    }

    @Test
    void newVaultCredentialsWorkThroughAdminApiAndAreNeverListed() throws Exception {
        try (var server = new FakeMcpHttpServer()) {
            String alias = "qlawkus.mcp.servers.docs.headers.authorization";
            String token = "Bearer fixture-live-admin-token";
            server.requiredAuthorization = token;
            given().auth().preemptive().basic(USER, PASSWORD).contentType("application/json")
                    .body(Map.of("alias", alias, "value", token)).put("/api/admin/secrets").then().statusCode(204);
            var connection = new McpConnection("docs", McpTransport.HTTP, true, server.url(), List.of(),
                    Map.of("Authorization", alias), Map.of(), null);
            given().auth().preemptive().basic(USER, PASSWORD).contentType("application/json")
                    .body(connection).put(BASE + "/docs").then().statusCode(200);
            String list = given().auth().preemptive().basic(USER, PASSWORD).get(BASE).then().statusCode(200)
                    .extract().asString();
            assertTrue(list.contains(alias));
            assertFalse(list.contains(token));
            assertEquals(token, server.lastAuthorization);
        }
    }

    @Test
    void oauthClientCredentialsAuthenticateTheActualMcpTransport() throws Exception {
        try (var provider = new FakeMcpOAuthServer(); var server = new FakeMcpHttpServer()) {
            String alias = "qlawkus.mcp.servers.oauth.oauth.client-secret";
            secrets.setSecret(alias, FakeMcpOAuthServer.CLIENT_SECRET);
            server.requiredAuthorization = "Bearer " + FakeMcpOAuthServer.ACCESS_TOKEN;
            var oauth = new McpOAuth(McpOAuthGrant.CLIENT_CREDENTIALS, provider.tokenEndpoint(), null, null,
                    "fixture-client", alias, List.of("tools:read"), null, server.url());
            registry.put(new McpConnection("oauth", McpTransport.HTTP, true, server.url(), List.of(), Map.of(),
                    Map.of(), null, oauth));
            String result = McpRegistryContractTest.executor(this.provider.provideTools(null), "mcp_oauth_echo")
                    .execute(McpRegistryContractTest.request("mcp_oauth_echo", "authenticated"), "mcp-contract");
            assertEquals("echo:authenticated", McpRegistryContractTest.body(result));
            assertEquals("Bearer " + FakeMcpOAuthServer.ACCESS_TOKEN, server.lastAuthorization);
            assertEquals(1, provider.tokenRequests.get());
            String listed = FakeMcpProtocol.JSON.writeValueAsString(registry.list());
            assertFalse(listed.contains(FakeMcpOAuthServer.ACCESS_TOKEN));
            assertFalse(listed.contains(FakeMcpOAuthServer.CLIENT_SECRET));
        }
    }

    @Test
    void upstreamStderrAndCredentialValuesStayOutOfApplicationLogs() throws Exception {
        var records = new CopyOnWriteArrayList<String>();
        Handler capture = new Handler() {
            @Override public void publish(LogRecord record) {
                records.add(record.getMessage() + Arrays.toString(record.getParameters()));
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        Logger root = Logger.getLogger("");
        root.addHandler(capture);
        try {
            Path directory = Files.createDirectories(Path.of("target/mcp-management/stdio"));
            Path args = directory.resolve("java.args").toAbsolutePath();
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            Files.writeString(args, "-cp\n\"" + classpath.replace("\\", "\\\\").replace("\"", "\\\"")
                    + "\"\n" + FakeMcpStdioServer.class.getName() + "\n");
            String alias = "qlawkus.mcp.servers.local.environment.MCP_FIXTURE_KEY";
            secrets.setSecret(alias, "fixture-process-key");
            registry.put(new McpConnection("local", McpTransport.STDIO, true, null,
                    List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "@" + args),
                    Map.of(), Map.of("MCP_FIXTURE_KEY", alias), FakeMcpProtocol.LEGACY));
            String output = McpRegistryContractTest.executor(provider.provideTools(null), "mcp_local_environment")
                    .execute(McpRegistryContractTest.request("mcp_local_environment", ""), "mcp-contract");
            assertEquals("filtered=true;explicit=true", McpRegistryContractTest.body(output));
            registry.remove("local");
            assertTrue(records.stream().noneMatch(message -> message.contains(FakeMcpProtocol.STDERR_SENTINEL)
                    || message.contains("fixture-process-key")), "process output and credentials must not enter logs");
        } finally {
            root.removeHandler(capture);
        }
    }

    private static boolean has(ToolProviderResult tools, String name) {
        return tools.tools().keySet().stream().anyMatch(spec -> spec.name().equals(name));
    }
}
