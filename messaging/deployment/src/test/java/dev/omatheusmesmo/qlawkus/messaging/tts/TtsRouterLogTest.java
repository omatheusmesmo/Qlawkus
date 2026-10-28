package dev.omatheusmesmo.qlawkus.messaging.tts;

import dev.omatheusmesmo.qlawkus.messaging.logging.MessagingLogCapture;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TtsRouterLogTest {

    @Test
    void synthesisLogsProviderLanguageCharsAndOutcomeButNotTheText() {
        TtsRouter router = router(providerWith("openai", Optional.empty()), new FixedClient("openai", null));

        try (MessagingLogCapture logs = new MessagingLogCapture()) {
            assertEquals(3, router.synthesize("secret reply", "pt").length);

            List<MessagingLogCapture.Line> synthesized = logs.containing("Speech synthesized");
            assertEquals(1, synthesized.size(), () -> "expected one synthesis line, got " + logs.lines());
            String line = synthesized.get(0).message();
            assertTrue(line.contains("provider=openai"), line);
            assertTrue(line.contains("language=pt"), line);
            assertTrue(line.contains("chars=12"), line);
            assertTrue(line.contains("outcome=ok"), line);
            assertTrue(line.matches(".*elapsedMs=\\d+.*"), line);
            assertTrue(logs.lines().stream().noneMatch(l -> l.message().contains("secret")),
                    () -> "reply text reached a log line: " + logs.lines());
        }
    }

    @Test
    void failedSynthesisWarnsWithExceptionClassAndRethrows() {
        TtsRouter router = router(providerWith("elevenlabs", Optional.empty()),
                new FixedClient("elevenlabs", new IllegalStateException("upstream echoed: secret reply")));

        try (MessagingLogCapture logs = new MessagingLogCapture()) {
            assertThrows(IllegalStateException.class, () -> router.synthesize("secret reply", "pt"));

            List<MessagingLogCapture.Line> failed = logs.containing("Speech synthesis failed");
            assertEquals(1, failed.size(), () -> "expected one failure line, got " + logs.lines());
            assertEquals(java.util.logging.Level.WARNING, failed.get(0).level());
            String line = failed.get(0).message();
            assertTrue(line.contains("provider=elevenlabs"), line);
            assertTrue(line.contains("exception=IllegalStateException"), line);
            assertTrue(logs.lines().stream().noneMatch(l -> l.message().contains("secret")),
                    () -> "reply text reached a log line: " + logs.lines());
        }
    }

    private static TtsRouter router(TtsConfig.TtsProvider provider, TtsClient client) {
        TtsRouter router = new TtsRouter();
        TtsConfig config = Mockito.mock(TtsConfig.class);
        when(config.enabled()).thenReturn(true);
        when(config.defaultLanguage()).thenReturn("pt");
        when(config.providers()).thenReturn(Map.of("pt", provider));
        router.config = config;
        router.clients = List.of(client);
        return router;
    }

    private static TtsConfig.TtsProvider providerWith(String kind, Optional<String> fallback) {
        TtsConfig.TtsProvider provider = Mockito.mock(TtsConfig.TtsProvider.class);
        when(provider.kind()).thenReturn(kind);
        when(provider.fallback()).thenReturn(fallback);
        return provider;
    }

    private record FixedClient(String kind, RuntimeException failure) implements TtsClient {

        @Override
        public String kind() {
            return kind;
        }

        @Override
        public byte[] synthesize(TtsConfig.TtsProvider provider, String text) {
            if (failure != null) {
                throw failure;
            }
            return new byte[] {1, 2, 3};
        }
    }
}
