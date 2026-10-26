package dev.omatheusmesmo.qlawkus.model;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The NVIDIA kind's three tiers, driven through the real {@link FallbackChatModel} and
 * {@link FallbackStreamingChatModel} against one WireMock. The OpenAI-compatible completions endpoint
 * serves GLM (the primary) and Nemotron Ultra (the secondary on the same endpoint), told apart by the
 * {@code model} member of the request body; the Ollama fallback speaks its native {@code /api/chat} on
 * the same server. What is proven is the wire request each tier receives, not a double.
 */
@QuarkusTest
@TestProfile(GlmWithUltraFallbackTest.NvidiaOverWireMock.class)
@ConnectWireMock
class GlmWithUltraFallbackTest {

    private static final String COMPLETIONS = "/v1/chat/completions";
    private static final String OLLAMA_CHAT = "/api/chat";
    private static final String GLM = "z-ai/glm-5.3";
    private static final String ULTRA = "nvidia/nemotron-3-ultra-550b-a55b";
    private static final String OLLAMA = "qwen3.5:4b";

    public static class NvidiaOverWireMock implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "qlawkus.openai.kind", "nvidia",
                    "qlawkus.model.circuit-breaker-reset-timeout", "60s",
                    "quarkus.langchain4j.\"primary\".chat-model.provider", "openai");
        }
    }

    WireMock wiremock;

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    private final List<StubMapping> stubs = new ArrayList<>();

    @BeforeEach
    void resetBreakers() {
        CircuitBreakerMaintenance.get().reset(ModelFallbackConfig.CIRCUIT_BREAKER_CHAT);
        CircuitBreakerMaintenance.get().reset(ModelFallbackConfig.CIRCUIT_BREAKER_SECONDARY_CHAT);
        wiremock.resetRequests();
    }

    @AfterEach
    void removeStubs() {
        stubs.forEach(wiremock::removeStubMapping);
        stubs.clear();
    }

    @Test
    void aTransientPrimaryFailureAnswersFromTheSecondaryAndKeepsTheToolsOnTheWire() {
        stubModelDown(GLM);
        stubCompletionJson(ULTRA, "from-ultra");

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .toolSpecifications(ToolSpecification.builder().name("lookup").description("look something up").build())
                .build();

        ChatResponse first = chatModel.chat(request);

        assertEquals("from-ultra", first.aiMessage().text());
        wiremock.verifyThat(WireMock.postRequestedFor(WireMock.urlEqualTo(COMPLETIONS))
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(GLM))));
        wiremock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo(COMPLETIONS))
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(ULTRA)))
                .withRequestBody(WireMock.matchingJsonPath("$.tools[0].function.name", WireMock.equalTo("lookup"))));
        wiremock.verifyThat(0, WireMock.postRequestedFor(WireMock.urlEqualTo(OLLAMA_CHAT)));
        assertEquals(CircuitBreakerState.OPEN,
                CircuitBreakerMaintenance.get().currentState(ModelFallbackConfig.CIRCUIT_BREAKER_CHAT));

        int primaryHits = hits(GLM);
        assertTrue(primaryHits >= 3, "the primary is retried before it is abandoned");

        ChatResponse second = chatModel.chat(request);

        assertEquals("from-ultra", second.aiMessage().text());
        assertEquals(primaryHits, hits(GLM), "an open breaker goes straight to the secondary");
    }

    @Test
    void aTransientPrimaryFailureStreamsTheReplyFromTheSecondary() throws Exception {
        stubModelDown(GLM);
        stubStreamSse(ULTRA, "from-ultra-stream");

        List<String> partials = new ArrayList<>();
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        streamingChatModel.chat("hi", handler(partials, done));

        ChatResponse response = done.get(30, TimeUnit.SECONDS);

        assertEquals("from-ultra-stream", response.aiMessage().text());
        assertEquals(List.of("from-ultra-stream"), partials);
        assertTrue(hits(GLM) >= 3, "GLM is tried and retried before the reply moves to Ultra");
        wiremock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo(COMPLETIONS))
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(ULTRA)))
                .withRequestBody(WireMock.matchingJsonPath("$.stream", WireMock.equalTo("true"))));
    }

    @Test
    void whenThePrimaryAndTheSecondaryAreDownTheReplyComesFromOllama() {
        stubModelDown(GLM);
        stubModelDown(ULTRA);
        stubOllamaJson("from-ollama");

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .build());

        assertEquals("from-ollama", response.aiMessage().text());
        wiremock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo(OLLAMA_CHAT))
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(OLLAMA))));
        assertEquals(CircuitBreakerState.OPEN,
                CircuitBreakerMaintenance.get().currentState(ModelFallbackConfig.CIRCUIT_BREAKER_SECONDARY_CHAT));
    }

    @Test
    void whenThePrimaryAndTheSecondaryAreDownTheStreamComesFromOllama() throws Exception {
        stubModelDown(GLM);
        stubModelDown(ULTRA);
        stubOllamaStream("from-ollama-stream");

        List<String> partials = new ArrayList<>();
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        streamingChatModel.chat("hi", handler(partials, done));

        ChatResponse response = done.get(30, TimeUnit.SECONDS);

        assertEquals("from-ollama-stream", response.aiMessage().text());
        assertEquals(List.of("from-ollama-stream"), partials);
        wiremock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo(OLLAMA_CHAT))
                .withRequestBody(WireMock.matchingJsonPath("$.stream", WireMock.equalTo("true"))));
    }

    /**
     * A permanent answer from the secondary (the model retired, or the key no longer entitled to it)
     * must not end the chain: the primary already failed, so only Ollama is left to answer.
     */
    @Test
    void aSecondaryThatAnswersPermanentlyStillFallsThroughToOllama() {
        stubModelDown(GLM);
        stubModelStatus(ULTRA, 404, "{\"error\":\"model not found\"}");
        stubOllamaJson("from-ollama");

        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .build());

        assertEquals("from-ollama", response.aiMessage().text());
        wiremock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo(COMPLETIONS))
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(ULTRA))));
    }

    @Test
    void aSecondaryThatAnswersPermanentlyStillStreamsFromOllama() throws Exception {
        stubModelDown(GLM);
        stubModelStatus(ULTRA, 401, "{\"error\":\"unauthorized\"}");
        stubOllamaStream("from-ollama-stream");

        List<String> partials = new ArrayList<>();
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        streamingChatModel.chat("hi", handler(partials, done));

        assertEquals("from-ollama-stream", done.get(30, TimeUnit.SECONDS).aiMessage().text());
    }

    private static StreamingChatResponseHandler handler(List<String> partials, CompletableFuture<ChatResponse> done) {
        return new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partial) {
                partials.add(partial);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                done.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        };
    }

    private int hits(String model) {
        return wiremock.find(WireMock.postRequestedFor(WireMock.urlEqualTo(COMPLETIONS))
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(model)))).size();
    }

    private void stubModelDown(String model) {
        stubModelStatus(model, 503, "{\"error\":\"overloaded\"}");
    }

    private void stubModelStatus(String model, int status, String body) {
        stubs.add(wiremock.register(WireMock.post(WireMock.urlEqualTo(COMPLETIONS))
                .atPriority(1)
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(model)))
                .willReturn(WireMock.aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body))));
    }

    private void stubCompletionJson(String model, String text) {
        stubs.add(wiremock.register(WireMock.post(WireMock.urlEqualTo(COMPLETIONS))
                .atPriority(1)
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(model)))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"c1","object":"chat.completion","created":1,"model":"%s",
                                 "choices":[{"index":0,"message":{"role":"assistant","content":"%s"},
                                 "finish_reason":"stop"}],
                                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                                """.formatted(model, text)))));
    }

    private void stubStreamSse(String model, String text) {
        String body = "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"" + model
                + "\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"" + text
                + "\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"" + model
                + "\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n";
        stubs.add(wiremock.register(WireMock.post(WireMock.urlEqualTo(COMPLETIONS))
                .atPriority(1)
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(model)))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(body))));
    }

    private void stubOllamaJson(String text) {
        stubs.add(wiremock.register(WireMock.post(WireMock.urlEqualTo(OLLAMA_CHAT))
                .atPriority(1)
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(OLLAMA)))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaLine(text, true)))));
    }

    private void stubOllamaStream(String text) {
        stubs.add(wiremock.register(WireMock.post(WireMock.urlEqualTo(OLLAMA_CHAT))
                .atPriority(1)
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(OLLAMA)))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-ndjson")
                        .withBody(ollamaLine(text, false) + "\n" + ollamaLine("", true)))));
    }

    private static String ollamaLine(String text, boolean done) {
        return "{\"model\":\"" + OLLAMA + "\",\"created_at\":\"2026-10-26T00:00:00Z\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"" + text + "\"},\"done\":" + done
                + (done ? ",\"done_reason\":\"stop\",\"prompt_eval_count\":1,\"eval_count\":1" : "") + "}";
    }
}
