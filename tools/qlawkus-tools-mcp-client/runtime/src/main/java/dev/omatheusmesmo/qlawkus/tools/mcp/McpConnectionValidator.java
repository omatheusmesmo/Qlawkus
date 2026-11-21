package dev.omatheusmesmo.qlawkus.tools.mcp;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuthValidator;

/** Validates non-secret connection metadata before any I/O or registry mutation. */
final class McpConnectionValidator {
  private static final Set<String> RESERVED_HEADERS = Set.of("host", "content-length", "connection",
      "transfer-encoding", "accept", "content-type", "mcp-session-id", "mcp-protocol-version");
  private static final Set<String> UNSAFE_ENV = Set.of("BASH_ENV", "ENV", "LD_PRELOAD", "LD_LIBRARY_PATH",
      "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH", "PYTHONPATH", "NODE_OPTIONS");

  static void validateId(String id) {
    require(id != null && id.matches("[a-z][a-z0-9-]{0,19}"), "Invalid MCP connection id");
  }

  static void validate(McpConnection connection) {
    require(connection != null, "MCP connection is required");
    validateId(connection.id());
    require(connection.transport() != null, "MCP transport is required");
    require(connection.protocolVersion() == null || connection.protocolVersion().matches("20[0-9]{2}-[0-9]{2}-[0-9]{2}"),
        "Invalid MCP protocol version");
    if (connection.transport() == McpTransport.HTTP) {
      require(connection.command().isEmpty() && connection.environmentSecretRefs().isEmpty(),
          "HTTP connections cannot define commands or environment references");
      try {
        URI uri = URI.create(connection.url());
        require(Set.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null
            && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
            && uri.getPort() != 0 && uri.getPort() <= 65535 && connection.url().length() <= 2048
            && connection.url().chars().noneMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c)),
            "Invalid MCP HTTP endpoint");
        require(connection.headerSecretRefs().isEmpty() || "https".equals(uri.getScheme())
            || McpEndpointSecurity.isLoopback(uri.getHost()),
            "Credentialed MCP endpoints require HTTPS except on loopback");
      } catch (RuntimeException failure) {
        throw new McpRegistryException("Invalid MCP HTTP endpoint");
      }
    } else {
      require(connection.url() == null && connection.headerSecretRefs().isEmpty(),
          "STDIO connections cannot define endpoints or headers");
      require(!connection.command().isEmpty() && connection.command().size() <= 64,
          "MCP command must contain between 1 and 64 arguments");
      int length = 0;
      for (String argument : connection.command()) {
        require(argument != null && argument.length() <= 4096
            && argument.chars().noneMatch(Character::isISOControl), "Invalid MCP command argument");
        length += argument.length();
      }
      require(!connection.command().getFirst().isBlank() && length <= 16384, "Invalid MCP command size");
    }
    validateRefs(connection.id(), "headers", connection.headerSecretRefs());
    validateRefs(connection.id(), "environment", connection.environmentSecretRefs());
    McpOAuthValidator.validate(connection);
  }

  private static void validateRefs(String id, String kind, Map<String, String> refs) {
    require(refs.size() <= 16, "Too many MCP secret references");
    Set<String> normalizedNames = new HashSet<>();
    refs.forEach((name, alias) -> {
      boolean header = kind.equals("headers");
      String normalized = header ? name.toLowerCase(Locale.ROOT) : name;
      require(header ? name.matches("[A-Za-z][A-Za-z0-9-]{0,63}") && !RESERVED_HEADERS.contains(normalized)
          : name.matches("[A-Z][A-Z0-9_]{0,63}") && !UNSAFE_ENV.contains(name), "Invalid MCP secret target");
      require(normalizedNames.add(normalized), "Duplicate MCP secret target");
      require(("qlawkus.mcp.servers." + id + "." + kind + "." + normalized).equals(alias),
          "MCP secret reference must belong to this connection and target");
    });
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new McpRegistryException(message);
  }
}
