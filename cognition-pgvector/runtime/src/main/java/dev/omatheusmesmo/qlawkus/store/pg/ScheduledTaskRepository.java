package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Panache access to the {@code scheduled_task} table, shared by the pg and hybrid task stores so both
 * write the same rows through one transactional seam.
 */
@ApplicationScoped
public class ScheduledTaskRepository {

  @Transactional
  public void save(ScheduledTask scheduled) {
    ScheduledTaskEntity entity = ScheduledTaskEntity.findById(scheduled.id());
    if (entity == null) {
      entity = new ScheduledTaskEntity();
      entity.id = scheduled.id();
      entity.copyFrom(scheduled);
      entity.persist();
    } else {
      entity.copyFrom(scheduled);
    }
  }

  @Transactional
  public void delete(String id) {
    ScheduledTaskEntity.deleteById(id);
  }

  @Transactional
  public List<ScheduledTask> listAll() {
    return ScheduledTaskEntity.<ScheduledTaskEntity>list("order by createdAt").stream()
        .map(ScheduledTaskEntity::toTask)
        .toList();
  }

  @Transactional
  public void deleteAll() {
    ScheduledTaskEntity.deleteAll();
  }
}
