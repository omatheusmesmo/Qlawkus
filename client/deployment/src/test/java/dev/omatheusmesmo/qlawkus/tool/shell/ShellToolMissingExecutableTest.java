package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Pins the missing-binary path of the code-review tools: a container image without {@code git} or
 * {@code gh} used to fail with a bare {@code sh: gh: not found}, so the check runs against the PATH
 * the command itself would see (the workspace env file can override it) and names the binary.
 */
@DisabledOnOs(OS.WINDOWS)
class ShellToolMissingExecutableTest {

    @TempDir
    Path bin;

    private WorkspaceConfinement workspaceConfinement;
    private ShellTool shellTool;

    @BeforeEach
    void setUp() {
        workspaceConfinement = Mockito.mock(WorkspaceConfinement.class);
        shellTool = new ShellTool();
        shellTool.workspaceConfinement = workspaceConfinement;
    }

    @Test
    void aPathWithoutGhNamesItAndPointsAtTheDevtoolsImage() {
        pathIs(bin.toString());

        Optional<CommandResult> missing = shellTool.missingExecutable("gh");

        assertTrue(missing.isPresent(), "gh is not in an empty PATH directory");
        assertEquals(ShellTool.MISSING_EXECUTABLE_EXIT_CODE, missing.get().exitCode());
        assertTrue(missing.get().stderr().contains("'gh'"), missing.get().stderr());
        assertTrue(missing.get().stderr().contains("QLAWKUS_IMAGE_VARIANT=devtools"), missing.get().stderr());
    }

    @Test
    void theWorkspacePathWinsOverTheProcessPath() {
        pathIs(bin.toString());

        assertTrue(shellTool.missingExecutable("sh").isPresent(),
                "the command runs with the workspace PATH, so a binary only on the JVM's PATH is missing to it");
    }

    @Test
    void anExecutableOnThePathIsFound() throws IOException {
        executable("gh");
        pathIs("/nonexistent:" + bin);

        assertFalse(shellTool.missingExecutable("gh").isPresent());
    }

    @Test
    void aFileWithoutTheExecuteBitDoesNotCount() throws IOException {
        Files.writeString(bin.resolve("git"), "#!/bin/sh\n");
        pathIs(bin.toString());

        assertTrue(shellTool.missingExecutable("git").isPresent());
    }

    @Test
    void aBinaryTheDevtoolsImageDoesNotShipGetsNoDevtoolsHint() {
        pathIs(bin.toString());

        String stderr = shellTool.missingExecutable("mvn").orElseThrow().stderr();

        assertTrue(stderr.contains("'mvn'"), stderr);
        assertFalse(stderr.contains("devtools"), stderr);
    }

    @Test
    void aPathQualifiedCommandIsNotLookedUpOnThePath() {
        pathIs(bin.toString());

        assertFalse(shellTool.missingExecutable("./mvnw").isPresent());
    }

    @Test
    void withoutAWorkspacePathTheProcessPathIsUsed() {
        when(workspaceConfinement.getWorkspaceEnv()).thenReturn(Map.of());

        assertFalse(shellTool.missingExecutable("sh").isPresent());
    }

    private void pathIs(String path) {
        when(workspaceConfinement.getWorkspaceEnv()).thenReturn(Map.of("PATH", path));
    }

    private void executable(String name) throws IOException {
        Path file = Files.writeString(bin.resolve(name), "#!/bin/sh\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
    }
}
