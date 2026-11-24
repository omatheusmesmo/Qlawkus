package dev.omatheusmesmo.qlawkus.it.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.store.FactChunker;
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

  private static final String ORIGINAL = "User prefers dark theme in IDE. User keeps the editor font at 14 points. "
      + "User runs the test suite before every push.";
  private static final String REWORDED = "User prefers a dark IDE theme. User keeps the editor font at 14 points. "
      + "User runs the tests before each push.";
  private static final String ORIGINAL_FIRST = "User prefers dark theme in IDE. User keeps the editor font at 14 points.";
  private static final String ORIGINAL_SECOND = "User keeps the editor font at 14 points. User runs the test suite before every push.";
  private static final String REWORDED_FIRST = "User prefers a dark IDE theme. User keeps the editor font at 14 points.";
  private static final String REWORDED_SECOND = "User keeps the editor font at 14 points. User runs the tests before each push.";

  private static final UUID ORIGINAL_FIRST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID ORIGINAL_SECOND_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final UUID REWORDED_FIRST_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID REWORDED_SECOND_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");

  private static final UUID INTERLEAVED_ORIGINAL_FIRST = UUID.fromString("00000000-0000-0000-0000-000000000201");
  private static final UUID INTERLEAVED_REWORDED_FIRST = UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID INTERLEAVED_ORIGINAL_SECOND = UUID.fromString("00000000-0000-0000-0000-000000000203");
  private static final UUID INTERLEAVED_REWORDED_SECOND = UUID.fromString("00000000-0000-0000-0000-000000000204");

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

  private long rowCount() {
    return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager.createNativeQuery(
        "SELECT COUNT(*) FROM embeddings").getSingleResult()).longValue());
  }

  private void insert(String text, double x, double y) {
    insert(UUID.randomUUID(), text, x, y, "{\"source\":\"semantic-extractor\"}");
  }

  private void insert(UUID id, String text, double x, double y, String metadata) {
    int dimension = dimension();
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < dimension; i++) {
      parts.add(i == 0 ? Double.toString(x) : i == 1 ? Double.toString(y) : "0");
    }
    String vector = "[" + String.join(",", parts) + "]";
    QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
        "INSERT INTO embeddings (embedding_id, embedding, text, metadata) "
            + "VALUES (?1, CAST(?2 AS vector), ?3, CAST(?4 AS jsonb))")
        .setParameter(1, id)
        .setParameter(2, vector)
        .setParameter(3, text)
        .setParameter(4, metadata)
        .executeUpdate());
  }

  private static String segmentMetadata(String fact, int index, int count) {
    String factText = index == 0 ? ",\"factText\":\"" + fact + "\"" : "";
    return "{\"source\":\"semantic-extractor\",\"factHash\":\"" + FactChunker.factHash(fact)
        + "\",\"chunkIndex\":\"" + index + "\",\"chunkCount\":\"" + count + "\"" + factText + "}";
  }

  private void insertClosePair() {
    insert(PREFERENCE, 1.0, 0.0);
    insert(CORRECTION, 0.971, 0.239);
  }

  private void insertOriginalFact() {
    insert(ORIGINAL_FIRST_ID, ORIGINAL_FIRST, 1.0, 0.0, segmentMetadata(ORIGINAL, 0, 2));
    insert(ORIGINAL_SECOND_ID, ORIGINAL_SECOND, 0.995, 0.0998, segmentMetadata(ORIGINAL, 1, 2));
  }

  private void insertRewordedFact() {
    insert(REWORDED_FIRST_ID, REWORDED_FIRST, 0.99, 0.14, segmentMetadata(REWORDED, 0, 2));
    insert(REWORDED_SECOND_ID, REWORDED_SECOND, 0.98, 0.199, segmentMetadata(REWORDED, 1, 2));
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

  @Test
  void theSegmentsOfOneChunkedFactAreNeverPairedWithEachOther() {
    insertOriginalFact();
    List<List<String>> asked = new ArrayList<>();

    long removed = factStore.purgeNearDuplicates(MAX_DISTANCE, (kept, candidate) -> {
      asked.add(List.of(kept, candidate));
      return true;
    });

    assertEquals(0, removed);
    assertEquals(2, rowCount());
    assertTrue(asked.isEmpty());
  }

  @Test
  void aRewordedCopyOfAChunkedFactIsRemovedWithAllItsSegments() {
    insertOriginalFact();
    insertRewordedFact();
    List<List<String>> asked = new ArrayList<>();

    long removed = factStore.purgeNearDuplicates(MAX_DISTANCE, (kept, candidate) -> {
      asked.add(List.of(kept, candidate));
      return true;
    });

    assertEquals(2, removed);
    assertEquals(2, rowCount());
    assertEquals(List.of(List.of(ORIGINAL, REWORDED)), asked);
    assertEquals(List.of(ORIGINAL), factStore.listFactTexts(10));
  }

  @Test
  void twoFactsWhoseSegmentsInterleaveAreNeverBothRemoved() {
    insert(INTERLEAVED_ORIGINAL_FIRST, ORIGINAL_FIRST, 1.0, 0.0, segmentMetadata(ORIGINAL, 0, 2));
    insert(INTERLEAVED_REWORDED_FIRST, REWORDED_FIRST, 0.99, 0.14, segmentMetadata(REWORDED, 0, 2));
    insert(INTERLEAVED_ORIGINAL_SECOND, ORIGINAL_SECOND, 0.995, 0.0998, segmentMetadata(ORIGINAL, 1, 2));
    insert(INTERLEAVED_REWORDED_SECOND, REWORDED_SECOND, 0.98, 0.199, segmentMetadata(REWORDED, 1, 2));
    List<List<String>> asked = new ArrayList<>();

    long removed = factStore.purgeNearDuplicates(MAX_DISTANCE, (kept, candidate) -> {
      asked.add(List.of(kept, candidate));
      return true;
    });

    assertEquals(2, removed);
    assertEquals(2, rowCount());
    assertEquals(1, asked.size());
    assertEquals(1, factStore.listFactTexts(10).size());
  }
}
