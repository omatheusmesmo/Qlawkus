package dev.omatheusmesmo.qlawkus.model;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
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

/**
 * A kind with no same-provider model (here the default, {@code openai}) has the secondary tier disabled,
 * so a failing primary goes straight to the native Ollama fallback. This is the shape of the
 * {@code local} Compose stack, which must keep working with no external key. The primary and Ollama
 * share one WireMock: the completions endpoint for the primary, Ollama's {@code /api/chat} for the
 * fallback. No request may reach a secondary completions call.
 */
@QuarkusTest
@TestProfile(LocalOllamaFallbackTest.OpenAiKindOverWireMock.class)
@ConnectWireMock
class LocalOllamaFallbackTest {

    private static final String COMPLETIONS = "/v1/chat/completions";
    private static final String OLLAMA_CHAT = "/api/chat";
    private static final String PRIMARY = "gpt-5-mini";
    private static final String OLLAMA = "qwen3.5:4b";

    public static class OpenAiKindOverWireMock implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
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
    void resetBreaker() {
        CircuitBreakerMaintenance.get().reset(ModelFallbackConfig.CIRCUIT_BREAKER_CHAT);
        wiremock.resetRequests();
    }

    @AfterEach
    void removeStubs() {
        stubs.forEach(wiremock::removeStubMapping);
        stubs.clear();
    }

    @Test
    void aTransientPrimaryFailureAnswersFromOllamaWithNoSecondaryCall() {
        stubPrimaryDown();
        stubOllamaJson("from-ollama");

        ChatResponse response = chatModel.chat(ChatRequest.builder().messages(UserMessage.from("hi")).build());

        assertEquals("from-ollama", response.aiMessage().text());
        wiremock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo(OLLAMA_CHAT))
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(OLLAMA))));
        wiremock.verifyThat(0, WireMock.postRequestedFor(WireMock.urlEqualTo(COMPLETIONS))
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo("z-ai/glm-5.3"))));
    }

    @Test
    void aTransientPrimaryFailureStreamsTheReplyFromOllama() throws Exception {
        stubPrimaryDown();
        stubOllamaStream("from-ollama-stream");

        List<String> partials = new ArrayList<>();
        CompletableFuture<ChatResponse> done = new CompletableFuture<>();
        streamingChatModel.chat("hi", new StreamingChatResponseHandler() {
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
        });

        ChatResponse response = done.get(30, TimeUnit.SECONDS);

        assertEquals("from-ollama-stream", response.aiMessage().text());
        assertEquals(List.of("from-ollama-stream"), partials);
        wiremock.verifyThat(1, WireMock.postRequestedFor(WireMock.urlEqualTo(OLLAMA_CHAT))
                .withRequestBody(WireMock.matchingJsonPath("$.stream", WireMock.equalTo("true"))));
    }

    private void stubPrimaryDown() {
        stubs.add(wiremock.register(WireMock.post(WireMock.urlEqualTo(COMPLETIONS))
                .atPriority(1)
                .withRequestBody(WireMock.matchingJsonPath("$.model", WireMock.equalTo(PRIMARY)))
                .willReturn(WireMock.aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"overloaded\"}"))));
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
