package dev.omatheusmesmo.qlawkus.it.brag;

import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.tools.brag.ImpactTranslator;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class ImpactTranslatorTest {

    @InjectMock
    ChatModel chatModel;

    @Inject
    ImpactTranslator impactTranslator;

    @Test
    void translate_returnsBusinessImpactStatement() {
        when(chatModel.chat(anyString())).thenReturn("Improved query response time by 40%, enhancing user experience");

        String result = impactTranslator.translate("Changed database index on users table");

        assertEquals("Improved query response time by 40%, enhancing user experience", result);
    }

    @Test
    void translate_reframesTechnicalAchievementAsBusinessImpact() {
        when(chatModel.chat(anyString())).thenReturn("Reduced system failure risk and improved operational reliability");

        String result = impactTranslator.translate("Added circuit breaker to payment service");

        assertEquals("Reduced system failure risk and improved operational reliability", result);
    }

    @Test
    void translate_propagatesLlmException() {
        when(chatModel.chat(anyString())).thenThrow(new RuntimeException("LLM unavailable"));

        assertThrows(RuntimeException.class, () ->
                impactTranslator.translate("Deployed hotfix"));
    }
}
