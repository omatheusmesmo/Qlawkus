package dev.omatheusmesmo.qlawkus.it.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.pg.PgFactStore;
import io.quarkus.arc.ClientProxy;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The pgvector purge nominates close pairs by cosine distance and removes the later one only when a
 * judge confirms it restates the earlier. Rows are written with chosen vectors, because what is under
 * test is the SQL and the decision around it, not what an embedding model makes of a sentence.
 */
@QuarkusTest
class PgNearDuplicatePurgeTest {

  private static final String PREFERENCE = "User prefers dark theme in IDE";
  private static final String CORRECTION = "User no longer prefers dark theme in IDE";
  private static final double MAX_DISTANCE = 0.03;

  @Inject
  FactStore factStore;

  @Inject
  EntityManager entityManager;

  @BeforeEach
  void setup() {
    QuarkusTransaction.requiringNew().run(factStore::purgeAll);
  }

  @AfterEach
  void cleanup() {
    QuarkusTransaction.requiringNew().run(factStore::purgeAll);
  }

  private int dimension() {
    return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery(
        "SELECT atttypmod FROM pg_attribute WHERE attrelid = 'embeddings'::regclass AND attname = 'embedding'")
        .getSingleResult()).intValue());
  }

  private void insert(String text, double x, double y) {
    int dimension = dimension();
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < dimension; i++) {
      parts.add(i == 0 ? Double.toString(x) : i == 1 ? Double.toString(y) : "0");
    }
    String vector = "[" + String.join(",", parts) + "]";
    QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
        "INSERT INTO embeddings (embedding_id, embedding, text, metadata) "
            + "VALUES (?1, CAST(?2 AS vector), ?3, CAST(?4 AS jsonb))")
        .setParameter(1, UUID.randomUUID())
        .setParameter(2, vector)
        .setParameter(3, text)
        .setParameter(4, "{\"source\":\"semantic-extractor\"}")
        .executeUpdate());
  }

  private void insertClosePair() {
    insert(PREFERENCE, 1.0, 0.0);
    insert(CORRECTION, 0.971, 0.239);
  }

  @Test
  void aCorrectionTheJudgeRejectsSurvivesThePurge() {
    assertInstanceOf(PgFactStore.class, ClientProxy.unwrap(factStore), "this module runs on pgvector");
    insertClosePair();

    long removed = factStore.purgeNearDuplicates(MAX_DISTANCE, (kept, candidate) -> false);

    assertEquals(0, removed);
    assertEquals(Set.of(PREFERENCE, CORRECTION), Set.copyOf(factStore.listFactTexts(10)));
  }

  @Test
  void aRewordingTheJudgeConfirmsIsRemoved() {
    insertClosePair();

    long removed = factStore.purgeNearDuplicates(MAX_DISTANCE, (kept, candidate) -> true);

    assertEquals(1, removed);
    assertEquals(1, factStore.listFactTexts(10).size());
  }

  @Test
  void aDistantPairIsNeverPutToTheJudge() {
    insert(PREFERENCE, 1.0, 0.0);
    insert("The owner keeps bees on the roof", 0.0, 1.0);
    List<String> asked = new ArrayList<>();

    factStore.purgeNearDuplicates(MAX_DISTANCE, (kept, candidate) -> {
      asked.add(candidate);
      return true;
    });

    assertTrue(asked.isEmpty());
    assertEquals(2, factStore.listFactTexts(10).size());
  }

  @Test
  void withoutAJudgeTheDistanceAloneStillDecides() {
    insertClosePair();

    assertEquals(1, factStore.purgeNearDuplicates(MAX_DISTANCE));
  }
}
