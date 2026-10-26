package dev.omatheusmesmo.qlawkus.messaging.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class ToolEmojiTest {

    @ParameterizedTest
    @CsvSource({
            "queryMetrics, 📊",
            "findTraces, 📊",
            "getTrace, 📊",
            "queryLogs, 📊",
            "searchMemories, 🧠",
            "searchTranscripts, 🧠",
            "rememberFact, 🧠",
            "viewSkill, 🧠",
            "calendarListEvents, 📅",
            "sendMail, 📧",
            "shellExec, 💻",
            "webSearch, 🔎",
            "fetchUrl, 🔎",
            "readFile, 📄",
            "listFiles, 📄",
            "someUnknownTool, 🔧"
    })
    void mapsToolNamesToTheirCategory(String toolName, String emoji) {
        assertEquals(emoji, ToolEmoji.of(toolName));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void blankNameFallsBackToTheDefault(String toolName) {
        assertEquals(ToolEmoji.DEFAULT, ToolEmoji.of(toolName));
    }

    @Test
    void telemetryWinsOverSearchForFindTraces() {
        assertEquals("📊", ToolEmoji.of("findTraces"));
    }
}
