package dev.omatheusmesmo.qlawkus.store.pg;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * The near-duplicate purge shared by the pgvector and hybrid stores. The pairs are read in one
 * transaction, judged outside any transaction (a judge may call a model, and a database connection
 * must not wait on it), and the confirmed duplicates are deleted in a second one. A fact goes when
 * any earlier fact within the distance is confirmed to be the same thing, which is what the single
 * {@code DELETE} did before a judge existed.
 */
final class NearDuplicatePurge {

  private NearDuplicatePurge() {
  }

  static long run(EmbeddingRepository repository, double maxCosineDistance, BiPredicate<String, String> sameFact) {
    Set<Object> doomed = new LinkedHashSet<>();
    for (EmbeddingRepository.NearPair pair : repository.nearDuplicatePairs(maxCosineDistance)) {
      if (!doomed.contains(pair.candidateId()) && sameFact.test(pair.keptText(), pair.candidateText())) {
        doomed.add(pair.candidateId());
      }
    }
    return repository.deleteByIds(doomed);
  }
}
