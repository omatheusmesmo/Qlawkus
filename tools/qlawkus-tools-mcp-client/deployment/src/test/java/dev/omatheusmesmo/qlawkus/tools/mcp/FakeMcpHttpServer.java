package dev.omatheusmesmo.qlawkus.tools.mcp;

import tools.jackson.databind.JsonNode;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Real loopback JSON-RPC server, supporting both released MCP protocol variants. */
final class FakeMcpHttpServer implements AutoCloseable {
    final FakeMcpProtocol protocol = new FakeMcpProtocol();
    final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
    final AtomicInteger openConnections = new AtomicInteger();
    final Vertx vertx = Vertx.vertx();
    final HttpServer server;
    volatile String requiredAuthorization;
    volatile String lastAuthorization;
    volatile JsonNode lastArguments;
    volatile boolean failDiscovery;
    volatile boolean failCalls;
    volatile boolean eventStreamReplies;
    volatile String redirectTo;
    volatile Duration toolDelay = Duration.ZERO;

    FakeMcpHttpServer() throws Exception {
        server = vertx.createHttpServer().connectionHandler(connection -> {
            openConnections.incrementAndGet();
            connection.closeHandler(ignored -> openConnections.decrementAndGet());
        }).requestHandler(request -> {
            if (!request.method().name().equals("POST")) {
                request.response().setStatusCode(request.method().name().equals("DELETE") ? 200 : 405).end();
                return;
            }
            request.body().onSuccess(body -> {
                try {
                    JsonNode message = FakeMcpProtocol.JSON.readTree(body.getBytes());
                    String method = message.path("method").asString();
                    requests.computeIfAbsent(method, ignored -> new AtomicInteger()).incrementAndGet();
                    if (redirectTo != null) {
                        request.response().setStatusCode(307).putHeader("Location", redirectTo).end();
                        return;
                    }
                    lastAuthorization = request.getHeader("Authorization");
                    if (requiredAuthorization != null && !requiredAuthorization.equals(lastAuthorization)) {
                        request.response().setStatusCode(401).end("MCP_FIXTURE_PRIVATE_AUTH_ERROR");
                        return;
                    }
                    if ((failDiscovery && (method.equals("server/discover") || method.equals("initialize")
                            || method.equals("tools/list"))) || (failCalls && method.equals("tools/call"))) {
                        request.response().setStatusCode(500).end("MCP_FIXTURE_PRIVATE_SERVER_ERROR");
                        return;
                    }
                    if (method.equals("subscriptions/listen")) {
                        request.response().putHeader("Content-Type", "text/event-stream")
                                .end("event: message\ndata: " + FakeMcpProtocol.subscriptionAck(message) + "\n\n");
                        return;
                    }
                    if (method.equals("initialize")) {
                        request.response().putHeader("Mcp-Session-Id", "fixture-session");
                    }
                    if (method.equals("tools/call")) {
                        lastArguments = message.path("params").path("arguments").deepCopy();
                    }
                    var response = protocol.reply(message, Map.of());
                    Runnable send = () -> {
                        if (response == null) {
                            request.response().setStatusCode(202).end();
                        } else if (eventStreamReplies) {
                            request.response().putHeader("Content-Type", "text/event-stream")
                                    .end("event: message\ndata: " + response + "\n\n");
                        } else {
                            request.response().putHeader("Content-Type", "application/json").end(response.toString());
                        }
                    };
                    if (method.equals("tools/call") && message.path("params").path("name").asString().equals("slow")) {
                        vertx.setTimer(Math.max(1, toolDelay.toMillis()), ignored -> send.run());
                    } else {
                        send.run();
                    }
                } catch (Exception e) {
                    request.response().setStatusCode(400).end("Invalid fixture request");
                }
            });
        }).listen(0, "127.0.0.1").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    String url() {
        return "http://127.0.0.1:" + server.actualPort() + "/mcp";
    }

    int count(String method) {
        return requests.getOrDefault(method, new AtomicInteger()).get();
    }

    @Override
    public void close() throws Exception {
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
