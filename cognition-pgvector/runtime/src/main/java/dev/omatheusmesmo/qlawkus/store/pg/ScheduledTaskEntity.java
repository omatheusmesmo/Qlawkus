package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import dev.omatheusmesmo.qlawkus.task.TaskStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "scheduled_task")
public class ScheduledTaskEntity extends PanacheEntityBase {

  @Id
  @Column(length = 64)
  public String id;

  @Column(nullable = false)
  public String memoryId;

  @Column(nullable = false)
  public String providerId;

  @Column(nullable = false)
  public String chatId;

  @Column(columnDefinition = "TEXT", nullable = false)
  public String task;

  @Column(nullable = false)
  public String cron;

  @Column(nullable = false)
  public boolean oneShot;

  @Column(nullable = false, length = 16)
  public String status;

  @Column(nullable = false)
  public Instant createdAt;

  void copyFrom(ScheduledTask scheduled) {
    memoryId = scheduled.memoryId();
    providerId = scheduled.destination().providerId();
    chatId = scheduled.destination().chatId();
    task = scheduled.task();
    cron = scheduled.cron();
    oneShot = scheduled.oneShot();
    status = scheduled.status().name();
    createdAt = scheduled.createdAt();
  }

  ScheduledTask toTask() {
    return new ScheduledTask(id, memoryId, new TaskDestination(providerId, chatId), task, cron, oneShot,
        createdAt, TaskStatus.valueOf(status));
  }
}
