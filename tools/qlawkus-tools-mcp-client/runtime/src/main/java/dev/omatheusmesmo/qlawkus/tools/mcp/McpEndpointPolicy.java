package dev.omatheusmesmo.qlawkus.tools.mcp;

import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuth;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Keeps server-side requests to the network the owner expects. Unless private endpoints are allowed,
 * every address the host resolves to must be public. {@link McpEndpointFilter} repeats that check
 * before every request, so a name that starts resolving to a private address is refused on the next
 * call. The JAX-RS client the upstream transport uses exposes no hook on the connection itself, so
 * the window between the check and the connect is not closed the way a connection handler closed it.
 */
public final class McpEndpointPolicy {
  @FunctionalInterface
  public interface Resolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  private final boolean allowPrivate;
  private final Resolver resolver;

  public McpEndpointPolicy(boolean allowPrivate) {
    this(allowPrivate, InetAddress::getAllByName);
  }

  public McpEndpointPolicy(boolean allowPrivate, Resolver resolver) {
    this.allowPrivate = allowPrivate;
    this.resolver = resolver;
  }

  public void require(McpConnection connection) {
    if (allowPrivate) return;
    if (connection.url() != null) require(connection.url());
    McpOAuth oauth = connection.oauth();
    if (oauth != null) {
      if (oauth.tokenEndpoint() != null) require(oauth.tokenEndpoint());
      if (oauth.revocationEndpoint() != null) require(oauth.revocationEndpoint());
    }
  }

  public void require(String url) {
    if (allowPrivate) return;
    String host = URI.create(url).getHost();
    McpConnectionValidator.require(host != null, "Invalid MCP endpoint");
    String literal = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    InetAddress[] addresses;
    try {
      addresses = new InetAddress[] {InetAddress.ofLiteral(literal)};
    } catch (IllegalArgumentException notLiteral) {
      try {
        addresses = resolver.resolve(literal);
      } catch (UnknownHostException failure) {
        throw new McpRegistryException("MCP endpoint host could not be resolved");
      }
    }
    McpConnectionValidator.require(addresses.length > 0, "MCP endpoint host could not be resolved");
    for (InetAddress address : addresses) {
      McpConnectionValidator.require(isPublic(address),
          "MCP endpoint resolves to a private or loopback address; the owner can allow it with qlawkus.mcp.allow-private-endpoints");
    }
  }

  static boolean isPublic(InetAddress address) {
    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
        || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
    byte[] bytes = address.getAddress();
    if (bytes.length == 4) {
      int first = bytes[0] & 0xff;
      int second = bytes[1] & 0xff;
      return first != 0 && !(first == 100 && second >= 64 && second <= 127);
    }
    return (bytes[0] & 0xfe) != 0xfc;
  }
}
