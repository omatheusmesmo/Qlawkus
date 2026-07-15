package dev.omatheusmesmo.qlawkus.console.onboarding;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Guards the local-endpoint detection that keeps the first-run setup banner from nagging a working
 * local install (Ollama with a placeholder key) to configure a key it does not need.
 */
class SetupStateTest {

    @Test
    void cloudEndpointIsNotLocal() {
        assertFalse(SetupState.isLocalEndpoint("https://integrate.api.nvidia.com/v1"));
        assertFalse(SetupState.isLocalEndpoint("https://api.openai.com/v1"));
    }

    @Test
    void dockerOllamaHostIsLocal() {
        assertTrue(SetupState.isLocalEndpoint("http://ollama:11434/v1"));
    }

    @Test
    void loopbackIsLocal() {
        assertTrue(SetupState.isLocalEndpoint("http://localhost:11434/v1"));
        assertTrue(SetupState.isLocalEndpoint("http://127.0.0.1:8000/v1"));
    }

    @Test
    void blankOrNullIsNotLocal() {
        assertFalse(SetupState.isLocalEndpoint(""));
        assertFalse(SetupState.isLocalEndpoint(null));
    }
}
