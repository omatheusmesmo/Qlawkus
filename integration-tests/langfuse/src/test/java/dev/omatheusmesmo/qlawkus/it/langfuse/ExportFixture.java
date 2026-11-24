package dev.omatheusmesmo.qlawkus.it.langfuse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.omatheusmesmo.qlawkus.config.SecretsConfig;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ExportFixture implements QuarkusTestResourceLifecycleManager {

    private static final ObjectMapper JSON = new ObjectMapper();
    static final BlockingQueue<ExportTraceServiceRequest> TEMPO = new LinkedBlockingQueue<>();
    static final BlockingQueue<ExportTraceServiceRequest> LANGFUSE = new LinkedBlockingQueue<>();
    static final BlockingQueue<String> AUTH = new LinkedBlockingQueue<>();
    private static final String STORE_PASSWORD = "fixture-keystore-password";
    private HttpServer server;
    private Path keystore;

    @Override
    public Map<String, String> start() {
        try {
            keystore = Files.createTempFile("qlawkus-langfuse-test-", ".p12");
            Files.delete(keystore);
            var writer = new KeystoreSecretWriter(new SecretsConfig() {
                @Override
                public String keystorePath() {
                    return keystore.toString();
                }

                @Override
                public Optional<String> keystorePassword() {
                    return Optional.of(STORE_PASSWORD);
                }

                @Override
                public int keystoreOrdinal() {
                    return 350;
                }
            });
            writer.setSecret("quarkus.langfuse.public-key", "pk-fixture");
            writer.setSecret("quarkus.langfuse.secret-key", "sk-fixture");

            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/traces", exchange -> export(exchange, TEMPO));
            server.createContext("/api/public/otel/v1/traces", exchange -> {
                AUTH.add(exchange.getRequestHeaders().getFirst("Authorization"));
                export(exchange, LANGFUSE);
            });
            server.createContext("/v1/chat/completions", ExportFixture::reply);
            server.createContext("/v1/embeddings", exchange -> sendJson(exchange, """
                    {"object":"list","data":[{"object":"embedding","index":0,"embedding":[%s]}],
                     "model":"fixture-embedding","usage":{"prompt_tokens":2,"total_tokens":2}}
                    """.formatted("0.01,".repeat(1023) + "0.01")));
            server.start();
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            return Map.of(
                    "quarkus.langfuse.base-url", url,
                    "quarkus.otel.exporter.otlp.traces.endpoint", url,
                    "qlawkus.secrets.keystore-path", keystore.toString(),
                    "qlawkus.secrets.keystore-password", STORE_PASSWORD,
                    "quarkus.langchain4j.openai.\"primary\".base-url", url + "/v1",
                    "quarkus.langchain4j.openai.\"primary\".api-key", "llm-fixture",
                    "qlawkus.embedding.base-url", url + "/v1",
                    "qlawkus.embedding.api-key", "llm-fixture");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void export(HttpExchange exchange, BlockingQueue<ExportTraceServiceRequest> destination) throws IOException {
        try (exchange) {
            destination.add(ExportTraceServiceRequest.parseFrom(exchange.getRequestBody()));
            exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
            exchange.sendResponseHeaders(200, -1);
        }
    }

    private static void reply(HttpExchange exchange) throws IOException {
        try (exchange) {
            var messages = JSON.readTree(exchange.getRequestBody()).path("messages");
            String assistant = messages.path(messages.size() - 1).path("role").asText().equals("tool")
                    ? """
                            "message":{"role":"assistant","content":"private-completion-fixture"},"finish_reason":"stop"
                            """
                    : """
                            "message":{"role":"assistant","tool_calls":[
                              {"id":"voice-call","type":"function","function":{"name":"respondWithVoice","arguments":"{\\"language\\":\\"en\\"}"}},
                              {"id":"glob-call","type":"function","function":{"name":"globFiles","arguments":"{\\"pattern\\":\\"private-glob-fixture\\"}"}}
                            ]},"finish_reason":"tool_calls"
                            """;
            sendJson(exchange, """
                    {"id":"fixture","object":"chat.completion","created":1,"model":"fixture-model",
                     "choices":[{"index":0,%s}],"usage":{"prompt_tokens":9,"completion_tokens":3,"total_tokens":12}}
                    """.formatted(assistant));
        }
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        try (exchange) {
            byte[] response = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (keystore != null) {
            try {
                Files.deleteIfExists(keystore);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
