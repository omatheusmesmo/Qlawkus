package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;

/**
 * Hybrid {@link ScheduledTaskStore}, active when {@code qlawkus.cognition.backend=hybrid}. Each task is
 * written to its file (the source of truth, editable and versionable) and mirrored into the
 * {@code scheduled_task} table; reads come from the files. Selected at build time via
 * {@link IfBuildProperty}.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "hybrid")
public class HybridScheduledTaskStore implements ScheduledTaskStore {

  private final MarkdownScheduledTaskStore files;
  private final ScheduledTaskRepository tasks;
  private final PgWriteProbe writeProbe;

  @Inject
  public HybridScheduledTaskStore(AgentConfig config, ScheduledTaskRepository tasks, PgWriteProbe writeProbe) {
    this(Path.of(config.state().root(), MarkdownScheduledTaskStore.DIRECTORY), tasks, writeProbe);
  }

  HybridScheduledTaskStore(Path directory, ScheduledTaskRepository tasks, PgWriteProbe writeProbe) {
    this.files = new MarkdownScheduledTaskStore(directory);
    this.tasks = tasks;
    this.writeProbe = writeProbe;
  }

  @Override
  public void save(ScheduledTask task) {
    files.save(task);
    tasks.save(task);
  }

  @Override
  public void delete(String id) {
    files.delete(id);
    tasks.delete(id);
  }

  @Override
  public List<ScheduledTask> loadAll() {
    return files.loadAll();
  }

  @Override
  public void verifyWritable() {
    files.verifyWritable();
    writeProbe.verify(PgWriteProbe.Table.SCHEDULED_TASK);
  }
}
