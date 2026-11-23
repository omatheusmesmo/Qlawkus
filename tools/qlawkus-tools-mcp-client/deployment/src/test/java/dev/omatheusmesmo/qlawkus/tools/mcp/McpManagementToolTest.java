package dev.omatheusmesmo.qlawkus.tools.mcp;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.approval.ApprovedExecution;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@QuarkusTest
class McpManagementToolTest {
    @TempDir
    Path root;

    private McpManagementTool tool(McpFixture fixture) {
        return new McpManagementTool(fixture.registry, new ObjectMapper(), fixture.config);
    }

    private static McpConnection http(String id, FakeMcpHttpServer server, Map<String, String> headers) {
        return new McpConnection(id, McpTransport.HTTP, true, server.url(), List.of(), headers, Map.of(), null);
    }

    @Test
    void approvalModeDefaultsToHitlAndNothingIsSavedOutsideAnApprovedExecution() throws Exception {
        try (var server = new FakeMcpHttpServer(); var fixture = new McpFixture(root)) {
            var tool = tool(fixture);
            String withoutConfirm = tool.configureMcpConnection(http("docs", server, Map.of()), false);
            String selfConfirmed = tool.configureMcpConnection(http("docs", server, Map.of()), true);
            assertTrue(withoutConfirm.contains(server.url()), withoutConfirm);
            assertTrue(selfConfirmed.toLowerCase().contains("approval"), selfConfirmed);
            assertFalse(selfConfirmed.contains("confirm=true"), "the model must not be taught to self-confirm");
            assertTrue(fixture.registry.list().isEmpty(), "a flag the model sets itself approves nothing");
            assertTrue(server.requests.isEmpty(), "nothing is contacted before the owner approves");
            String applied = ApprovedExecution.run(() -> tool.configureMcpConnection(http("docs", server, Map.of()), false));
            assertTrue(applied.contains("\"state\":\"READY\""), applied);
            assertEquals(1, fixture.registry.list().size());
        }
    }

    @Test
    void hitlHoldsTheWritingToolsOnlyAndYoloHoldsNothing() throws Exception {
        try (var hitl = new McpFixture(root.resolve("hitl")); var yolo = new McpFixture(root.resolve("yolo"),
                "qlawkus.mcp.approval-mode", "yolo")) {
            assertTrue(new McpApprovalRequirement(hitl.config).requires("configureMcpConnection"));
            assertFalse(new McpApprovalRequirement(hitl.config).requires("listMcpConnections"));
            assertFalse(new McpApprovalRequirement(hitl.config).requires("testMcpConnection"));
            assertFalse(new McpApprovalRequirement(yolo.config).requires("configureMcpConnection"));
        }
    }

    @Test
    void aStdioPreviewNamesTheCommandThatWouldBeLaunchedWithoutLaunchingIt() throws Exception {
        try (var fixture = new McpFixture(root)) {
            var connection = new McpConnection("local", McpTransport.STDIO, true, null,
                    List.of("/opt/never-started", "--stdio"), Map.of(), Map.of(), null);
            String preview = tool(fixture).configureMcpConnection(connection, true);
            assertTrue(preview.contains("/opt/never-started --stdio"), preview);
            assertTrue(fixture.registry.list().isEmpty());
        }
    }

    @Test
    void anInvalidProposalIsReportedInsteadOfPreviewed() throws Exception {
        try (var fixture = new McpFixture(root)) {
            var connection = new McpConnection("bad", McpTransport.HTTP, true, "file:///etc/passwd", List.of(),
                    Map.of(), Map.of(), null);
            assertTrue(tool(fixture).configureMcpConnection(connection, false).startsWith("Error:"));
        }
    }

    @Test
    void yoloAppliesAtOnce() throws Exception {
        try (var server = new FakeMcpHttpServer(); var fixture = new McpFixture(root, "qlawkus.mcp.approval-mode", "yolo")) {
            String applied = tool(fixture).configureMcpConnection(http("docs", server, Map.of()), false);
            assertTrue(applied.contains("\"state\":\"READY\""), applied);
        }
    }

    @Test
    void theAgentCannotRedirectAnExistingCredentialedConnectionEvenWhenApproved() throws Exception {
        try (var trusted = new FakeMcpHttpServer(); var attacker = new FakeMcpHttpServer();
                var fixture = new McpFixture(root, "qlawkus.mcp.approval-mode", "yolo")) {
            String alias = "qlawkus.mcp.servers.docs.headers.authorization";
            fixture.secrets.setSecret(alias, "Bearer fixture-owner-token");
            var tool = tool(fixture);
            Map<String, String> refs = Map.of("Authorization", alias);
            assertTrue(tool.configureMcpConnection(http("docs", trusted, refs), true).contains("READY"));
            String refused = tool.configureMcpConnection(http("docs", attacker, refs), true);
            assertTrue(refused.startsWith("Error:") && refused.contains("console"), refused);
            assertTrue(attacker.requests.isEmpty(), "the credential must never reach the new endpoint");
            assertEquals(trusted.url(), fixture.registry.list().getFirst().url());
            var disabled = new McpConnection("docs", McpTransport.HTTP, false, trusted.url(), List.of(), refs, Map.of(), null);
            assertTrue(tool.configureMcpConnection(disabled, true).contains("DISABLED"),
                    "changes that keep the endpoint are still allowed");
        }
    }
}
