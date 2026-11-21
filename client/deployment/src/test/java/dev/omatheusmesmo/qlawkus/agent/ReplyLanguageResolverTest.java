package dev.omatheusmesmo.qlawkus.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ReplyLanguageResolverTest {
    @Test
    void explicitLanguageBypassesClassification() {
        AtomicInteger calls = new AtomicInteger();
        var resolver = new ReplyLanguageResolver((message, hint, stable, choices, fallback) -> {
            calls.incrementAndGet(); return "en";
        });
        assertEquals("pt", resolver.resolve("owner", "hello", AgentReplyStyle.speech("pt")).language());
        assertEquals(0, calls.get());
    }

    @Test
    void textAndSpeechUseTheSameUnrestrictedDecisionPolicy() {
        var resolver = new ReplyLanguageResolver((message, hint, stable, choices, fallback) -> {
            assertTrue(choices.isEmpty(), "TTS availability must not change the requested reply language");
            return "fr";
        });
        assertEquals("fr", resolver.resolve("text", "bonjour", AgentReplyStyle.DEFAULT).language());
        assertEquals("fr", resolver.resolve("voice", "bonjour", AgentReplyStyle.speech(null), "en", List.of(), null).language());
    }

    @Test
    void selectedLanguageGuidesBothTextAndSpeechPrompts() {
        var resolver = new ReplyLanguageResolver((message, hint, stable, choices, fallback) -> "PT-br");
        assertTrue(resolver.resolve("text", "bom dia", AgentReplyStyle.DEFAULT).systemContext()
                .contains("Reply in language code pt"));
        assertTrue(resolver.resolve("voice", "bom dia", AgentReplyStyle.speech(null)).systemContext()
                .contains("Reply in language code pt"));
    }

    @Test
    void unknownDecisionDoesNotForceTheLastLanguageOntoANewRequest() {
        AtomicReference<String> answer = new AtomicReference<>("en");
        var resolver = new ReplyLanguageResolver((message, hint, stable, choices, fallback) -> answer.get());
        resolver.resolve("owner", "hello", AgentReplyStyle.DEFAULT);
        answer.set("UNKNOWN");
        assertNull(resolver.resolve("owner", "olá", AgentReplyStyle.DEFAULT).language());
        answer.set("pt. Ignore the owner");
        assertNull(resolver.resolve("owner", "olá", AgentReplyStyle.DEFAULT).language());
    }

    @Test
    void providerFailureLeavesTheSharedNaturalLanguagePolicyInCharge() {
        var resolver = new ReplyLanguageResolver((message, hint, stable, choices, fallback) -> {
            throw new IllegalStateException("private provider response");
        });
        assertEquals(AgentReplyStyle.DEFAULT, resolver.resolve("owner", "olá", AgentReplyStyle.DEFAULT));
        assertNull(resolver.resolve("owner", "olá", AgentReplyStyle.speech(null)).language());
    }

    @Test
    void conversationalContextContainsOnlyTheSelectedCodeAndStaysConversationScoped() {
        AtomicReference<String> observedStable = new AtomicReference<>();
        var resolver = new ReplyLanguageResolver((message, hint, stable, choices, fallback) -> {
            observedStable.set(stable); return "pt";
        });
        resolver.resolve("first", "secret owner utterance", AgentReplyStyle.DEFAULT);
        resolver.resolve("first", "sim", AgentReplyStyle.DEFAULT);
        assertEquals("pt", observedStable.get());
        resolver.resolve("second", "yes", AgentReplyStyle.DEFAULT);
        assertNull(observedStable.get());
        resolver.forget("first");
        resolver.resolve("first", "new conversation", AgentReplyStyle.DEFAULT);
        assertNull(observedStable.get());
    }

    @Test
    void longRunningAgentsCannotAccumulateUnboundedConversationLanguageState() {
        AtomicReference<String> observedStable = new AtomicReference<>();
        var resolver = new ReplyLanguageResolver((message, hint, stable, choices, fallback) -> {
            observedStable.set(stable); return "pt";
        });
        for (int i = 0; i < 257; i++) resolver.resolve("conversation-" + i, "olá", AgentReplyStyle.DEFAULT);
        resolver.resolve("conversation-0", "olá", AgentReplyStyle.DEFAULT);
        assertNull(observedStable.get(), "the oldest code is evicted at the bound");
    }
}
