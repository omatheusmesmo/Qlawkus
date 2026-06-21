package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.cognition.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA mapping for the singleton {@link UserProfile} (id = 1), seeded by Flyway
 * {@code V5__create_user_profile.sql}. Bridges the {@code user_profile} table to the
 * persistence-free domain object.
 */
@Entity
@Table(name = "user_profile")
@Cacheable
public class UserProfileEntity extends PanacheEntityBase {

  @Id
  public Long id;

  public String name;

  @Column(columnDefinition = "TEXT")
  public String profile;

  public Instant createdAt;

  public Instant updatedAt;

  public static UserProfileEntity findProfile() {
    return findById(1L);
  }

  public UserProfile toDomain() {
    UserProfile p = new UserProfile();
    p.id = id;
    p.name = name;
    p.profile = profile;
    p.createdAt = createdAt;
    p.updatedAt = updatedAt;
    return p;
  }

  public void copyFrom(UserProfile p) {
    this.name = p.name;
    this.profile = p.profile;
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
