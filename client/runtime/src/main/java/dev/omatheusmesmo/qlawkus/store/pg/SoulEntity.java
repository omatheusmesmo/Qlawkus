package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.cognition.Mood;
import dev.omatheusmesmo.qlawkus.cognition.Soul;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA mapping for the singleton {@link Soul} (id = 1), seeded by Flyway {@code V1__create_soul.sql}.
 * Bridges the {@code soul} table to the persistence-free domain object, mirroring how
 * {@code ChatMessageEntity} bridges {@code ChatMessage}.
 */
@Entity
@Table(name = "soul")
@Cacheable
public class SoulEntity extends PanacheEntityBase {

  @Id
  public Long id;

  public String name;

  @Column(columnDefinition = "TEXT")
  public String coreIdentity;

  @Column(columnDefinition = "TEXT")
  public String currentState;

  @Enumerated(EnumType.STRING)
  public Mood mood;

  public Instant createdAt;

  public Instant updatedAt;

  public static SoulEntity findSoul() {
    return findById(1L);
  }

  public Soul toDomain() {
    Soul soul = new Soul();
    soul.id = id;
    soul.name = name;
    soul.coreIdentity = coreIdentity;
    soul.currentState = currentState;
    soul.mood = mood;
    soul.createdAt = createdAt;
    soul.updatedAt = updatedAt;
    return soul;
  }

  public void copyFrom(Soul soul) {
    this.name = soul.name;
    this.coreIdentity = soul.coreIdentity;
    this.currentState = soul.currentState;
    this.mood = soul.mood;
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
