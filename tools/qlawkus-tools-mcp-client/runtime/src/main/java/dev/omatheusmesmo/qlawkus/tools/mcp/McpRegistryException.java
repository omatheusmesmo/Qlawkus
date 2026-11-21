package dev.omatheusmesmo.qlawkus.tools.mcp;

/** Local validation or connection failure, deliberately without remote causes or payloads. */
public class McpRegistryException extends IllegalArgumentException {
  public McpRegistryException(String message) { super(message); }
}
