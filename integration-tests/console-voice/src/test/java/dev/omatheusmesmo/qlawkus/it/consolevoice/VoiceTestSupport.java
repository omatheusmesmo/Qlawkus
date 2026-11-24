package dev.omatheusmesmo.qlawkus.it.consolevoice;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.client.WireMock;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;

/**
 * WireMock stubs for the Ollama chat and embedding endpoints the agent calls, and the origin a browser on
 * the test server would send.
 */
final class VoiceTestSupport {

    static final String USER = "qlawkus";
    static final String PASSWORD = "qlawkus-test";

    private VoiceTestSupport() {
    }

    static String origin(URI base) {
        return "http://" + base.getAuthority();
    }

    static void stubAgentReply(WireMock wiremock, String reply) {
        wiremock.register(post(urlEqualTo("/api/chat"))
                .withRequestBody(WireMock.notMatching("(?s).*(LANGUAGE_DECISION|VOICE_REPLY_LANGUAGE_CHECK).*"))
                .willReturn(aResponse().withHeader("Content-Type", "application/x-ndjson")
                        .withBody(textNdjson(reply))));
    }

    static void stubEmbeddings(WireMock wiremock) {
        stubLanguageCheck(wiremock, null, 10);
        // Ordinary voice tests exercise the local fallback; decision-specific tests override this stub.
        wiremock.register(post(urlEqualTo("/api/chat")).withRequestBody(WireMock.containing("LANGUAGE_DECISION"))
                .atPriority(10).willReturn(WireMock.okJson("""
                {"model":"gemma4:e2b","created_at":"2026-11-21T00:00:00Z",
                 "message":{"role":"assistant","content":"UNKNOWN"},"done":true,
                 "prompt_eval_count":1,"eval_count":1}
                """)));
        wiremock.register(post(urlEqualTo("/api/embed")).willReturn(WireMock.okJson(
                "{\"model\":\"nomic-embed-text\",\"embeddings\":[" + vector(768) + "],\"total_duration\":0}")));
        wiremock.register(post(urlEqualTo("/v1/embeddings")).willReturn(WireMock.okJson(
                "{\"data\":[{\"embedding\":" + vector(1024) + ",\"index\":0}],"
                        + "\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}")));
    }

    static void stubLanguageCheck(WireMock wiremock, String translated, int priority) {
        var json = JsonMapper.builder().build();
        String content = json.valueToTree(json.createObjectNode().put("text", translated).toString()).toString();
        wiremock.register(post(urlEqualTo("/api/chat")).withRequestBody(WireMock.containing("VOICE_REPLY_LANGUAGE_CHECK"))
                .atPriority(priority).willReturn(WireMock.okJson("""
                        {"model":"gemma4:e2b","created_at":"2026-11-21T00:00:00Z",
                         "message":{"role":"assistant","content":%s},"done":true,
                         "prompt_eval_count":1,"eval_count":1}
                        """.formatted(content))));
    }

    private static String textNdjson(String text) {
        String ts = "2026-04-25T00:00:01Z";
        String encoded = JsonMapper.builder().build().valueToTree(text).toString();
        return "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts + "\",\"message\":{\"role\":\"assistant\","
                + "\"content\":" + encoded + "},\"done\":false}\n"
                + "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts
                + "\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\","
                + "\"prompt_eval_count\":9,\"eval_count\":4}\n";
    }

    private static String vector(int size) {
        StringBuilder embedding = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                embedding.append(',');
            }
            embedding.append("0.01");
        }
        return embedding.append(']').toString();
    }
}
