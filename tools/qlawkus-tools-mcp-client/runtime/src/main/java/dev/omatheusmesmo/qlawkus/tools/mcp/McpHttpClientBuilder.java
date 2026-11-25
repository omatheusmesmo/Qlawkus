package dev.omatheusmesmo.qlawkus.tools.mcp;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientOptions;
import java.time.Duration;

/**
 * Builds the {@link HttpClient} one MCP connection hands to the upstream transport. It is given to that
 * transport instance directly and never registered as a {@code HttpClientBuilderFactory}, so no other
 * builder of an HTTP client in the application is affected. Every client it builds is guarded by
 * {@link McpEndpointPolicy#guard}.
 */
final class McpHttpClientBuilder implements HttpClientBuilder {
  private final Vertx vertx;
  private final McpEndpointPolicy endpoints;
  private Duration connectTimeout = Duration.ofSeconds(10);
  private Duration readTimeout = Duration.ZERO;

  McpHttpClientBuilder(Vertx vertx, McpEndpointPolicy endpoints) {
    this.vertx = vertx;
    this.endpoints = endpoints;
  }

  @Override
  public Duration connectTimeout() {
    return connectTimeout;
  }

  @Override
  public McpHttpClientBuilder connectTimeout(Duration timeout) {
    this.connectTimeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    return this;
  }

  @Override
  public Duration readTimeout() {
    return readTimeout;
  }

  @Override
  public McpHttpClientBuilder readTimeout(Duration timeout) {
    this.readTimeout = timeout == null ? Duration.ZERO : timeout;
    return this;
  }

  @Override
  public HttpClient build() {
    HttpClientOptions options = new HttpClientOptions()
        .setDecompressionSupported(true)
        .setConnectTimeout((int) Math.min(Integer.MAX_VALUE, connectTimeout.toMillis()));
    HttpClientAgent agent = endpoints.guard(vertx.httpClientBuilder().with(options)).build();
    return new McpHttpClient(agent, readTimeout);
  }
}
