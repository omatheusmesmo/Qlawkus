package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Lets the agent maintain its owner's profile ({@link UserProfile}), which is injected into the
 * system prompt every turn. Mirrors {@link UpdateSelfStateTool} (which maintains the agent's own
 * {@link Soul}); this one maintains what the agent knows about the person it serves.
 */
@ApplicationScoped
public class UpdateUserProfileTool {

  @Inject
  UserProfileStore userProfileStore;

  @Tool("""
      Record or update what you know about your owner (the single person you serve). Use this for \
      durable, profile-level facts: their name, role, location, stack, preferences, ongoing \
      projects. This profile is injected into every conversation, so keep it compact and current. \
      It replaces the existing profile, so include everything that should still be remembered.""")
  public String updateUserProfile(
      @P("The owner's profile as a compact set of durable facts in markdown") String profile) {
    UserProfile userProfile = userProfileStore.load();
    if (userProfile == null) {
      return "User profile not found.";
    }
    if (profile == null || profile.isBlank()) {
      return "Cannot set an empty profile.";
    }
    userProfile.rewriteProfile(profile);
    userProfileStore.save(userProfile);
    return "Owner profile updated.";
  }

  @Tool("Set or update your owner's name (the person you serve).")
  public String updateOwnerName(@P("The owner's name") String name) {
    UserProfile userProfile = userProfileStore.load();
    if (userProfile == null) {
      return "User profile not found.";
    }
    if (name == null || name.isBlank()) {
      return "Cannot set an empty name.";
    }
    userProfile.rename(name);
    userProfileStore.save(userProfile);
    return "Owner name set to: " + name;
  }
}
