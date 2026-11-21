package dev.omatheusmesmo.qlawkus.tools.mcp;

import java.util.List;
import java.util.Map;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuth;

/** Persisted connection metadata. Credentials are vault aliases, never literal values. */
public record McpConnection(String id, McpTransport transport, boolean enabled, String url,
    List<String> command, Map<String, String> headerSecretRefs,
    Map<String, String> environmentSecretRefs, String protocolVersion, McpOAuth oauth) {
  public McpConnection(String id, McpTransport transport, boolean enabled, String url,
      List<String> command, Map<String, String> headerSecretRefs,
      Map<String, String> environmentSecretRefs, String protocolVersion) {
    this(id, transport, enabled, url, command, headerSecretRefs, environmentSecretRefs, protocolVersion, null);
  }
  public McpConnection {
    command = command == null ? List.of() : List.copyOf(command);
    headerSecretRefs = headerSecretRefs == null ? Map.of() : Map.copyOf(headerSecretRefs);
    environmentSecretRefs = environmentSecretRefs == null ? Map.of() : Map.copyOf(environmentSecretRefs);
  }
}
