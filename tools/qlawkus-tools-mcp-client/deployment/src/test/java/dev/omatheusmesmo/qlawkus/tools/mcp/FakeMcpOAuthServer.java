package dev.omatheusmesmo.qlawkus.tools.mcp;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Local client-credentials provider for the end-to-end MCP transport contract. */
final class FakeMcpOAuthServer implements AutoCloseable {
    static final String ACCESS_TOKEN = "fixture-oauth-access";
    static final String CLIENT_SECRET = "fixture-oauth-client-secret";
    final AtomicInteger tokenRequests = new AtomicInteger();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    FakeMcpOAuthServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/token", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean valid = exchange.getRequestMethod().equals("POST") && body.contains("grant_type=client_credentials")
                    && body.contains("client_id=fixture-client") && body.contains("client_secret=" + CLIENT_SECRET);
            if (valid) tokenRequests.incrementAndGet();
            byte[] response = (valid ? "{\"access_token\":\"" + ACCESS_TOKEN
                    + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}" : "{\"error\":\"invalid_client\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(valid ? 200 : 401, response.length);
            try (var stream = exchange.getResponseBody()) { stream.write(response); }
            finally { exchange.close(); }
        });
        server.start();
    }

    String tokenEndpoint() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/token"; }

    @Override public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
