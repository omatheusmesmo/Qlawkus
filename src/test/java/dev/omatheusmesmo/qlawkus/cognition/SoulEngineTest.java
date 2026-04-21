package dev.omatheusmesmo.qlawkus.cognition;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SoulEngineTest {

    @Inject
    SoulEngine soulEngine;

    @Test
    @Transactional
    void getSystemMessage_returnsPresentWithSeededSoul() {
        Optional<String> message = soulEngine.getSystemMessage(null);
        assertTrue(message.isPresent());
    }

    @Test
    @Transactional
    void getSystemMessage_containsNameAsMarkdownHeader() {
        Optional<String> message = soulEngine.getSystemMessage(null);
        assertTrue(message.isPresent());
        assertTrue(message.get().startsWith("# Qlawkus"));
    }

    @Test
    @Transactional
    void getSystemMessage_containsCoreIdentity() {
        Optional<String> message = soulEngine.getSystemMessage(null);
        assertTrue(message.isPresent());
        assertTrue(message.get().contains("Who I Am"));
        assertTrue(message.get().contains("How I Work"));
        assertTrue(message.get().contains("Boundaries"));
    }

    @Test
    @Transactional
    void getSystemMessage_containsCurrentStateSection() {
        Optional<String> message = soulEngine.getSystemMessage(null);
        assertTrue(message.isPresent());
        assertTrue(message.get().contains("## Current State"));
    }

    @Test
    @Transactional
    void getSystemMessage_containsMoodSection() {
        SoulEntity soul = SoulEntity.findSoul();
        Mood currentMood = soul.mood;

        Optional<String> message = soulEngine.getSystemMessage(null);
        assertTrue(message.isPresent());
        assertTrue(message.get().contains("## Current Mood"));
        assertTrue(message.get().contains(currentMood.getDescription()));
    }

    @Test
    @Transactional
    void getSystemMessage_reflectsMoodChange() {
        SoulEntity soul = SoulEntity.findSoul();
        soul.mood = Mood.CURIOUS;
        soul.persist();

        Optional<String> message = soulEngine.getSystemMessage(null);
        assertTrue(message.isPresent());
        assertTrue(message.get().contains(Mood.CURIOUS.getDescription()));
    }

    @Test
    void toSystemMessage_composesAllFieldsAsMarkdown() {
        SoulEntity soul = new SoulEntity();
        soul.name = "TestAgent";
        soul.coreIdentity = "I am a test agent.";
        soul.currentState = "Running tests.";
        soul.mood = Mood.PLAYFUL;

        String message = soul.toSystemMessage();

        assertTrue(message.startsWith("# TestAgent"));
        assertTrue(message.contains("I am a test agent."));
        assertTrue(message.contains("## Current State"));
        assertTrue(message.contains("Running tests."));
        assertTrue(message.contains("## Current Mood"));
        assertTrue(message.contains("**PLAYFUL**"));
        assertTrue(message.contains(Mood.PLAYFUL.getDescription()));
        assertTrue(message.contains("Adjust your approach"));
    }

    @Test
    @Transactional
    void getSystemMessage_reflectsCurrentStateUpdate() {
        String newState = "Analyzing code repository at " + System.currentTimeMillis();
        SoulEntity soul = SoulEntity.findSoul();
        soul.currentState = newState;
        soul.persist();

        Optional<String> message = soulEngine.getSystemMessage(null);
        assertTrue(message.isPresent());
        assertTrue(message.get().contains(newState));
    }
}
