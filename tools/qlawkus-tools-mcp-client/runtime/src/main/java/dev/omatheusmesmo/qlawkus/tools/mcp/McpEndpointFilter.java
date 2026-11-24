package dev.omatheusmesmo.qlawkus.tools.mcp;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

/**
 * Applies {@link McpEndpointPolicy} to every request the MCP HTTP transport sends, so a host that
 * starts resolving to a non-public address after discovery is refused instead of reached.
 */
final class McpEndpointFilter implements ClientRequestFilter {
  private final McpEndpointPolicy endpoints;

  McpEndpointFilter(McpEndpointPolicy endpoints) {
    this.endpoints = endpoints;
  }

  @Override
  public void filter(ClientRequestContext request) {
    endpoints.require(request.getUri().toString());
  }
}
