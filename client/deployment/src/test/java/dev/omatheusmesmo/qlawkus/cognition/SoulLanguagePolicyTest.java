package dev.omatheusmesmo.qlawkus.cognition;

import static org.junit.jupiter.api.Assertions.*;

import dev.omatheusmesmo.qlawkus.agent.AgentReplyStyle;
import dev.omatheusmesmo.qlawkus.agent.AgentTurn;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SoulLanguagePolicyTest {
    @Inject
    SoulEngine soul;

    @Test
    void ordinaryTurnsFollowCurrentOwnerLanguageWithoutSpeechFormatting() {
        String prompt = soul.getSystemMessage("language-policy-text").orElseThrow();
        assertTrue(prompt.contains("Answer in the language of the owner's current request"));
        assertFalse(prompt.contains("## Spoken conversation"));
    }

    @Test
    void speechUsesTheResolvedLanguageWithoutTheAutomaticPolicy() {
        AgentTurn turn = AgentTurn.open("language-policy-speech", "rest", true, AgentReplyStyle.speech("pt"));
        try (var ignored = turn.bind()) {
            String prompt = soul.getSystemMessage("language-policy-speech").orElseThrow();
            assertFalse(prompt.contains("Answer in the language of the owner's current request"));
            assertTrue(prompt.contains("Write the entire reply in Portuguese (pt)"));
            assertTrue(prompt.indexOf("## Required language for this reply") > prompt.indexOf("## Spoken conversation"));
        } finally { turn.close("ok"); }
    }
}
