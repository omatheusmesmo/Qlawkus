package dev.omatheusmesmo.qlawkus.it.cognition;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;
import dev.omatheusmesmo.qlawkus.cognition.ActiveMemoryAugmentor;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import dev.omatheusmesmo.qlawkus.store.pg.HybridFactStore;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.arc.ClientProxy;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Active memory on the hybrid backend must recall from the pgvector index that
 * {@link HybridFactStore} writes, not from the markdown store's in-process index. The profile keeps
 * every bean, so the markdown fact store survives the build exactly as marking it
 * {@code @Unremovable} would: recall has to follow the selected {@link FactStore}, not whichever
 * concrete store happens to be left in the container.
 */
@QuarkusTest
@ConnectWireMock
@TestProfile(HybridActiveMemoryTest.HybridBackend.class)
class HybridActiveMemoryTest {

  WireMock wiremock;

  @Inject
  ActiveMemoryAugmentor activeMemory;

  @Inject
  FactStore factStore;

  @BeforeEach
  void setup() {
    QlawkusWireMockStubs.registerOpenAiStubs(wiremock);
    QuarkusTransaction.requiringNew().run(factStore::purgeAll);
  }

  @AfterEach
  void cleanup() {
    QuarkusTransaction.requiringNew().run(factStore::purgeAll);
  }

  @Test
  void recallsAFactStoredThroughTheHybridStore() {
    assertInstanceOf(HybridFactStore.class, ClientProxy.unwrap(factStore),
        "the profile must select the hybrid backend at build time");
    RetrievalAugmentor augmentor = activeMemory.get();
    String fact = "User prefers dark theme in IDE " + UUID.randomUUID();

    factStore.store(fact, Map.of("source", MemorySource.REMEMBER_TOOL.value()));

    UserMessage question = UserMessage.from("IDE theme preference");
    AugmentationResult result = augmentor.augment(
        new AugmentationRequest(question, Metadata.from(question, "hybrid-recall", List.of())));
    assertTrue(result.contents().stream().map(Content::textSegment).anyMatch(s -> s.text().equals(fact)),
        "active memory should retrieve the fact from the pgvector index the hybrid store writes");
  }

  public static class HybridBackend implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qlawkus.cognition.backend", "hybrid",
          "quarkus.arc.remove-unused-beans", "false",
          "qlawkus.agent.facts.root", "target/hybrid-active-memory-it/facts");
    }
  }
}
