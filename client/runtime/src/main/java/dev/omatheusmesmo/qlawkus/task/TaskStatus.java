package dev.omatheusmesmo.qlawkus.task;

/**
 * Where a stored task stands. {@code MISSED} is a one-time task whose time passed while the agent was
 * down, so it was never run; {@code INVALID} is a schedule the scheduler cannot run. Both stay stored
 * and listed until the owner cancels them, and neither is registered again.
 */
public enum TaskStatus {
  ACTIVE,
  MISSED,
  INVALID
}
