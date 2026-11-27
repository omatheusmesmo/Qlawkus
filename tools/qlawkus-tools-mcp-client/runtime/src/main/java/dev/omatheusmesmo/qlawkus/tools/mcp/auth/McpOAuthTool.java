package dev.omatheusmesmo.qlawkus.tools.mcp.auth;

import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccess;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpConnection;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpRegistry;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpRegistryException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The agent can initiate consent, while the owner completes the provider's login in a browser. */
@QlawTool(capturePayload = false)
@ApplicationScoped
public class McpOAuthTool {
    @Inject McpRegistry registry;
    @Inject McpOAuthService oauth;
    @Inject ObjectMapper json;

    @ToolAccess(AccessClass.CREATE)
    @Tool("Begin browser OAuth authorization for a configured MCP connection. Return its authorizationUrl to the owner "
            + "to open and approve. The owner completes login and consent; tokens remain in the vault and are renewed automatically. "
            + "The browser must be able to authenticate to this Qlawkus console for the callback.")
    public String authorizeMcpConnection(@P("Configured MCP connection id") String id) {
        try { return json.writeValueAsString(oauth.begin(connection(id), null)); }
        catch (McpRegistryException | JacksonException failure) {
            return "Error: MCP authorization could not be started.";
        }
    }

    @ToolAccess(AccessClass.READ)
    @Tool("Check MCP OAuth authorization status without exposing tokens or contacting the provider.")
    public String mcpAuthorizationStatus(@P("Configured MCP connection id") String id) {
        try { return json.writeValueAsString(oauth.status(connection(id))); }
        catch (McpRegistryException | JacksonException failure) {
            return "Error: MCP authorization status is unavailable.";
        }
    }

    @ToolAccess(AccessClass.DELETE)
    @Tool("Disable an MCP connection, remove its OAuth tokens from the vault and attempt provider token revocation. "
            + "remoteRevoked=false means local access is removed but remote revocation was unavailable or unsupported.")
    public String disconnectMcpAuthorization(@P("Configured MCP connection id") String id) {
        try {
            McpConnection current = connection(id);
            registry.put(McpOAuthResource.disabled(current));
            return json.writeValueAsString(oauth.disconnect(current));
        } catch (McpRegistryException | JacksonException failure) {
            return "Error: MCP authorization could not be disconnected.";
        }
    }

    private McpConnection connection(String id) {
        return registry.list().stream().filter(connection -> connection.id().equals(id)).findFirst()
                .orElseThrow(() -> new McpRegistryException("Unknown MCP connection"));
    }
}
