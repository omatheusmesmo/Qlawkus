package dev.omatheusmesmo.qlawkus.rest;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class WireMockSseProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "quarkus.langchain4j.ollama.devservices.enabled", "false",
        "quarkus.langchain4j.ollama.base-url",
        "http://localhost:${quarkus.wiremock.devservices.port}",
        "qlawkus.startup-thought.enabled", "false",
        "quarkus.wiremock.devservices.enabled", "true");
  }
}
