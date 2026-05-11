package dev.omatheusmesmo.qlawkus.it.brag;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class BragTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                Map.entry("qlawkus.brag.enabled", "true"),
                Map.entry("qlawkus.brag.impact-translation-enabled", "true"),
                Map.entry("qlawkus.brag.cleanup-cron", "0 0 3 * * ?"),
                Map.entry("qlawkus.startup-thought.enabled", "false"),
                Map.entry("quarkus.langchain4j.chat-model.provider", "openai"),
                Map.entry("quarkus.langchain4j.openai.base-url", "https://integrate.api.nvidia.com/v1"),
                Map.entry("quarkus.langchain4j.openai.api-key", "${NVIDIA_AI_API_KEY}"),
                Map.entry("quarkus.langchain4j.openai.chat-model.model-name", "z-ai/glm-5.1"),
                Map.entry("quarkus.langchain4j.openai.chat-model.temperature", "0"),
                Map.entry("quarkus.langchain4j.openai.timeout", "120s"),
                Map.entry("quarkus.langchain4j.openai.max-retries", "5"),
                Map.entry("quarkus.langchain4j.ollama.devservices.enabled", "false"));
    }
}
