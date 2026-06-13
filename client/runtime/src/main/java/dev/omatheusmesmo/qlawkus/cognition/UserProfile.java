package dev.omatheusmesmo.qlawkus.cognition;

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
 * The owner of this agent. Qlawkus is a single-user agent: every conversation across every channel
 * is the same person. This profile is the durable, structured record of who that person is and is
 * injected into the system prompt on every turn, so the agent always has owner context without
 * depending on a memory search. Modeled as a singleton (id = 1), like {@link Soul}.
 */
@Entity
@Table(name = "user_profile")
@Cacheable
public class UserProfile extends PanacheEntityBase {

  @Id
  public Long id;

  public String name;

  @Column(columnDefinition = "TEXT")
  public String profile;

  public Instant createdAt;

  public Instant updatedAt;

  public static UserProfile findProfile() {
    return findById(1L);
  }

  public void rename(String newName) {
    this.name = newName;
  }

  public void rewriteProfile(String newProfile) {
    this.profile = newProfile;
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

  /** Renders the owner section injected into the system prompt on every turn. */
  public String toContextBlock() {
    StringBuilder sb = new StringBuilder();
    sb.append("## Your Owner\n\n");
    sb.append("You are the dedicated personal agent of a single owner. Every conversation — across ")
        .append("Discord, Telegram, and the REST API — is the same person. Treat that continuity as ")
        .append("real: what they told you before still holds now, on any channel.\n\n");

    if (name != null && !name.isBlank()) {
      sb.append("Your owner's name is **").append(name).append("**.\n\n");
    }

    if (profile != null && !profile.isBlank()) {
      sb.append("What you know about them (this is your durable profile of the owner):\n\n")
          .append(profile).append("\n\n");
    } else {
      sb.append("You don't have a profile for them yet. As you learn durable facts about them — ")
          .append("their name, role, location, stack, preferences, ongoing projects — call ")
          .append("updateUserProfile to record them here so you never lose that context.\n\n");
    }

    sb.append("Before telling the owner you don't know or don't remember something about them, call ")
        .append("searchMemories first. When you learn a durable fact, record it: updateUserProfile ")
        .append("for profile-level facts, rememberFact for one-off facts. Write facts as declarative ")
        .append("statements (\"Owner prefers constructor injection\"), not instructions to yourself.");

    return sb.toString();
  }
}
