package dev.omatheusmesmo.qlawkus.it.google;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class GoogleWireMockProfile implements QuarkusTestProfile {

    static final String WIREMOCK_URL = "http://localhost:${quarkus.wiremock.devservices.port}";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                Map.entry("quarkus.wiremock.devservices.enabled", "true"),
                Map.entry("qlawkus.google.auth.client-id", "test-client-id"),
                Map.entry("qlawkus.google.auth.client-secret", "test-client-secret"),
                Map.entry("qlawkus.google.calendar.enabled", "true"),
                Map.entry("qlawkus.google.gmail.enabled", "true"),
                Map.entry("qlawkus.google.drive.enabled", "true"),
                Map.entry("qlawkus.google.sheets.enabled", "true"),
                Map.entry("qlawkus.google.storage.enabled", "true"),
                Map.entry("qlawkus.google.vault.enabled", "false"),
                Map.entry("qlawkus.google.calendar.calendar-id", "primary"),
                Map.entry("qlawkus.google.storage.project-id", "test-project"),
                Map.entry("quarkus.rest-client.\"dev.omatheusmesmo.qlawkus.tools.google.calendar.GoogleCalendarRestClient\".url", WIREMOCK_URL),
                Map.entry("quarkus.rest-client.\"dev.omatheusmesmo.qlawkus.tools.google.gmail.GoogleGmailRestClient\".url", WIREMOCK_URL),
                Map.entry("quarkus.rest-client.\"dev.omatheusmesmo.qlawkus.tools.google.drive.GoogleDriveRestClient\".url", WIREMOCK_URL),
                Map.entry("quarkus.rest-client.\"dev.omatheusmesmo.qlawkus.tools.google.drive.GoogleDriveDownloadClient\".url", WIREMOCK_URL),
                Map.entry("quarkus.rest-client.\"dev.omatheusmesmo.qlawkus.tools.google.drive.GoogleDriveUploadClient\".url", WIREMOCK_URL),
                Map.entry("quarkus.rest-client.\"dev.omatheusmesmo.qlawkus.tools.google.sheets.GoogleSheetsRestClient\".url", WIREMOCK_URL),
                Map.entry("quarkus.rest-client.\"dev.omatheusmesmo.qlawkus.tools.google.storage.GoogleStorageRestClient\".url", WIREMOCK_URL),
                Map.entry("quarkus.rest-client.\"dev.omatheusmesmo.qlawkus.tools.google.storage.GoogleStorageDownloadClient\".url", WIREMOCK_URL),
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
