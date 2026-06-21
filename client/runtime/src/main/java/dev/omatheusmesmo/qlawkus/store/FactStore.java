package dev.omatheusmesmo.qlawkus.store;

import java.util.List;
import java.util.Map;

public interface FactStore {

  /**
   * Stores the content as an embedding. Idempotent on identical content (deduplicated by hash).
   * Propagates a {@link RuntimeException} when the embedding or persistence fails so callers can
   * report the failure instead of assuming success; best-effort callers should catch it.
   */
  void store(String content, Map<String, Object> metadata);

  List<String> search(String query, int maxResults, double minScore);

  /** Like {@link #search} but restricted to embeddings whose {@code source} metadata matches. */
  List<String> searchBySource(String query, String source, int maxResults, double minScore);

  List<String> listSources();

  /**
   * Texts of curated user facts ({@code remember-tool} + {@code semantic-extractor}), newest-leaning,
   * capped at {@code limit}. Used by profile curation. Excludes transcripts and journals.
   */
  List<String> listFactTexts(int limit);

  /**
   * Removes semantically near-duplicate facts, keeping one per cluster. The write-time hash dedup
   * only catches byte-identical facts; this catches reworded variants by cosine distance
   * ({@code maxCosineDistance = 1 - similarity}). Returns how many were removed.
   */
  long purgeNearDuplicates(double maxCosineDistance);

  long purgeBySource(String source);

  long purgeAll();
}
