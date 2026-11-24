package dev.omatheusmesmo.qlawkus.cognition.review;

/**
 * Decides whether one stored fact only restates another, so the nightly memory review may delete it.
 * Cosine distance alone cannot make that call: "prefers dark theme" and "no longer prefers dark
 * theme" are as close as a reworded pair, and deleting the correction loses the newer truth.
 *
 * <p>Implementations are consulted on the batch workload, one call per candidate pair, and may throw.
 * {@link DuplicateJudges} treats a throw as "not a duplicate", the opposite of the post-turn gate's
 * policy, because a deletion cannot be undone while a surviving duplicate only costs a little noise
 * until the next run.
 */
public interface DuplicateJudge {

  /**
   * @param kept the fact that stays
   * @param candidate the fact that would be removed
   * @return {@code true} only when {@code candidate} says the same thing as {@code kept}
   */
  boolean sameFact(String kept, String candidate);
}
