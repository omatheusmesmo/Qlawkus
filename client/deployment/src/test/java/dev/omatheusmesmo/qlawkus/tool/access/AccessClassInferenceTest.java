package dev.omatheusmesmo.qlawkus.tool.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AccessClassInferenceTest {

    @ParameterizedTest
    @CsvSource({
            "listEvents,READ", "getEmail,READ", "readFile,READ", "searchWeb,READ", "findTraces,READ", "fetchUrl,READ",
            "checkAvailability,READ", "queryMetrics,READ", "downloadFile,READ", "globFiles,READ", "grepFiles,READ",
            "generateMarkdownReport,READ", "analyzeCodeQuality,READ", "suggestFocusTime,READ", "testMcpConnection,READ",
            "createEvent,CREATE", "addAchievement,CREATE", "scheduleTask,CREATE", "saveNote,CREATE",
            "installSkill,CREATE",
            "updateCell,UPDATE", "setTimezone,UPDATE", "editFile,UPDATE", "renameFile,UPDATE", "writeFile,UPDATE", "untrashEmail,UPDATE",
            "deleteFile,DELETE", "removeMcpConnection,DELETE", "cancelScheduledTask,DELETE", "trashEmail,DELETE",
            "sendEmail,SEND", "shareFile,SEND", "inviteGuest,SEND", "postMessage,SEND", "replyToEmail,SEND",
            "runCommand,EXECUTE", "execScript,EXECUTE", "shellExec,EXECUTE"
    })
    void theLeadingVerbOfTheMethodNameDecidesTheClass(String method, AccessClass expected) {
        assertEquals(Optional.of(expected), AccessClassInference.fromMethodName(method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"listing", "address", "settle", "reader", "checker", "testing", "writer", "installer", "probeStep", "uploadFile", "applyPatch",
            "configureHeartbeat", "disconnectMcpAuthorization", "heartbeatStatus", "", "LIST"})
    void onlyAWholeLeadingWordCounts(String method) {
        assertTrue(AccessClassInference.fromMethodName(method).isEmpty(), method);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fileList", "driveGet", "emailSend"})
    void aVerbThatIsNotLeadingIsIgnored(String method) {
        assertTrue(AccessClassInference.fromMethodName(method).isEmpty(), method);
    }

    @ParameterizedTest
    @CsvSource({"get2,READ", "list_all,READ"})
    void aDigitOrUnderscoreEndsTheLeadingWord(String method, AccessClass expected) {
        assertEquals(Optional.of(expected), AccessClassInference.fromMethodName(method));
    }
}
