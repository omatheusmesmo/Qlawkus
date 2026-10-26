package dev.omatheusmesmo.qlawkus.messaging.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.UUID;

/**
 * Stubs the chat model a real {@code chatSync} turn talks to, so tool calls happen for real on the
 * turn thread. Each turn gets its own token carried in the user message, and its replies are a
 * scripted scenario keyed by that token, so a background extractor call never consumes a script.
 */
final class ModelStubs {

    static final String CHAT = "/api/chat";
    private static final String MODEL = "gemma4:e2b";

    private ModelStubs() {
    }

    static String newToken() {
        return "turn-" + UUID.randomUUID();
    }

    static void register(WireMock wiremock) {
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/embed"))
                .willReturn(WireMock.okJson(ollamaEmbedding())));
        wiremock.register(WireMock.post(WireMock.urlEqualTo("/v1/embeddings"))
                .willReturn(WireMock.okJson(openAiEmbedding())));
        wiremock.register(WireMock.post(WireMock.urlEqualTo(CHAT)).atPriority(10)
                .willReturn(okJson(text("unrelated"))));
    }

    static void script(WireMock wiremock, String token, String... replies) {
        String scenario = "scenario-" + token;
        for (int i = 0; i < replies.length; i++) {
            String state = i == 0 ? Scenario.STARTED : "step-" + i;
            ScenarioMappingBuilder step = post(urlEqualTo(CHAT)).atPriority(2)
                    .inScenario(scenario).whenScenarioStateIs(state)
                    .withRequestBody(containing(token));
            if (i + 1 < replies.length) {
                wiremock.register(step.willSetStateTo("step-" + (i + 1)).willReturn(okJson(replies[i])));
            } else {
                wiremock.register(step.willReturn(okJson(replies[i])));
            }
        }
    }

    static String toolCall(String name, String argumentsJson) {
        return response("\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"" + name
                + "\",\"arguments\":" + argumentsJson + "}}]");
    }

    static String text(String content) {
        return response("\"content\":\"" + content + "\"");
    }

    private static String response(String messageFields) {
        return "{\"model\":\"" + MODEL + "\",\"created_at\":\"2026-04-25T00:00:00Z\",\"message\":{\"role\":\"assistant\","
                + messageFields + "},\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":5,\"eval_count\":3}";
    }

    private static String ollamaEmbedding() {
        return "{\"model\":\"nomic-embed-text\",\"embeddings\":[" + vector(768) + "],\"total_duration\":0}";
    }

    private static String openAiEmbedding() {
        return "{\"data\":[{\"embedding\":" + vector(1024) + ",\"index\":0}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
    }

    private static String vector(int size) {
        return "[" + "0.01,".repeat(size - 1) + "0.01]";
    }
}
