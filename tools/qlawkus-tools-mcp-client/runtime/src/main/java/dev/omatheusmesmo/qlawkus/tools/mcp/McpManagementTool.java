package dev.omatheusmesmo.qlawkus.tools.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The agent manages the same persistent registry exposed by the admin API. */
@QlawTool(capturePayload = false)
@ApplicationScoped
public class McpManagementTool {
    private final McpRegistry registry;
    private final ObjectMapper json;
    private final McpConfig config;

    @Inject
    public McpManagementTool(McpRegistry registry, ObjectMapper json, McpConfig config) {
        this.registry = registry;
        this.json = json;
        this.config = config;
    }

    @Tool("List configured MCP connections. Contains credential references only, never secret values. "
            + "Connections can be configured at runtime; installing the MCP capability itself requires a distribution build.")
    public String listMcpConnections() {
        return render(registry.list());
    }

    @Tool("Add or replace a persistent MCP connection. In HITL approval mode you MUST first call with confirm=false "
            + "to preview, present it to the owner, get explicit approval, then call again with confirm=true; nothing "
            + "is saved or contacted before that. In YOLO mode it applies immediately. Enabled connections are validated "
            + "and tested before replacing the working configuration. New tools appear on subsequent discovery without "
            + "restarting. Use HTTP for a remote MCP endpoint or STDIO with a command argument list. Credentials must be "
            + "vault alias references in that connection's own namespace; never put tokens or passwords in URLs or command "
            + "arguments. You cannot move an existing credentialed connection to another endpoint or command; the owner "
            + "does that in the console.")
    public String configureMcpConnection(@P("Connection definition, including its stable id, transport, enabled flag and credential references")
            McpConnection connection,
            @P("Set true only after the owner explicitly approved this exact change (HITL mode)") boolean confirm) {
        try {
            McpConnectionValidator.validate(connection);
            if (movesCredentials(connection)) {
                return "Error: an existing connection with credentials cannot be pointed at another endpoint or command "
                        + "by the agent. Ask the owner to change it in the console.";
            }
            if (config.approvalMode() == McpConfig.ApprovalMode.HITL && !confirm) {
                return preview(connection);
            }
            return render(registry.put(connection));
        } catch (McpRegistryException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Test discovery on a configured MCP connection. Returns only availability and tool count, never remote error details.")
    public String testMcpConnection(@P("Configured connection id") String id) {
        try {
            return render(registry.test(id));
        } catch (McpRegistryException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Remove a persistent MCP connection and its tools. Applies to subsequent discovery without restarting.")
    public String removeMcpConnection(@P("Configured connection id") String id) {
        try {
            return registry.remove(id) ? "MCP connection removed." : "MCP connection not found.";
        } catch (McpRegistryException e) {
            return "Error: " + e.getMessage();
        }
    }

    private boolean movesCredentials(McpConnection proposed) {
        return registry.list().stream().filter(current -> current.id().equals(proposed.id())).findFirst()
                .filter(current -> !current.headerSecretRefs().isEmpty() || !current.environmentSecretRefs().isEmpty()
                        || current.oauth() != null)
                .map(current -> current.transport() != proposed.transport()
                        || !Objects.equals(current.url(), proposed.url())
                        || !current.command().equals(proposed.command())
                        || !Objects.equals(current.oauth() == null ? null : current.oauth().tokenEndpoint(),
                                proposed.oauth() == null ? null : proposed.oauth().tokenEndpoint()))
                .orElse(false);
    }

    private static String preview(McpConnection connection) {
        String target = connection.transport() == McpTransport.HTTP ? "contact " + connection.url()
                : "launch the process: " + String.join(" ", connection.command());
        List<String> credentials = new ArrayList<>(connection.headerSecretRefs().values());
        credentials.addAll(connection.environmentSecretRefs().values());
        return "Awaiting owner approval. Saving MCP connection '" + connection.id() + "' ("
                + (connection.enabled() ? "enabled" : "disabled") + ") will " + target
                + " and expose that server's tools to you"
                + (credentials.isEmpty() ? "" : ", sending the vault credentials " + credentials)
                + (connection.oauth() == null ? "" : ", authorizing through OAuth")
                + ". Ask the owner to approve, then call configureMcpConnection again with confirm=true.";
    }

    private String render(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "Error: MCP status could not be rendered.";
        }
    }
}
