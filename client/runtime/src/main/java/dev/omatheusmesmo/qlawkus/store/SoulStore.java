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
}
