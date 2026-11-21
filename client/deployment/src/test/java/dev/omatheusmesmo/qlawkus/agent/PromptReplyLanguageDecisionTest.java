package dev.omatheusmesmo.qlawkus.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.config.ReplyLanguageConfig;
import dev.omatheusmesmo.qlawkus.model.ReplyLanguageModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PromptReplyLanguageDecisionTest {
    @Test
    void classificationReceivesBoundedDataWithoutMemoryOrTools() throws Exception {
        var provider = provider(true);
        when(provider.model.classify(anyString())).thenReturn("pt");
        assertEquals("pt", provider.decide("a".repeat(1999) + "😀more", "pt", "en", List.of(), null));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(provider.model).classify(body.capture());
        var data = provider.json.readTree(body.getValue());
        assertEquals(1999, data.path("utterance").asText().length());
        assertEquals("pt", data.path("recognizedLanguage").asText());
        assertEquals("en", data.path("stableLanguage").asText());
        assertEquals(4, data.size());
    }

    @Test
    void theRegisteredNvidiaModelsJsonReplyIsAcceptedWithoutAcceptingProseOrExtraFields() {
        var provider = provider(true);
        var resolver = new ReplyLanguageResolver(provider);
        when(provider.model.classify(anyString())).thenReturn("{\"language\":\"pt\"}");
        assertEquals("pt", resolver.resolve("voice", "bom dia como ta o projeto",
                AgentReplyStyle.speech(null), "es", List.of(), null).language());
        for (String invalid : List.of("{\"language\":\"pt\",\"extra\":true}",
                "{\"language\":\"pt\"} trailing", "Answer in pt", "x".repeat(257))) {
            when(provider.model.classify(anyString())).thenReturn(invalid);
            assertNull(resolver.resolve("text", "olá", AgentReplyStyle.DEFAULT).language());
        }
    }

    @Test
    void disabledDistributionsDoNotCallTheRegisteredModel() {
        var provider = provider(false);
        assertNull(provider.decide("olá", null, null, List.of(), null));
        verifyNoInteractions(provider.model);
    }

    @Test
    void aProviderFailureLeavesLanguageToTheSharedPromptAndRecognizer() {
        var provider = provider(true);
        when(provider.model.classify(anyString())).thenThrow(new IllegalStateException("private provider detail"));
        assertNull(provider.decide("olá", null, null, List.of(), null));
        var resolver = new ReplyLanguageResolver(provider);
        assertEquals("pt", resolver.resolve("voice", "Oi.", AgentReplyStyle.speech(null), "pt-BR", List.of(), null).language());
        assertNull(resolver.resolve("text", "olá", AgentReplyStyle.DEFAULT).language());
    }

    private static PromptReplyLanguageDecision provider(boolean enabled) {
        var provider = new PromptReplyLanguageDecision();
        provider.config = () -> enabled;
        provider.model = mock(ReplyLanguageModel.class);
        provider.json = new ObjectMapper();
        return provider;
    }
}
