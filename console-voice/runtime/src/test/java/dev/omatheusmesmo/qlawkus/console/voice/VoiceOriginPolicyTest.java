package dev.omatheusmesmo.qlawkus.console.voice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class VoiceOriginPolicyTest {

    @Test
    void sameOriginAsTheHostIsAccepted() {
        assertTrue(VoiceOriginPolicy.allowed("http://localhost:8080", "localhost:8080", List.of()));
        assertTrue(VoiceOriginPolicy.allowed("https://agent.example.com", "agent.example.com", List.of()));
    }

    @Test
    void aDifferentHostIsRejected() {
        assertFalse(VoiceOriginPolicy.allowed("https://evil.example", "agent.example.com", List.of()));
    }

    @Test
    void aMissingOriginIsRejected() {
        assertFalse(VoiceOriginPolicy.allowed(null, "agent.example.com", List.of()));
        assertFalse(VoiceOriginPolicy.allowed("  ", "agent.example.com", List.of()));
    }

    @Test
    void aNonWebSchemeIsRejectedEvenWhenTheHostMatches() {
        assertFalse(VoiceOriginPolicy.allowed("file://agent.example.com", "agent.example.com", List.of()));
    }

    @Test
    void anExplicitlyAllowedOriginIsAcceptedWhateverTheHost() {
        assertTrue(VoiceOriginPolicy.allowed("https://console.example.com", "internal:8080",
            List.of("https://console.example.com")));
    }
}
