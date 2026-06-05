package dev.omatheusmesmo.qlawkus.testing;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public class QlawkusWireMockStubs {

    public static void registerOpenAiStubs(WireMock wiremock) {
        StubMapping streamingStub = post(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(containing("\"stream\""))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(openAiChatStreamingResponse()))
                .atPriority(10)
                .build();

        wiremock.register(streamingStub);

        wiremock.register(post(urlEqualTo("/v1/chat/completions"))
                .atPriority(20)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(openAiChatResponse())));

        wiremock.register(post(urlEqualTo("/v1/embeddings"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(openAiEmbedResponse())));
    }

    public static void registerOllamaStubs(WireMock wiremock) {
        wiremock.register(post(urlEqualTo("/api/chat"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-ndjson")
                        .withBody(ollamaChatNdjsonResponse())));

        wiremock.register(post(urlEqualTo("/api/embed"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ollamaEmbedJsonResponse())));
    }

    private static String openAiChatResponse() {
        return """
                {
                  "id": "chatcmpl-mock",
                  "object": "chat.completion",
                  "created": 1677652288,
                  "model": "z-ai/glm-5.1",
                  "system_fingerprint": "fp_mock",
                  "choices": [{
                    "index": 0,
                    "message": {
                      "role": "assistant",
                      "content": "QlawkusMock"
                    },
                    "logprobs": null,
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": 9,
                    "completion_tokens": 3,
                    "total_tokens": 12
                  }
                }
                """;
    }

    private static String openAiChatStreamingResponse() {
        return "data: {\"id\":\"chatcmpl-mock\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,"
                + "\"model\":\"z-ai/glm-5.1\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\","
                + "\"content\":\"QlawkusMock\"},\"finish_reason\":null}]}\n\n"
                + "data: {\"id\":\"chatcmpl-mock\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,"
                + "\"model\":\"z-ai/glm-5.1\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n";
    }

    private static String openAiEmbedResponse() {
        StringBuilder embedding = new StringBuilder("[");
        for (int i = 0; i < 1024; i++) {
            if (i > 0) {
                embedding.append(",");
            }
            embedding.append("0.01");
        }
        embedding.append("]");
        return """
                {
                  "object": "list",
                  "data": [{
                    "object": "embedding",
                    "embedding": """ + embedding + """
                ,
                  "index": 0
                  }],
                  "model": "nvidia/nv-embedqa-e5-v5",
                  "usage": {
                    "prompt_tokens": 5,
                    "total_tokens": 5
                  }
                }
                """;
    }

    private static String ollamaChatNdjsonResponse() {
        String ts = "2026-01-01T00:00:00Z";
        return "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts
                + "\",\"message\":{\"role\":\"assistant\",\"content\":\"QlawkusMock\"},\"done\":false}\n"
                + "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts
                + "\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,"
                + "\"total_duration\":0,\"eval_count\":1}\n";
    }

    private static String ollamaEmbedJsonResponse() {
        StringBuilder embedding = new StringBuilder("[");
        for (int i = 0; i < 1024; i++) {
            if (i > 0) {
                embedding.append(",");
            }
            embedding.append("0.01");
        }
        embedding.append("]");
        return "{\"model\":\"nvidia/nv-embedqa-e5-v5\",\"embeddings\":[" + embedding + "],\"total_duration\":0}";
    }

    private QlawkusWireMockStubs() {
    }
}
