package dev.omatheusmesmo.qlawkus.tools.mcp;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMethod;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounds discovery pagination and decoded response size while delegating all protocol I/O upstream. */
final class BoundedMcpTransport implements McpTransport {
  private final McpTransport delegate;
  private final long discoveryDeadline;
  private final Duration callTimeout;
  private final AtomicInteger pages = new AtomicInteger();

  BoundedMcpTransport(McpTransport delegate, Duration discoveryTimeout, Duration callTimeout) {
    this.delegate = delegate;
    this.discoveryDeadline = System.nanoTime() + discoveryTimeout.toNanos();
    this.callTimeout = callTimeout;
  }

  @Override public void start(McpOperationHandler handler) { delegate.start(handler); }
  @Override public CompletableFuture<String> sendInitializeRequest(McpInitializeRequest request) {
    return bounded(delegate.sendInitializeRequest(request), remaining());
  }

  @Override public CompletableFuture<String> sendRequest(McpCallContext context) {
    boolean call = context.message().method == McpClientMethod.TOOLS_CALL;
    if (!call && (System.nanoTime() >= discoveryDeadline
        || context.message().method == McpClientMethod.TOOLS_LIST && pages.incrementAndGet() > 8))
      return CompletableFuture.failedFuture(new McpRegistryException("MCP discovery budget exceeded"));
    return bounded(delegate.sendRequest(context), call ? callTimeout.toNanos() : remaining());
  }

  private long remaining() { return Math.max(1, discoveryDeadline - System.nanoTime()); }

  private CompletableFuture<String> bounded(CompletableFuture<String> response, long timeoutNanos) {
    return response.orTimeout(timeoutNanos, TimeUnit.NANOSECONDS).thenApply(body -> {
      McpConnectionValidator.require(body != null && body.length() <= 1048576, "MCP response limit exceeded");
      return body;
    });
  }

  @Override public void sendMessage(McpCallContext context) { delegate.sendMessage(context); }
  @Override public void checkHealth() { delegate.checkHealth(); }
  @Override public void onFailure(Runnable action) { delegate.onFailure(() -> {}); }
  @Override public void setModernProtocol(boolean modern) { delegate.setModernProtocol(modern); }
  @Override public void setProtocolVersion(String version) { delegate.setProtocolVersion(version); }
  @Override public boolean requiresCancellationNotification() { return delegate.requiresCancellationNotification(); }
  @Override public void close() throws IOException { delegate.close(); }
}
