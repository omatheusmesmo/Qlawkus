package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UpdateSelfStateTool {

    @Inject
    SoulStore soulStore;

    @Tool("Update your current state — what you are focused on or working on right now")
    public String updateCurrentState(String newState) {
        Soul soul = soulStore.load();
        if (soul == null) {
            return "Soul not found.";
        }
        soul.shiftState(newState);
        soulStore.save(soul);
        return "Current state updated to: " + newState;
    }

    @Tool("Update your current mood. Available moods: FOCUSED, CURIOUS, ALERT, REFLECTIVE, ENERGETIC, PLAYFUL, CAUTIOUS, DETERMINED")
    public String updateMood(String newMood) {
        Soul soul = soulStore.load();
        if (soul == null) {
            return "Soul not found.";
        }
        try {
            soul.shiftMood(Mood.valueOf(newMood.toUpperCase()));
            soulStore.save(soul);
            return "Mood updated to: " + soul.mood + " — " + soul.mood.getDescription();
        } catch (IllegalArgumentException e) {
            return "Invalid mood: " + newMood + ". Valid options: FOCUSED, CURIOUS, ALERT, REFLECTIVE, ENERGETIC, PLAYFUL, CAUTIOUS, DETERMINED";
        }
    }

    @Tool("Update your core identity — your foundational personality, principles, and boundaries")
    public String updateCoreIdentity(String newCoreIdentity) {
        Soul soul = soulStore.load();
        if (soul == null) {
            return "Soul not found.";
        }
        soul.rewriteIdentity(newCoreIdentity);
        soulStore.save(soul);
        return "Core identity updated.";
    }

    @Tool("Update your name")
    public String updateName(String newName) {
        Soul soul = soulStore.load();
        if (soul == null) {
            return "Soul not found.";
        }
        soul.rename(newName);
        soulStore.save(soul);
        return "Name updated to: " + newName;
    }
}
