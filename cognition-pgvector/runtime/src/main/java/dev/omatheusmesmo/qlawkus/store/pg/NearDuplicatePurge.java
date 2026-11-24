package dev.omatheusmesmo.qlawkus.store.pg;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * The near-duplicate purge shared by the pgvector and hybrid stores. The pairs are facts, not rows, and
 * are read in one transaction, judged outside any transaction (a judge may call a model, and a database
 * connection must not wait on it), and the confirmed duplicates are deleted in a second one, all of their
 * segments together. A fact goes when any earlier fact within the distance is confirmed to be the same
 * thing, which is what the single {@code DELETE} did before a judge existed. A pair whose kept fact is
 * already doomed is skipped, since the same two facts can be nominated in both orientations when their
 * segment ids interleave and neither may take the other down with it.
 */
final class NearDuplicatePurge {

  private NearDuplicatePurge() {
  }

  static long run(EmbeddingRepository repository, double maxCosineDistance, BiPredicate<String, String> sameFact) {
    return repository.deleteFacts(doomed(repository, maxCosineDistance, sameFact));
  }

  /** The fact keys the judge confirmed as duplicates, for a caller that has more to remove than table rows. */
  static Set<String> doomed(EmbeddingRepository repository, double maxCosineDistance,
      BiPredicate<String, String> sameFact) {
    Set<String> doomed = new LinkedHashSet<>();
    for (EmbeddingRepository.NearPair pair : repository.nearDuplicatePairs(maxCosineDistance)) {
      if (!doomed.contains(pair.keptFact()) && !doomed.contains(pair.candidateFact())
          && sameFact.test(pair.keptText(), pair.candidateText())) {
        doomed.add(pair.candidateFact());
      }
    }
    return doomed;
  }
}
