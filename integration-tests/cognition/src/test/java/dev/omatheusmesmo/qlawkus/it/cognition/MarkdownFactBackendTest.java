package dev.omatheusmesmo.qlawkus.it.cognition;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownFactStore;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration test for the markdown fact backend against the module's real {@link EmbeddingModel}
 * bean (WireMock-backed in tests). The store is instantiated directly with a temp root (mirroring
 * {@code HybridSkillStoreTest}), so no separate markdown-pinned module is needed: a fact is written
 * as a {@code .md} file and then retrieved through the actual embedding pipeline. Stubs are
 * registered per test so the suite passes in isolation, not only when another test seeds them first.
 */
@QuarkusTest
@ConnectWireMock
class MarkdownFactBackendTest {

  WireMock wiremock;

  @Inject
  EmbeddingModel embeddingModel;

  @TempDir
  Path factsRoot;

  @BeforeEach
  void setupStubs() {
    QlawkusWireMockStubs.registerOpenAiStubs(wiremock);
  }

  @Test
  void storesFactAsFileAndRetrievesThroughEmbeddingPipeline() throws IOException {
    MarkdownFactStore store = new MarkdownFactStore(factsRoot.toString(), embeddingModel);
    store.load();

    store.store("User prefers dark theme in IDE", Map.of("source", "semantic-extractor"));

    try (Stream<Path> files = Files.list(factsRoot)) {
      assertTrue(files.anyMatch(p -> p.getFileName().toString().endsWith(".md")),
          "fact should be persisted as a .md file");
    }

    List<String> facts = store.search("IDE theme preference", 5, 0.0);
    assertTrue(facts.stream().anyMatch(f -> f.toLowerCase().contains("dark theme")),
        "stored fact should be retrievable through the embedding store");
  }
}
