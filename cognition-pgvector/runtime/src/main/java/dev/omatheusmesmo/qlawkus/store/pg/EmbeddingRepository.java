package dev.omatheusmesmo.qlawkus.store.pg;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

@ApplicationScoped
public class EmbeddingRepository {

  private static final String FACT_KEY = "COALESCE(metadata->>'factHash', CAST(embedding_id AS text))";

  @Inject
  EntityManager entityManager;

  @Transactional
  public List<String> listSources() {
    return entityManager.createNativeQuery(
        "SELECT DISTINCT metadata->>'source' FROM embeddings WHERE metadata IS NOT NULL")
        .getResultList();
  }

  @Transactional
  public long countBySource(String source) {
    Object result = entityManager.createNativeQuery(
        "SELECT COUNT(*) FROM embeddings WHERE metadata->>'source' = ?1")
        .setParameter(1, source)
        .getSingleResult();
    return result != null ? ((Number) result).longValue() : 0;
  }

  @Transactional
  public long deleteBySource(String source) {
    return entityManager.createNativeQuery(
        "DELETE FROM embeddings WHERE metadata->>'source' = ?1")
        .setParameter(1, source)
        .executeUpdate();
  }

  @Transactional
  public long deleteAll() {
    return entityManager.createNativeQuery("DELETE FROM embeddings")
        .executeUpdate();
  }

  /**
   * Two facts with a segment pair within the cosine distance asked for. A fact is the rows sharing a
   * {@code factHash} (a row without one is a fact of its own); {@code keptText} is the whole fact of
   * the fact holding the lower row id, and {@code candidateText} the whole fact of the other.
   */
  public record NearPair(String keptFact, String candidateFact, String keptText, String candidateText) {
  }

  /**
   * Every pair of facts with a segment closer than {@code maxCosineDistance} ({@code 1 - similarity}),
   * ordered by candidate so a caller sees all the pairs of one fact together. Segments of the same
   * fact are never paired: overlapping segments are close by construction. The exact-hash dedup at
   * write time misses reworded variants ("User's name is Matheus" vs "The user is named Matheus");
   * distance nominates them here, and the caller decides which nominee really is a rewording.
   */
  @Transactional
  @SuppressWarnings("unchecked")
  public List<NearPair> nearDuplicatePairs(double maxCosineDistance) {
    List<Object[]> rows = entityManager.createNativeQuery(
        "WITH segments AS (SELECT embedding_id, embedding, text, metadata, " + FACT_KEY + " AS fact_key "
            + "FROM embeddings), "
            + "facts AS (SELECT DISTINCT ON (fact_key) fact_key, COALESCE(metadata->>'factText', text) AS fact_text "
            + "FROM segments ORDER BY fact_key, COALESCE(metadata->>'chunkIndex', '0')) "
            + "SELECT DISTINCT a.fact_key, b.fact_key, fa.fact_text, fb.fact_text FROM segments a "
            + "JOIN segments b ON a.embedding_id < b.embedding_id "
            + "JOIN facts fa ON fa.fact_key = a.fact_key "
            + "JOIN facts fb ON fb.fact_key = b.fact_key "
            + "WHERE a.fact_key <> b.fact_key AND (a.embedding <=> b.embedding) < ?1 "
            + "ORDER BY b.fact_key, a.fact_key")
        .setParameter(1, maxCosineDistance)
        .getResultList();
    return rows.stream()
        .map(r -> new NearPair((String) r[0], (String) r[1], (String) r[2], (String) r[3]))
        .toList();
  }

  /** Deletes every row of the given facts, so a fact never survives as a fragment of its segments. */
  @Transactional
  public long deleteFacts(Collection<String> factKeys) {
    if (factKeys.isEmpty()) {
      return 0;
    }
    return entityManager.createNativeQuery("DELETE FROM embeddings WHERE " + FACT_KEY + " IN (:keys)")
        .setParameter("keys", factKeys)
        .executeUpdate();
  }

  /**
   * Texts of curated user facts (remember-tool + semantic-extractor), for profile curation. Facts
   * are stored as N segments; this returns the whole fact once (segment 0 carries the full
   * {@code factText}), never a fragment and never N duplicates.
   */
  @Transactional
  @SuppressWarnings("unchecked")
  public List<String> listFactTexts(int limit) {
    return entityManager.createNativeQuery(
        "SELECT COALESCE(metadata->>'factText', text) FROM embeddings "
            + "WHERE metadata->>'source' IN ('remember-tool','semantic-extractor') "
            + "AND COALESCE(metadata->>'chunkIndex', '0') = '0' "
            + "ORDER BY embedding_id LIMIT ?1")
        .setParameter(1, limit)
        .getResultList();
  }

  /** A stored fact in raw form, for reconciling/migrating pgvector facts back to markdown files. */
  public record FactRow(String text, String metadataJson) {
  }

  /**
   * Every stored fact (whole text + JSON metadata), one row per fact, used to mirror pgvector facts
   * back to files. Only segment 0 of each fact is returned, and its full {@code factText} is
   * reconstructed, so a chunked fact yields exactly one file (not one per segment).
   */
  @Transactional
  @SuppressWarnings("unchecked")
  public List<FactRow> loadAllFacts() {
    List<Object[]> rows = entityManager.createNativeQuery(
        "SELECT COALESCE(metadata->>'factText', text), metadata::text FROM embeddings "
            + "WHERE COALESCE(metadata->>'chunkIndex', '0') = '0' ORDER BY embedding_id")
        .getResultList();
    return rows.stream()
        .map(r -> new FactRow((String) r[0], (String) r[1]))
        .toList();
  }

  @Transactional
  public boolean existsByContentHash(String hash) {
    Object result = entityManager.createNativeQuery(
        "SELECT EXISTS(SELECT 1 FROM embeddings WHERE content_hash = ?1)")
        .setParameter(1, hash)
        .getSingleResult();
    return switch (result) {
      case Boolean b -> b;
      case Number n -> n.intValue() != 0;
      default -> false;
    };
  }

  public static String md5(String text) {
    try {
      var digest = MessageDigest.getInstance("MD5");
      var bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception e) {
      throw new RuntimeException("MD5 algorithm not available", e);
    }
  }
}
