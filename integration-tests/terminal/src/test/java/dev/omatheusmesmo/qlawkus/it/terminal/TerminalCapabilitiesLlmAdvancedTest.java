package dev.omatheusmesmo.qlawkus.it.terminal;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.cognition.Soul;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.testing.QlawkusTestUtils;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import dev.omatheusmesmo.qlawkus.testing.SoulResetHelper;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@ConnectWireMock
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisabledOnOs(OS.WINDOWS)
class TerminalCapabilitiesLlmAdvancedTest {

    WireMock wiremock;

    @Inject
    AgentService agentService;

    @Inject
    WorkingMemoryStore memoryStore;

    @BeforeEach
    void setupStubs() {
        QlawkusWireMockStubs.registerOpenAiStubs(wiremock);
    }

    @AfterEach
    @Transactional
    void resetSoul() {
        SoulResetHelper.resetToDefaults();
    }

    @AfterEach
    void clearMemory() {
        memoryStore.deleteMessages("default");
    }

    @AfterEach
    void cleanupWorkspace() throws IOException {
        Files.deleteIfExists(Path.of("llm-delete-test.txt"));
        Files.deleteIfExists(Path.of("llm-workflow-file.txt"));
    }

    @Test
    @Order(1)
    void llm_usesListEnvironment_discoversShellAndOs() {
        String response = agentService.chatSync("it-test", "Use the list-environment tool to discover the current execution environment. Tell me what shell and operating system are detected.");
        assertThat(response, QlawkusTestUtils.containsStringOrMock("linux", "shell", "bash"));
    }

    @Test
    @Order(2)
    void llm_denylist_blocksSudoCommand() {
        String response = agentService.chatSync("it-test", "Run the command 'sudo whoami'. Tell me what happened.");
        assertThat(response, QlawkusTestUtils.containsStringOrMock("block", "denied", "not allowed", "restrict", "security"));
    }

    @Test
    @Order(3)
    void llm_usesListFiles_showsDirectoryContents() {
        String response = agentService.chatSync("it-test", "List the files and directories in the current working directory using the list-files tool. Tell me what you find.");
        assertFalse(response.isBlank(), "LLM should return directory listing");
    }

    @Test
    @Order(4)
    void llm_usesDeleteFile_removesCreatedFile() throws Exception {
        try {
            agentService.chatSync("it-test", "Write 'to-be-deleted' to a file called 'llm-delete-test.txt'.");
            String response = agentService.chatSync("it-test", "Delete the file 'llm-delete-test.txt'. Then confirm the file was deleted.");
            assertThat(response, QlawkusTestUtils.containsStringOrMock("deleted", "removed", "success", "gone", "no longer"));
        } finally {
            Files.deleteIfExists(Path.of("llm-delete-test.txt"));
        }
    }

    @Test
    @Order(5)
    void llm_ptySession_listsActiveSessions() {
        String response = agentService.chatSync("it-test", "Start an interactive PTY shell session, then list all active PTY sessions using the list-sessions tool. Tell me what sessions are listed. Then close the session.");
        assertFalse(response.isBlank(), "LLM should report about PTY sessions");
    }

    @Test
    @Order(6)
    void llm_workspaceConfinement_writePathTraversalBlocked() {
        String response = agentService.chatSync("it-test", "Write the text 'escaped' to the file at path '../../../tmp/escaped.txt'. Tell me the result.");
        assertThat(response, QlawkusTestUtils.containsStringOrMock("block", "denied", "error", "restrict", "outside"));
    }

    @Test
    @Order(7)
    void llm_ptySession_multiCommandWorkflow() {
        String response = agentService.chatSync("it-test", "Start an interactive PTY shell session, then send the command 'echo step1', read the output, send the command 'echo step2', read the output, and close the session. Report the output from both commands.");
        assertFalse(response.isBlank(), "LLM should return multi-step PTY output");
    }

    @Test
    @Order(8)
    void llm_readFile_notFound_returnsError() {
        String response = agentService.chatSync("it-test", "Read the file 'this-file-does-not-exist-xyz.txt'. Tell me what happened.");
        assertThat(response, QlawkusTestUtils.containsStringOrMock("not found", "does not exist", "error", "no such", "could not", "unable", "failed"));
    }

    @Test
    @Order(9)
    void llm_checkSecurity_dangerousDdCommand() {
        String response = agentService.chatSync("it-test", "Check if the command 'dd if=/dev/zero of=/dev/sda' is safe to run. Tell me the result.");
        assertThat(response, QlawkusTestUtils.containsStringOrMock("block", "unsafe", "denied", "not safe", "dangerous"));
    }

    @Test
    @Order(10)
    void llm_agentWorkflow_discoverWriteRead() {
        String response = agentService.chatSync("it-test", "Do the following steps in order: 1. Run 'uname -s' to discover the operating system. 2. Write 'workflow-test-content' to a file called 'llm-workflow-file.txt'. 3. Read that file back to confirm the content. 4. List the files in the current directory. Report the result of each step.");
        assertThat(response, QlawkusTestUtils.containsStringOrMock("linux", "gnu", "unix", "operating"));
        assertThat(response, QlawkusTestUtils.containsStringOrMock("workflow-test-content"));
    }
}
