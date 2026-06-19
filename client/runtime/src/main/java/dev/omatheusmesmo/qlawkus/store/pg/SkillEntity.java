package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.skill.SkillState;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A skill persisted in Postgres, used by the pgvector and hybrid backends. The skill name is the
 * primary key. Lifecycle telemetry (last use, count, state, pinned) lives in columns here. The
 * static helpers are shared by both backends and must be called from a transactional context.
 */
@Entity
@Table(name = "skill")
public class SkillEntity extends PanacheEntityBase {

  @Id
  public String name;

  @Column(columnDefinition = "TEXT")
  public String description;

  @Column(columnDefinition = "TEXT")
  public String body;

  public Instant lastUsedAt;

  public long useCount;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  public SkillState state = SkillState.ACTIVE;

  public boolean pinned;

  public Instant createdAt;

  public Instant updatedAt;

  public static void upsert(String name, String description, String body) {
    SkillEntity entity = findById(name);
    if (entity == null) {
      entity = new SkillEntity();
      entity.name = name;
      entity.description = description;
      entity.body = body;
      entity.persist();
    } else {
      entity.description = description;
      entity.body = body;
      entity.state = SkillState.ACTIVE;
    }
  }

  public static void recordUse(String name) {
    SkillEntity entity = findById(name);
    if (entity != null) {
      entity.useCount++;
      entity.lastUsedAt = Instant.now();
      entity.state = SkillState.ACTIVE;
    }
  }

  public static boolean setPinned(String name, boolean pinned) {
    SkillEntity entity = findById(name);
    if (entity == null) {
      return false;
    }
    entity.pinned = pinned;
    return true;
  }

  public static int sweep(int staleAfterDays, int archiveAfterDays) {
    Instant now = Instant.now();
    long archived = update(
        "state = ?1 where pinned = false and state <> ?1 and coalesce(lastUsedAt, createdAt) < ?2",
        SkillState.ARCHIVED, now.minus(archiveAfterDays, ChronoUnit.DAYS));
    long staled = update(
        "state = ?1 where pinned = false and state = ?2 and coalesce(lastUsedAt, createdAt) < ?3",
        SkillState.STALE, SkillState.ACTIVE, now.minus(staleAfterDays, ChronoUnit.DAYS));
    return (int) (archived + staled);
  }

  public static Set<String> archivedNames() {
    return SkillEntity.<SkillEntity>list("state", SkillState.ARCHIVED).stream()
        .map(entity -> entity.name)
        .collect(Collectors.toSet());
  }

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
