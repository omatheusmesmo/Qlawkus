package dev.omatheusmesmo.qlawkus.store;

import dev.omatheusmesmo.qlawkus.cognition.UserProfile;

/**
 * Persistence for the singleton {@link UserProfile} (the agent's owner). Backend-pluggable on
 * {@code qlawkus.cognition.backend}: pgvector keeps it in the {@code user_profile} table, markdown
 * in a single {@code owner.md} file. {@link #load} returns {@code null} when no profile exists yet.
 */
public interface UserProfileStore {

  UserProfile load();

  void save(UserProfile profile);
}
