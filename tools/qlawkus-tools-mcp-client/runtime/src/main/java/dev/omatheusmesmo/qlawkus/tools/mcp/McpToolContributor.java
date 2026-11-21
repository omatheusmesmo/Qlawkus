package dev.omatheusmesmo.qlawkus.tools.mcp;

import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.tool.QlawToolContributor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Adapts managed MCP connections to the existing agent tool pipeline. */
@ApplicationScoped
public class McpToolContributor implements QlawToolContributor {
  private final McpRegistry registry;

  @Inject public McpToolContributor(McpRegistry registry) { this.registry = registry; }

  @Override public ToolProviderResult provideTools(ToolProviderRequest request) { return registry.tools(); }
}
