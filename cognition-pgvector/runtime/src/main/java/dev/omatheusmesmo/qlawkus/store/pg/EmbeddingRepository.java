package dev.omatheusmesmo.qlawkus.store.pg;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

  /**
   * Removes semantically near-duplicate embeddings, keeping one per cluster. The exact-hash dedup at
   * write time misses reworded variants ("User's name is Matheus" vs "The user is named Matheus");
   * this catches them by cosine distance. {@code maxCosineDistance} is {@code 1 - similarity}.
   */
  @Transactional
  public long deleteNearDuplicates(double maxCosineDistance) {
    return entityManager.createNativeQuery(
        "DELETE FROM embeddings WHERE embedding_id IN ("
            + "SELECT b.embedding_id FROM embeddings a "
            + "JOIN embeddings b ON a.embedding_id < b.embedding_id "
            + "WHERE (a.embedding <=> b.embedding) < ?1)")
        .setParameter(1, maxCosineDistance)
        .executeUpdate();
  }

  /** Texts of curated user facts (remember-tool + semantic-extractor), for profile curation. */
  @Transactional
  @SuppressWarnings("unchecked")
  public List<String> listFactTexts(int limit) {
    return entityManager.createNativeQuery(
        "SELECT text FROM embeddings "
            + "WHERE metadata->>'source' IN ('remember-tool','semantic-extractor') "
            + "ORDER BY embedding_id LIMIT ?1")
        .setParameter(1, limit)
        .getResultList();
  }

  /** A stored fact in raw form, for reconciling/migrating pgvector facts back to markdown files. */
  public record FactRow(String text, String metadataJson) {
  }

  /** Every stored fact (text + JSON metadata), used to mirror pgvector facts back to files. */
  @Transactional
  @SuppressWarnings("unchecked")
  public List<FactRow> loadAllFacts() {
    List<Object[]> rows = entityManager.createNativeQuery(
        "SELECT text, metadata::text FROM embeddings ORDER BY embedding_id")
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
