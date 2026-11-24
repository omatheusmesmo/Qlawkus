package dev.omatheusmesmo.qlawkus.tools.mcp;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuthService;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder;
import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkiverse.langchain4j.mcp.runtime.http.McpHttpClients;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/** Creates only upstream transports and clients, retaining ownership until discovery succeeds. */
final class McpClientFactory {
  private final McpConfig config;
  private final KeystoreSecretWriter secrets;
  private final ExecutorService ioExecutor;
  private final McpOAuthService oauth;
  private final McpEndpointPolicy endpoints;

  McpClientFactory(McpConfig config, KeystoreSecretWriter secrets, ExecutorService ioExecutor,
      McpOAuthService oauth, McpEndpointPolicy endpoints) {
    this.config = config;
    this.secrets = secrets;
    this.ioExecutor = ioExecutor;
    this.oauth = oauth;
    this.endpoints = endpoints;
  }

  Handle connect(McpConnection connection) {
    McpTransport transport = null;
    McpClient client = null;
    try {
      endpoints.require(connection);
      if (isHttp(connection)) {
        JaxRsHttpClientBuilder http = McpHttpClients.builder((McpClientAuthProvider) null);
        http.connectTimeout(config.discoveryTimeout());
        http.addClientProvider(new McpEndpointFilter(endpoints));
        transport = StreamableHttpMcpTransport.builder()
            .url(connection.url())
            .httpClientBuilder(http)
            .customHeaders((Supplier<Map<String, String>>) () -> headers(connection))
            .timeout(config.callTimeout()).logRequests(false).logResponses(false)
            .subsidiaryChannel(false).build();
      } else {
        transport = StdioMcpTransport.builder().command(connection.command())
            .environment(McpEnvironment.masked(System.getenv(), resolve(connection.environmentSecretRefs(), false)))
            .executorService(ioExecutor).logEvents(false).build();
      }
      transport = new BoundedMcpTransport(transport, config.discoveryTimeout(), config.callTimeout());
      DefaultMcpClient.Builder builder = DefaultMcpClient.builder().key(connection.id()).transport(transport)
          .initializationTimeout(config.discoveryTimeout()).protocolDetectionTimeout(config.discoveryTimeout())
          .toolExecutionTimeout(config.callTimeout()).resourcesTimeout(config.discoveryTimeout())
          .promptsTimeout(config.discoveryTimeout()).pingTimeout(config.discoveryTimeout())
          .autoHealthCheck(false).subscribeToToolListChanges(false).subscribeToResourceListChanges(false)
          .subscribeToPromptListChanges(false).logHandler(message -> {})
          .toolExecutionTimeoutErrorMessage("MCP_TOOL_TIMEOUT");
      if (connection.protocolVersion() != null) builder.protocolVersion(connection.protocolVersion());
      client = builder.build();
      List<ToolSpecification> tools = client.listTools();
      McpConnectionValidator.require(tools.size() <= config.maxToolsPerServer(), "MCP server tool limit exceeded");
      Set<String> names = new HashSet<>();
      List<ToolSpecification> validated = new ArrayList<>();
      for (ToolSpecification tool : tools) {
        String name = tool.name();
        McpConnectionValidator.require(name != null && name.matches("[A-Za-z0-9_-]+")
            && names.add(name) && namespace(connection.id(), name).length() <= 64,
            "MCP server advertised an invalid or duplicate tool name");
        McpConnectionValidator.require(tool.description() == null || tool.description().length() <= 4000,
            "MCP tool description limit exceeded");
        McpConnectionValidator.require(tool.toString().length() <= 32768, "MCP tool schema limit exceeded");
        validated.add(tool);
      }
      return new Handle(client, List.copyOf(validated), currentSecrets(connection));
    } catch (Exception failure) {
      if (client != null) close(client);
      else if (transport != null) close(transport);
      throw new McpRegistryException("MCP connection failed validation or discovery");
    }
  }

  private static boolean isHttp(McpConnection connection) {
    return switch (connection.transport()) {
      case HTTP -> true;
      case STDIO -> false;
    };
  }

  private Map<String, String> headers(McpConnection connection) {
    Map<String, String> headers = new HashMap<>(resolve(connection.headerSecretRefs(), true));
    if (connection.oauth() != null) {
      McpConnectionValidator.require(oauth != null, "MCP OAuth support is unavailable");
      headers.put("Authorization", oauth.authorizationHeader(connection));
    }
    return headers;
  }

  List<String> currentSecrets(McpConnection connection) {
    List<String> values = new ArrayList<>(resolve(connection.headerSecretRefs(), true).values());
    values.addAll(resolve(connection.environmentSecretRefs(), false).values());
    for (String value : List.copyOf(values)) {
      if (value.regionMatches(true, 0, "Bearer ", 0, 7) && value.length() > 7) values.add(value.substring(7));
    }
    if (oauth != null) values.addAll(oauth.secretsForRedaction(connection));
    return List.copyOf(values);
  }

  Map<String, String> resolve(Map<String, String> references, boolean header) {
    Map<String, String> values = new HashMap<>();
    references.forEach((target, alias) -> {
      String value;
      try {
        value = secrets.readSecret(alias).orElseThrow(() -> new McpRegistryException("MCP secret reference is unavailable"));
      } catch (RuntimeException failure) {
        throw new McpRegistryException("MCP secret reference is unavailable");
      }
      McpConnectionValidator.require(!value.isEmpty() && value.length() <= 8192
          && value.indexOf('\0') < 0 && (!header || value.chars().noneMatch(Character::isISOControl)),
          "MCP secret value is invalid for its target");
      values.put(target, value);
    });
    return values;
  }

  static String namespace(String server, String tool) { return "mcp_" + server + "_" + tool; }

  static void close(AutoCloseable closeable) {
    try { closeable.close(); } catch (Exception ignored) { }
  }

  record Handle(McpClient client, List<ToolSpecification> tools, List<String> initialSecrets) implements AutoCloseable {
    @Override public void close() { McpClientFactory.close(client); }
  }
}
