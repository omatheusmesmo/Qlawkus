package dev.omatheusmesmo.qlawkus.store.pg;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A skill persisted in Postgres, used by the pgvector and hybrid backends. The skill name is the
 * primary key. In hybrid mode the SKILL.md files remain the source of truth and this table is a
 * mirror; in pgvector mode this table is the source of truth.
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
    }
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
