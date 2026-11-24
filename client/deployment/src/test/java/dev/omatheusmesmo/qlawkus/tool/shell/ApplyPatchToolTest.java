package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.dto.FileResult;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ApplyPatchToolTest {

    @Inject
    @QlawTool
    ApplyPatchTool applyPatch;

    @Inject
    WorkspaceConfinement workspace;

    @Test
    void applyPatch_addUpdateDeleteMove() throws Exception {
        Path base = workspace.getWorkspacePath().resolve("patch-demo-" + System.nanoTime());
        Files.createDirectories(base);
        String prefix = workspace.getWorkspacePath().relativize(base).toString().replace('\\', '/');

        FileResult result = applyPatch.applyPatch("""
                Begin Patch
                Add File: %s/a.txt
                alpha
                Update File: %s/a.txt
                <<<SEARCH
                alpha
                ===
                beta
                >>>
                Add File: %s/b.txt
                keep
                Move File: %s/b.txt
                To: %s/c.txt
                Delete File: %s/a.txt
                End Patch
                """.formatted(prefix, prefix, prefix, prefix, prefix, prefix));

        assertTrue(result.success(), result.error());
        assertFalse(Files.exists(base.resolve("a.txt")));
        assertFalse(Files.exists(base.resolve("b.txt")));
        assertTrue(Files.exists(base.resolve("c.txt")));
    }
}
