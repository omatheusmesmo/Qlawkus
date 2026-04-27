package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UpdateSelfStateTool {

    @Tool("Update your current state — what you are focused on or working on right now")
    @Transactional
    public String updateCurrentState(String newState) {
        Soul soul = Soul.findSoul();
        if (soul == null) {
            return "Soul not found.";
        }
        soul.shiftState(newState);
        return "Current state updated to: " + newState;
    }

    @Tool("Update your current mood. Available moods: FOCUSED, CURIOUS, ALERT, REFLECTIVE, ENERGETIC, PLAYFUL, CAUTIOUS, DETERMINED")
    @Transactional
    public String updateMood(String newMood) {
        Soul soul = Soul.findSoul();
        if (soul == null) {
            return "Soul not found.";
        }
        try {
            soul.shiftMood(Mood.valueOf(newMood.toUpperCase()));
            return "Mood updated to: " + soul.mood + " — " + soul.mood.getDescription();
        } catch (IllegalArgumentException e) {
            return "Invalid mood: " + newMood + ". Valid options: FOCUSED, CURIOUS, ALERT, REFLECTIVE, ENERGETIC, PLAYFUL, CAUTIOUS, DETERMINED";
        }
    }

    @Tool("Update your core identity — your foundational personality, principles, and boundaries")
    @Transactional
    public String updateCoreIdentity(String newCoreIdentity) {
        Soul soul = Soul.findSoul();
        if (soul == null) {
            return "Soul not found.";
        }
        soul.rewriteIdentity(newCoreIdentity);
        return "Core identity updated.";
    }

    @Tool("Update your name")
    @Transactional
    public String updateName(String newName) {
        Soul soul = Soul.findSoul();
        if (soul == null) {
            return "Soul not found.";
        }
        soul.rename(newName);
        return "Name updated to: " + newName;
    }
}
