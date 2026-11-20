package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Postgres-backed {@link ScheduledTaskStore}, active when {@code qlawkus.cognition.backend=pgvector} (the
 * default). Tasks are rows in the {@code scheduled_task} table. Selected at build time via
 * {@link IfBuildProperty}, so other backends do not wire it at all.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "pgvector", enableIfMissing = true)
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
