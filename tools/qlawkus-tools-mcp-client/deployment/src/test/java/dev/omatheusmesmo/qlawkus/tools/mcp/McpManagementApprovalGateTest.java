package dev.omatheusmesmo.qlawkus.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.approval.ApprovalService;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStatus;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStore;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * With the default {@code hitl} mode the agent's call to {@code configureMcpConnection} goes through the
 * real {@link QlawToolProvider}: the durable gate holds it, a request is stored for the owner, and only
 * the owner's approval makes the registry change.
 */
@QuarkusTest
class McpManagementApprovalGateTest {

    @Inject
    McpRegistry registry;
    @Inject
    QlawToolProvider provider;
    @Inject
    ApprovalService approvals;
    @Inject
    ApprovalStore store;

    @BeforeEach
    @AfterEach
    void clean() {
        registry.list().forEach(connection -> registry.remove(connection.id()));
    }

    private ApprovalStatus awaitSettled(String id) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        ApprovalStatus status = store.find(id).orElseThrow().status();
        while ((status == ApprovalStatus.APPROVED || status == ApprovalStatus.EXECUTING || status == ApprovalStatus.PENDING)
                && System.nanoTime() < deadline) {
            Thread.sleep(50);
            status = store.find(id).orElseThrow().status();
        }
        return status;
    }

    @Test
    void theAgentsCallIsHeldAndOnlyTheOwnersApprovalSavesTheConnection() throws Exception {
        try (var server = new FakeMcpHttpServer()) {
            ToolProviderResult tools = provider.provideTools(null);
            var connection = new McpConnection("gated", McpTransport.HTTP, true, server.url(), List.of(), Map.of(),
                    Map.of(), null);
            var args = FakeMcpProtocol.JSON.createObjectNode().put("confirm", true);
            args.set("connection", FakeMcpProtocol.JSON.valueToTree(connection));

            String held = McpRegistryContractTest.executor(tools, "configureMcpConnection").execute(
                    ToolExecutionRequest.builder().id("gate").name("configureMcpConnection")
                            .arguments(args.toString()).build(), "mcp-gate");

            assertTrue(held.contains("NOT been executed"), held);
            assertTrue(registry.list().isEmpty(), "confirm=true written by the model approves nothing");
            assertTrue(server.requests.isEmpty());
            ApprovalRequest pending = store.list().stream()
                    .filter(request -> request.toolName().equals("configureMcpConnection")
                            && request.status() == ApprovalStatus.PENDING)
                    .findFirst().orElseThrow();

            ApprovalService.Decision decision = approvals.approve(pending.id(), "owner");

            assertTrue(decision.accepted(), decision.message());
            ApprovalStatus settled = awaitSettled(pending.id());
            assertEquals(ApprovalStatus.EXECUTED, settled);
            assertEquals(1, registry.list().size());
            assertEquals("gated", registry.list().get(0).id());
        }
    }
}
