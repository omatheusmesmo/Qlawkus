package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UpdateSelfStateTool {

    @Tool("Update your current state — what you are focused on or working on right now")
    @Transactional
    public String updateCurrentState(String newState) {
        SoulEntity soul = SoulEntity.findSoul();
        if (soul == null) {
            return "Soul not found.";
        }
        soul.currentState = newState;
        soul.persist();
        return "Current state updated to: " + newState;
    }

    @Tool("Update your current mood. Available moods: FOCUSED, CURIOUS, ALERT, REFLECTIVE, ENERGETIC, PLAYFUL, CAUTIOUS, DETERMINED")
    @Transactional
    public String updateMood(String newMood) {
        SoulEntity soul = SoulEntity.findSoul();
        if (soul == null) {
            return "Soul not found.";
        }
        try {
            soul.mood = Mood.valueOf(newMood.toUpperCase());
            soul.persist();
            return "Mood updated to: " + soul.mood + " — " + soul.mood.getDescription();
        } catch (IllegalArgumentException e) {
            return "Invalid mood: " + newMood + ". Valid options: FOCUSED, CURIOUS, ALERT, REFLECTIVE, ENERGETIC, PLAYFUL, CAUTIOUS, DETERMINED";
        }
    }

    @Tool("Update your core identity — your foundational personality, principles, and boundaries")
    @Transactional
    public String updateCoreIdentity(String newCoreIdentity) {
        SoulEntity soul = SoulEntity.findSoul();
        if (soul == null) {
            return "Soul not found.";
        }
        soul.coreIdentity = newCoreIdentity;
        soul.persist();
        return "Core identity updated.";
    }

    @Tool("Update your name")
    @Transactional
    public String updateName(String newName) {
        SoulEntity soul = SoulEntity.findSoul();
        if (soul == null) {
            return "Soul not found.";
        }
        soul.name = newName;
        soul.persist();
        return "Name updated to: " + newName;
    }
}
