package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class FileToolPaginationTest {
    @Inject
    @QlawTool
    FileTool fileTool;

    @Inject
    WorkspaceConfinement workspace;

    Path file;
    String path;

    @BeforeEach
    void fixture() throws Exception {
        Path target = workspace.getWorkspacePath().resolve("target");
        Files.createDirectories(target);
        file = Files.createTempFile(target, "file-page-", ".txt");
        path = workspace.getWorkspacePath().relativize(file).toString();
    }

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(file);
    }

    @Test
    void legacyReadKeepsExactTextAndPagesCanReadPortuguese() throws Exception {
        String content = "Olá\r\nação 😀\r\nmais uma linha\r\n";
        Files.writeString(file, content);
        assertEquals(content, fileTool.readFile(path).content());
        assertEquals(content, fileTool.readFile(path, null, null).content());
        var page = fileTool.readFile(path, 2, 1);
        assertTrue(page.success(), page.error());
        assertEquals("2|ação 😀\n[TRUNCATED — continue with offset=3 and limit=1]", page.content());
    }

    @Test
    void missingAndOversizedLimitsUseTheConfiguredPageMaximum() throws Exception {
        Files.writeString(file, "line\n".repeat(2001));
        String defaultPage = fileTool.readFile(path, 1, null).content();
        assertTrue(defaultPage.startsWith("1|line\n"));
        assertTrue(defaultPage.endsWith("[TRUNCATED — continue with offset=2001 and limit=2000]"));
        assertEquals(defaultPage, fileTool.readFile(path, null, Integer.MAX_VALUE).content());
        assertEquals("2001|line\n", fileTool.readFile(path, 2001, 100).content());
    }

    @Test
    void paginatedReadsKeepTheSecurityAndBinaryGuards() throws Exception {
        assertFalse(fileTool.readFile("../../../outside.txt", 1, 10).success());
        Files.write(file, new byte[] {0, 1, 2});
        var result = fileTool.readFile(path, 1, 10);
        assertFalse(result.success());
        assertTrue(result.error().contains("Binary"));
    }

    @Test
    void invalidLineNumbersAndLimitsAreRejected() {
        assertFalse(fileTool.readFile(path, 0, 1).success());
        assertFalse(fileTool.readFile(path, -1, 1).success());
        assertFalse(fileTool.readFile(path, 1, 0).success());
        assertFalse(fileTool.readFile(path, 1, -1).success());
    }

    @Test
    void theAgentSeesOneReadToolWithOptionalPagingParameters() {
        var reads = ToolSpecifications.toolSpecificationsFrom(FileTool.class).stream()
                .filter(spec -> spec.name().equals("readFile")).toList();
        assertEquals(1, reads.size());
        var schema = reads.getFirst().parameters();
        assertTrue(schema.properties().containsKey("offset"));
        assertTrue(schema.properties().containsKey("limit"));
        assertEquals(java.util.List.of("path"), schema.required());
    }
}
