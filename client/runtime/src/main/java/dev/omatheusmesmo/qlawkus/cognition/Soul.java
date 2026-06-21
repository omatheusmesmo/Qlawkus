package dev.omatheusmesmo.qlawkus.cognition;

import java.time.Instant;

/**
 * The agent's persona: identity, current state, and mood. A plain domain object (no persistence),
 * loaded and saved through {@link dev.omatheusmesmo.qlawkus.store.SoulStore} so the backend
 * (pgvector or markdown) is pluggable. Mutation methods return nothing and mutate in place; callers
 * persist by passing the instance back to {@code SoulStore.save}.
 */
public class Soul {

  public Long id;
  public String name;
  public String coreIdentity;
  public String currentState;
  public Mood mood;
  public Instant createdAt;
  public Instant updatedAt;

  public void rename(String newName) {
    this.name = newName;
  }

  public void shiftState(String newState) {
    this.currentState = newState;
  }

  public void shiftMood(Mood newMood) {
    this.mood = newMood;
  }

  public void rewriteIdentity(String newCoreIdentity) {
    this.coreIdentity = newCoreIdentity;
  }

  public String toSystemMessage() {
    StringBuilder sb = new StringBuilder();

    sb.append("# ").append(name).append("\n\n");
    sb.append(coreIdentity).append("\n\n");
    sb.append("---\n\n");
    sb.append("## Current State\n\n");
    sb.append(currentState).append("\n\n");
    sb.append("## Current Mood\n\n");
    sb.append("**").append(mood).append("** — ")
        .append(mood.getDescription()).append("\n\n");
    sb.append("*Adjust your approach based on your current mood ")
        .append("while staying true to your core identity.*");

    return sb.toString();
  }
}
