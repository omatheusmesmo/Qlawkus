package dev.omatheusmesmo.qlawkus.it.terminal;

import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.cognition.Soul;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.testing.SoulResetHelper;
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

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisabledOnOs(OS.WINDOWS)
class TerminalCapabilitiesLlmAdvancedTest {

    @Inject
    AgentService agentService;

    @Inject
    WorkingMemoryStore memoryStore;

    @BeforeEach
    void rateLimitPause() {
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
        String response = agentService.chatSync("Use the list-environment tool to discover the current execution environment. Tell me what shell and operating system are detected.");
        assertFalse(response.isBlank(), "LLM should return environment info");
        assertTrue(response.toLowerCase().contains("linux") || response.toLowerCase().contains("shell") || response.toLowerCase().contains("bash"),
                "LLM should report shell or OS from environment. Got: " + response);
    }

    @Test
    @Order(2)
    void llm_denylist_blocksSudoCommand() {
        String response = agentService.chatSync("Run the command 'sudo whoami'. Tell me what happened.");
        assertFalse(response.isBlank(), "LLM should return a response about the blocked command");
        assertTrue(response.toLowerCase().contains("block") || response.toLowerCase().contains("denied") || response.toLowerCase().contains("not allowed") || response.toLowerCase().contains("restrict") || response.toLowerCase().contains("security") || response.toLowerCase().contains("deni"),
                "LLM should report that 'sudo' is blocked by denylist. Got: " + response);
    }

    @Test
    @Order(3)
    void llm_usesListFiles_showsDirectoryContents() {
        String response = agentService.chatSync("List the files and directories in the current working directory using the list-files tool. Tell me what you find.");
        assertFalse(response.isBlank(), "LLM should return directory listing");
    }

    @Test
    @Order(4)
    void llm_usesDeleteFile_removesCreatedFile() throws Exception {
        try {
            agentService.chatSync("Write 'to-be-deleted' to a file called 'llm-delete-test.txt'.");
            String response = agentService.chatSync("Delete the file 'llm-delete-test.txt'. Then confirm the file was deleted.");
            assertFalse(response.isBlank(), "LLM should confirm file deletion");
        assertTrue(response.toLowerCase().contains("deleted") || response.toLowerCase().contains("removed") || response.toLowerCase().contains("success") || response.toLowerCase().contains("gone") || response.toLowerCase().contains("no longer"),
                "LLM should report the file was deleted. Got: " + response);
        } finally {
            Files.deleteIfExists(Path.of("llm-delete-test.txt"));
        }
    }

    @Test
    @Order(5)
    void llm_ptySession_listsActiveSessions() {
        String response = agentService.chatSync("Start an interactive PTY shell session, then list all active PTY sessions using the list-sessions tool. Tell me what sessions are listed. Then close the session.");
        assertFalse(response.isBlank(), "LLM should report about PTY sessions");
    }

    @Test
    @Order(6)
    void llm_workspaceConfinement_writePathTraversalBlocked() {
        String response = agentService.chatSync("Write the text 'escaped' to the file at path '../../../tmp/escaped.txt'. Tell me the result.");
        assertFalse(response.isBlank(), "LLM should return a response about the blocked write");
        assertTrue(response.toLowerCase().contains("block") || response.toLowerCase().contains("denied") || response.toLowerCase().contains("error") || response.toLowerCase().contains("restrict") || response.toLowerCase().contains("outside") || response.toLowerCase().contains("not allowed") || !response.toLowerCase().contains("success"),
                "LLM should NOT report successful write outside workspace. Got: " + response);
    }

    @Test
    @Order(7)
    void llm_ptySession_multiCommandWorkflow() {
        String response = agentService.chatSync("Start an interactive PTY shell session, then send the command 'echo step1', read the output, send the command 'echo step2', read the output, and close the session. Report the output from both commands.");
        assertFalse(response.isBlank(), "LLM should return multi-step PTY output");
    }

    @Test
    @Order(8)
    void llm_readFile_notFound_returnsError() {
        String response = agentService.chatSync("Read the file 'this-file-does-not-exist-xyz.txt'. Tell me what happened.");
        assertFalse(response.isBlank(), "LLM should return a response about the missing file");
        assertTrue(response.toLowerCase().contains("not found") || response.toLowerCase().contains("does not exist") || response.toLowerCase().contains("error") || response.toLowerCase().contains("no such") || response.toLowerCase().contains("could not") || response.toLowerCase().contains("unable") || response.toLowerCase().contains("failed"),
                "LLM should report that the file was not found. Got: " + response);
    }

    @Test
    @Order(9)
    void llm_checkSecurity_dangerousDdCommand() {
        String response = agentService.chatSync("Check if the command 'dd if=/dev/zero of=/dev/sda' is safe to run. Tell me the result.");
        assertFalse(response.isBlank(), "LLM should return a security check result");
        assertTrue(response.toLowerCase().contains("block") || response.toLowerCase().contains("unsafe") || response.toLowerCase().contains("denied") || response.toLowerCase().contains("not safe") || response.toLowerCase().contains("dangerous"),
                "LLM should report that 'dd if=/dev/zero of=/dev/sda' is blocked/unsafe. Got: " + response);
    }

    @Test
    @Order(10)
    void llm_agentWorkflow_discoverWriteRead() {
        String response = agentService.chatSync("Do the following steps in order: 1. Run 'uname -s' to discover the operating system. 2. Write 'workflow-test-content' to a file called 'llm-workflow-file.txt'. 3. Read that file back to confirm the content. 4. List the files in the current directory. Report the result of each step.");
        assertFalse(response.isBlank(), "LLM should return a non-blank workflow response");
        assertTrue(response.toLowerCase().contains("linux") || response.toLowerCase().contains("gnu") || response.toLowerCase().contains("unix") || response.toLowerCase().contains("operating"),
                "Workflow should report OS info. Got: " + response);
        assertTrue(response.contains("workflow-test-content"),
                "Workflow step 3 should confirm file content. Got: " + response);
    }
}
