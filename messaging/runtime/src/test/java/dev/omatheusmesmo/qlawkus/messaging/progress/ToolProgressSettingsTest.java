package dev.omatheusmesmo.qlawkus.messaging.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.agent.ToolProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ToolProgressSettingsTest {

    @Test
    void cyclesThroughEveryModeAndWrapsAround() {
        assertEquals(ToolProgress.STATUS, ToolProgressSettings.next(ToolProgress.OFF));
        assertEquals(ToolProgress.PREVIEW, ToolProgressSettings.next(ToolProgress.STATUS));
        assertEquals(ToolProgress.VERBOSE, ToolProgressSettings.next(ToolProgress.PREVIEW));
        assertEquals(ToolProgress.OFF, ToolProgressSettings.next(ToolProgress.VERBOSE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/verbose", "  /verbose  ", "/VERBOSE", "/verbose@qlawkus_bot"})
    void recognisesTheVerboseCommand(String text) {
        assertTrue(ToolProgressSettings.isVerboseCommand(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"verbose", "/verbosely", "please use /verbose", "/verbose now", ""})
    void ignoresEverythingElse(String text) {
        assertFalse(ToolProgressSettings.isVerboseCommand(text));
    }

    @Test
    void aNullMessageIsNotACommand() {
        assertFalse(ToolProgressSettings.isVerboseCommand(null));
    }

    @Test
    void theChatKeyIsProviderAndChat() {
        assertEquals("telegram:42", ToolProgressSettings.key("telegram", "42"));
    }
}
