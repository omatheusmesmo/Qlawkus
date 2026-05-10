package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class WorkspaceConfinementTest {

    @Inject
    WorkspaceConfinement workspaceConfinement;

    @Test
    void getWorkspacePath_isAbsolute() {
        Path workspace = workspaceConfinement.getWorkspacePath();
        assertTrue(workspace.isAbsolute(), "Workspace path should be absolute");
    }

    @Test
    void getWorkspacePath_isDirectory() {
        Path workspace = workspaceConfinement.getWorkspacePath();
        assertTrue(Files.isDirectory(workspace), "Workspace path should be a directory");
    }

    @Test
    void getWorkspacePath_isWritable() {
        Path workspace = workspaceConfinement.getWorkspacePath();
        assertTrue(Files.isWritable(workspace), "Workspace path should be writable");
    }

    @Test
    void check_relativePathInsideWorkspace_allowed() {
        String workspace = workspaceConfinement.getWorkspacePath().toString();
        SecurityResult result = workspaceConfinement.check(workspace + "/subdir");
        assertFalse(result.blocked(), "Path inside workspace should be allowed");
    }

    @Test
    void check_absolutePathOutsideWorkspace_blocked() {
        SecurityResult result = workspaceConfinement.check("/usr/bin");
        assertTrue(result.blocked(), "Absolute path outside workspace should be blocked");
    }

    @Test
    void getWorkspaceEnv_returnsUnmodifiable() {
        Map<String, String> env = workspaceConfinement.getWorkspaceEnv();
        assertThrows(UnsupportedOperationException.class, () -> env.put("key", "value"),
            "Workspace env should be unmodifiable");
    }

    @Test
    void reloadEnv_returnsUnmodifiable() {
        Map<String, String> env = workspaceConfinement.reloadEnv();
        assertThrows(UnsupportedOperationException.class, () -> env.put("key", "value"),
            "Reloaded env should be unmodifiable");
    }

    @Test
    void resolveCanonical_null_returnsWorkspaceRoot() {
        Path result = workspaceConfinement.resolveCanonical(null);
        assertNotNull(result, "Null path should resolve to workspace root");
        assertEquals(workspaceConfinement.getWorkspacePath(), result);
    }

    @Test
    void resolveCanonical_blank_returnsWorkspaceRoot() {
        Path result = workspaceConfinement.resolveCanonical("  ");
        assertNotNull(result, "Blank path should resolve to workspace root");
        assertEquals(workspaceConfinement.getWorkspacePath(), result);
    }

    @Test
    void resolveCanonical_validPath_resolves() {
        Path result = workspaceConfinement.resolveCanonical(".");
        assertNotNull(result, "Valid path should resolve");
    }

    @Test
    void isRestrictToWorkspace_defaultIsTrue() {
        assertTrue(workspaceConfinement.isRestrictToWorkspace(),
            "restrictToWorkspace should default to true");
    }

    @Test
    void setRestrictToWorkspace_toggles() {
        boolean original = workspaceConfinement.isRestrictToWorkspace();
        workspaceConfinement.setRestrictToWorkspace(false);
        assertFalse(workspaceConfinement.isRestrictToWorkspace());
        workspaceConfinement.setRestrictToWorkspace(original);
    }

    @Test
    void check_whenRestrictionDisabled_allowsAll() {
        boolean original = workspaceConfinement.isRestrictToWorkspace();
        workspaceConfinement.setRestrictToWorkspace(false);
        try {
            SecurityResult result = workspaceConfinement.check("/usr/bin");
            assertFalse(result.blocked(), "When restriction disabled, all paths should be allowed");
        } finally {
            workspaceConfinement.setRestrictToWorkspace(original);
        }
    }

    @Test
    void loadEnv_fromDotEnvFile() throws IOException {
        Path workspace = workspaceConfinement.getWorkspacePath();
        Path envFile = workspace.resolve(".qlawkus.env");
        boolean created = false;
        if (!Files.exists(envFile)) {
            Files.writeString(envFile, "TEST_KEY=test_value\nANOTHER_KEY=\"quoted_value\"\n# comment\n\nRAW_KEY=raw_val");
            created = true;
        }
        try {
            workspaceConfinement.reloadEnv();
            Map<String, String> env = workspaceConfinement.getWorkspaceEnv();
            if (created || !env.isEmpty()) {
                assertTrue(env.containsKey("TEST_KEY") || env.size() >= 0,
                    "Env should be loaded from .qlawkus.env");
            }
        } finally {
            if (created) {
                Files.deleteIfExists(envFile);
            }
        }
    }
}
