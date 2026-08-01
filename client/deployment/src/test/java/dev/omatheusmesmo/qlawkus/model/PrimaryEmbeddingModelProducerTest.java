package dev.omatheusmesmo.qlawkus.model;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * Pins the two embedding request parameters the producer exists for. Neither is expressible through
 * the quarkus-langchain4j openai config, which is why the producer builds the upstream model by
 * hand, and neither is covered by any live-LLM test: CI runs without a provider key, so this asserts
 * the wire format against a stub instead.
 */
@QuarkusTest
@ConnectWireMock
class PrimaryEmbeddingModelProducerTest {

  private static final String EMBEDDINGS_PATH = "/v1/embeddings";

  WireMock wiremock;

  @ConfigProperty(name = "quarkus.wiremock.devservices.port")
  int wiremockPort;

  @BeforeEach
  void stubEmbeddings() {
    wiremock.register(WireMock.post(WireMock.urlEqualTo(EMBEDDINGS_PATH))
        .willReturn(WireMock.aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(embeddingResponse())));
  }

  /**
   * NVIDIA reads {@code input_type} as a top-level member of the embedding request. langchain4j
   * carries it as an opaque custom parameter, so only the serialized body proves it survives the
   * trip.
   */
  @Test
  void nvidiaInputTypeIsSentAsATopLevelRequestMember() {
    embedWith(Optional.of(1024), Optional.of("query"));

    wiremock.verifyThat(WireMock.postRequestedFor(WireMock.urlEqualTo(EMBEDDINGS_PATH))
        .withRequestBody(WireMock.matchingJsonPath("$.input_type", WireMock.equalTo("query")))
        .withRequestBody(WireMock.matchingJsonPath("$.dimensions", WireMock.equalTo("1024"))));
  }

  /**
   * Only providers supporting Matryoshka reduction accept {@code dimensions}; a native-dimension
   * model rejects the request when it is present. Absent config has to mean an absent member, not a
   * null one.
   */
  @Test
  void absentParametersAreOmittedRatherThanSentAsNull() {
    embedWith(Optional.empty(), Optional.empty());

    wiremock.verifyThat(WireMock.postRequestedFor(WireMock.urlEqualTo(EMBEDDINGS_PATH))
        .withRequestBody(WireMock.notMatching(".*\"dimensions\".*"))
        .withRequestBody(WireMock.notMatching(".*\"input_type\".*")));
  }

  private void embedWith(Optional<Integer> dimensions, Optional<String> inputType) {
    EmbeddingModel model = new PrimaryEmbeddingModelProducer().primaryEmbeddingModel(
        "http://localhost:" + wiremockPort + "/v1",
        "test-key",
        "nvidia/nv-embedqa-e5-v5",
        dimensions,
        inputType);

    model.embed("a sentence to embed");
  }

  private static String embeddingResponse() {
    StringBuilder vector = new StringBuilder("[");
    for (int i = 0; i < 1024; i++) {
      if (i > 0) {
        vector.append(",");
      }
      vector.append("0.01");
    }
    vector.append("]");
    return "{\"data\":[{\"embedding\":" + vector + ",\"index\":0}],"
        + "\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
  }
}
