package dev.omatheusmesmo.qlawkus.store;

import dev.omatheusmesmo.qlawkus.cognition.Soul;

/**
 * Persistence for the singleton {@link Soul} (the agent's persona). Backend-pluggable on
 * {@code qlawkus.cognition.backend}: pgvector keeps it in the {@code soul} table, markdown in a
 * single {@code soul.md} file. {@link #load} returns {@code null} when no persona exists yet.
 */
public interface SoulStore {

  Soul load();

  void save(Soul soul);

  /**
   * Proves this store could persist a change right now, without changing anything, because readiness
   * calls it on every poll. Throws when a write would fail: a read-only volume, a root owned by
   * another uid, a read-only database session, or a missing table privilege.
   */
  void verifyWritable();
}
