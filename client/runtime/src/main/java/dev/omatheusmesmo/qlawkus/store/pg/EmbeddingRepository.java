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
