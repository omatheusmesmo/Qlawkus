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
    void speechAddsTheTurnLanguageAfterTheSharedLanguagePolicy() {
        AgentTurn turn = AgentTurn.open("language-policy-speech", "rest", true, AgentReplyStyle.speech("pt"));
        try (var ignored = turn.bind()) {
            String prompt = soul.getSystemMessage("language-policy-speech").orElseThrow();
            assertTrue(prompt.contains("Answer in the language of the owner's current request"));
            assertTrue(prompt.contains("Reply in language code pt"));
            assertTrue(prompt.indexOf("## Spoken conversation") > prompt.indexOf("## Reply language"));
        } finally { turn.close("ok"); }
    }
}
