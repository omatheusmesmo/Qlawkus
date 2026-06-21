package dev.omatheusmesmo.qlawkus.cognition;

import java.time.Instant;

/**
 * The owner of this agent. Qlawkus is a single-user agent: every conversation across every channel
 * is the same person. This profile is the durable, structured record of who that person is and is
 * injected into the system prompt on every turn, so the agent always has owner context without
 * depending on a memory search. A plain domain object (no persistence), loaded and saved through
 * {@link dev.omatheusmesmo.qlawkus.store.UserProfileStore} so the backend is pluggable.
 */
public class UserProfile {

  public Long id;
  public String name;
  public String profile;
  public Instant createdAt;
  public Instant updatedAt;

  public void rename(String newName) {
    this.name = newName;
  }

  public void rewriteProfile(String newProfile) {
    this.profile = newProfile;
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
      sb.append("You don't have a profile for them yet. As you learn durable facts about them, call ")
          .append("updateUserProfile to record them. A good profile covers:\n")
          .append("- Preferences: communication style, language, timezone\n")
          .append("- Personal info: name, location, occupation/role\n")
          .append("- Work: stack, tools, conventions, ongoing projects\n")
          .append("- Goals & interests: what they want from you\n\n");
    }

    sb.append("Before telling the owner you don't know or don't remember something about them, call ")
        .append("searchMemories first. When you learn a durable fact, record it: updateUserProfile ")
        .append("for profile-level facts, rememberFact for one-off facts. Write facts as declarative ")
        .append("statements (\"Owner prefers constructor injection\"), not instructions to yourself ")
        .append("(\"Always use constructor injection\"). Only record facts that will still matter ")
        .append("later — not ephemeral details like task progress, IDs, or what you fixed today. ")
        .append("Prefer facts that stop the owner from having to tell you the same thing again.");

    return sb.toString();
  }
}
