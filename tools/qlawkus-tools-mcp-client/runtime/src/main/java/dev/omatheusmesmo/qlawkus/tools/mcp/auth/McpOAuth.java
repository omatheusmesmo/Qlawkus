package dev.omatheusmesmo.qlawkus.tools.mcp.auth;

import java.util.List;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpConfig;

/** Public connection metadata; credentials and token bundles live only in the vault. */
public record McpOAuth(McpOAuthGrant grant, String tokenEndpoint, String authorizationEndpoint,
        String revocationEndpoint, String clientId, String clientSecretRef, List<String> scopes,
        String redirectUri, String resource, McpOAuthClientAuthMethod clientAuthMethod) {
    public McpOAuth(McpOAuthGrant grant, String tokenEndpoint, String authorizationEndpoint,
            String revocationEndpoint, String clientId, String clientSecretRef, List<String> scopes,
            String redirectUri, String resource) {
        this(grant, tokenEndpoint, authorizationEndpoint, revocationEndpoint, clientId, clientSecretRef,
                scopes, redirectUri, resource, McpOAuthClientAuthMethod.POST);
    }

    public McpOAuth {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        clientAuthMethod = clientAuthMethod == null ? McpOAuthClientAuthMethod.POST : clientAuthMethod;
    }

    public static McpOAuth from(McpConfig.OAuth value) {
        return value.grant().map(grant -> new McpOAuth(grant, value.tokenEndpoint().orElse(null),
                value.authorizationEndpoint().orElse(null), value.revocationEndpoint().orElse(null),
                value.clientId().orElse(null), value.clientSecretRef().orElse(null),
                value.scopes().orElse(List.of()), value.redirectUri().orElse(null), value.resource().orElse(null), value.clientAuthMethod()))
                .orElse(null);
    }
}
