package dev.omatheusmesmo.qlawkus.tools.mcp.auth;

import dev.omatheusmesmo.qlawkus.tools.mcp.McpConnection;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpRegistryException;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpTransport;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpEndpointSecurity;
import java.net.URI;

/** Checks operator/agent-supplied metadata before network or vault access. */
public final class McpOAuthValidator {
    private McpOAuthValidator() {}

    public static void validate(McpConnection connection) {
        McpOAuth oauth = connection.oauth();
        if (oauth == null) return;
        require(connection.transport() == McpTransport.HTTP, "OAuth requires an HTTP MCP connection");
        endpoint(connection.url());
        require(oauth.grant() != null, "OAuth grant is required");
        endpoint(oauth.tokenEndpoint());
        if (oauth.revocationEndpoint() != null) endpoint(oauth.revocationEndpoint());
        require(oauth.clientId() != null && !oauth.clientId().isBlank() && oauth.clientId().length() <= 512
                && oauth.clientId().chars().noneMatch(Character::isISOControl), "Invalid OAuth client id");
        require(oauth.clientSecretRef() == null || oauth.clientSecretRef()
                .equals("qlawkus.mcp.servers." + connection.id() + ".oauth.client-secret"),
                "OAuth client secret must belong to this connection");
        require(oauth.scopes().size() <= 32 && oauth.scopes().stream().allMatch(scope -> scope != null
                && !scope.isEmpty() && scope.length() <= 128
                && scope.chars().allMatch(c -> c >= 0x21 && c <= 0x7e && c != '"' && c != '\\')),
                "Invalid OAuth scopes");
        require(connection.headerSecretRefs().keySet().stream().noneMatch(name -> name.equalsIgnoreCase("Authorization")),
                "OAuth cannot be combined with an Authorization header reference");
        if (oauth.resource() != null) endpoint(oauth.resource());
        if (oauth.grant() == McpOAuthGrant.AUTHORIZATION_CODE) {
            endpoint(oauth.authorizationEndpoint());
            URI callback = endpoint(oauth.redirectUri());
            require(callback.getRawPath().equals("/api/admin/mcp/oauth/callback"), "Invalid OAuth callback path");
        } else {
            require(oauth.clientSecretRef() != null, "Client credentials require a vault client secret");
            require(oauth.authorizationEndpoint() == null && oauth.redirectUri() == null,
                    "Client credentials do not use browser authorization");
        }
    }

    private static URI endpoint(String text) {
        try {
            require(text != null && text.length() <= 2048
                    && text.chars().noneMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c)),
                    "Invalid OAuth endpoint");
            URI uri = URI.create(text);
            require(uri.getHost() != null && uri.getUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null && uri.getPort() != 0 && uri.getPort() <= 65535
                    && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())
                    && McpEndpointSecurity.isLoopback(uri.getHost())),
                    "OAuth endpoints require HTTPS except on loopback");
            return uri;
        } catch (RuntimeException failure) {
            throw new McpRegistryException("Invalid OAuth endpoint or callback");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new McpRegistryException(message);
    }
}
