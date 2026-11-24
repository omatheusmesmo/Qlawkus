package dev.omatheusmesmo.qlawkus.tool.shell;

import static org.junit.jupiter.api.Assertions.*;

import tools.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.omatheusmesmo.qlawkus.dto.FileResult;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@QuarkusTest
@TestProfile(FileEditToolRegistrationTest.Profile.class)
class FileEditToolRegistrationTest {

    @Inject
    QlawToolProvider provider;

    @Inject
    WorkspaceConfinement confinement;

    @Inject
    ObjectMapper mapper;

    @Test
    void registeredToolAcceptsABatchThroughItsRealJsonExecutor() throws Exception {
        var registered = provider.provideTools(null).tools().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals("editFile"))
                .findFirst().orElseThrow();
        assertEquals(List.of("path"), registered.getKey().parameters().required());
        var file = confinement.getWorkspacePath().resolve("registered-edit.txt");
        Files.writeString(file, "one\n");
        try {
            String response = registered.getValue().execute(ToolExecutionRequest.builder()
                    .name("editFile")
                    .arguments("{\"path\":\"registered-edit.txt\",\"edits\":[{\"oldText\":\"one\",\"newText\":\"two\"}]}")
                    .build(), "file-edit-registration");
            assertTrue(mapper.readValue(response, FileResult.class).success(), response);
            assertEquals("two\n", Files.readString(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.test-port", "0", "quarkus.management.test-port", "0",
                    "qlawkus.agent.state.root", "target/file-edit-registration/state",
                    "qlawkus.workspace.root", "target/file-edit-registration/workspace");
        }
    }
}
