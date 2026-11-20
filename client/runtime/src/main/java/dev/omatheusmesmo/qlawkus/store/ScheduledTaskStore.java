package dev.omatheusmesmo.qlawkus.store;

import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import java.util.List;

/**
 * The record of agent-created tasks, kept so a restart, redeploy or crash can register them again.
 * Each task is one record keyed by its id; saving an id that exists replaces it.
 */
public interface ScheduledTaskStore {

  void save(ScheduledTask task);

  void delete(String id);

  /**
   * Every readable record, bounded by the store. A damaged record is skipped rather than thrown, so
   * one bad file never keeps the others from being restored.
   */
  List<ScheduledTask> loadAll();

  /**
   * Proves a save could succeed right now without saving anything, because readiness calls it on
   * every poll. Throws when a write would fail.
   */
  void verifyWritable();
}
