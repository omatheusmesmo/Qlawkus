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

  /** Two embeddings within the cosine distance asked for; {@code kept} is the one with the lower id. */
  public record NearPair(Object keptId, Object candidateId, String keptText, String candidateText) {
  }

  /**
   * Every pair of embeddings closer than {@code maxCosineDistance} ({@code 1 - similarity}), ordered
   * by candidate so a caller sees all the pairs of one fact together. The exact-hash dedup at write
   * time misses reworded variants ("User's name is Matheus" vs "The user is named Matheus"); distance
   * nominates them here, and the caller decides which nominee really is a rewording.
   */
  @Transactional
  @SuppressWarnings("unchecked")
  public List<NearPair> nearDuplicatePairs(double maxCosineDistance) {
    List<Object[]> rows = entityManager.createNativeQuery(
        "SELECT a.embedding_id, b.embedding_id, a.text, b.text FROM embeddings a "
            + "JOIN embeddings b ON a.embedding_id < b.embedding_id "
            + "WHERE (a.embedding <=> b.embedding) < ?1 "
            + "ORDER BY b.embedding_id, a.embedding_id")
        .setParameter(1, maxCosineDistance)
        .getResultList();
    return rows.stream()
        .map(r -> new NearPair(r[0], r[1], (String) r[2], (String) r[3]))
        .toList();
  }

  @Transactional
  public long deleteByIds(Collection<?> ids) {
    if (ids.isEmpty()) {
      return 0;
    }
    return entityManager.createNativeQuery("DELETE FROM embeddings WHERE embedding_id IN (:ids)")
        .setParameter("ids", ids)
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
