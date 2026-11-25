package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Postgres-backed {@link ScheduledTaskStore}, active when {@code qlawkus.cognition.backend=pgvector}. Tasks are rows in the {@code scheduled_task} table. Chosen at boot by {@link CognitionStoreSelector}.
 */
@ApplicationScoped
@Typed(PgScheduledTaskStore.class)
public class PgScheduledTaskStore implements ScheduledTaskStore {

  private final ScheduledTaskRepository tasks;
  private final PgWriteProbe writeProbe;

  @Inject
  public PgScheduledTaskStore(ScheduledTaskRepository tasks, PgWriteProbe writeProbe) {
    this.tasks = tasks;
    this.writeProbe = writeProbe;
  }

  @Override
  public void save(ScheduledTask task) {
    tasks.save(task);
  }

  @Override
  public void delete(String id) {
    tasks.delete(id);
  }

  @Override
  public List<ScheduledTask> loadAll() {
    return tasks.listAll();
  }

  @Override
  public void verifyWritable() {
    writeProbe.verify(PgWriteProbe.Table.SCHEDULED_TASK);
  }
}
