package dev.omatheusmesmo.qlawkus.it.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.FactChunker;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import dev.omatheusmesmo.qlawkus.store.pg.HybridFactStore;
import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionReconciler;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.arc.ClientProxy;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A near-duplicate removed on the hybrid backend must go from the pgvector table and from the
 * markdown file that is the source of truth, and a following reconcile must not write it back.
 * The stubbed embedding endpoint returns one vector for every text, so both facts are nominated and
 * the judge decides; which of the two is the duplicate depends on the generated ids, so the test
 * reads it back from what survived.
 */
@QuarkusTest
@ConnectWireMock
@TestProfile(HybridNearDuplicatePurgeTest.HybridBackend.class)
class HybridNearDuplicatePurgeTest {

  private static final String PREFERENCE = "User prefers dark theme in IDE";
  private static final String CORRECTION = "User no longer prefers dark theme in IDE";
  private static final double MAX_DISTANCE = 0.03;

  WireMock wiremock;

  @Inject
  FactStore factStore;

  @Inject
  AgentConfig config;

  @Inject
  CognitionReconciler reconciler;

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
  void aConfirmedDuplicateIsRemovedFromTableAndFilesAndReconcileDoesNotRestoreIt() {
    assertInstanceOf(HybridFactStore.class, ClientProxy.unwrap(factStore),
        "the profile must select the hybrid backend at build time");
    factStore.store(PREFERENCE, Map.of("source", MemorySource.REMEMBER_TOOL.value()));
    factStore.store(CORRECTION, Map.of("source", MemorySource.REMEMBER_TOOL.value()));

    long removed = factStore.purgeNearDuplicates(MAX_DISTANCE, (kept, candidate) -> true);

    assertEquals(1, removed);
    List<String> survivors = factStore.listFactTexts(10);
    assertEquals(1, survivors.size(), "the table should hold only the kept fact");
    String survivor = survivors.get(0);
    String duplicate = survivor.equals(PREFERENCE) ? CORRECTION : PREFERENCE;
    assertTrue(Files.exists(factFile(survivor)), "the kept fact keeps its file");
    assertFalse(Files.exists(factFile(duplicate)), "the duplicate's file must be removed with its row");

    reconciler.reconcileAll();

    assertEquals(List.of(survivor), factStore.listFactTexts(10),
        "a reconcile must not bring the removed duplicate back into pgvector");
    assertFalse(Files.exists(factFile(duplicate)), "a reconcile must not write the duplicate's file back");
  }

  private Path factFile(String text) {
    return Path.of(config.facts().root()).resolve(FactChunker.factHash(text) + ".md");
  }

  public static class HybridBackend implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qlawkus.cognition.backend", "hybrid",
          "quarkus.arc.remove-unused-beans", "false",
          "qlawkus.agent.facts.root", "target/hybrid-near-duplicate-purge-it/facts",
          "qlawkus.agent.episodic.root", "target/hybrid-near-duplicate-purge-it/journals",
          "qlawkus.agent.working-memory.root", "target/hybrid-near-duplicate-purge-it/working-memory",
          "qlawkus.agent.state.root", "target/hybrid-near-duplicate-purge-it/state");
    }
  }
}
