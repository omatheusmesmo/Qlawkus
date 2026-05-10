package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class WorkspaceConfigTest {

    @Inject
    WorkspaceConfig workspaceConfig;

    @Test
    void root_notBlank() {
        assertFalse(workspaceConfig.root().isBlank(), "root should not be blank");
    }

    @Test
    void restrictToWorkspace_defaultIsTrue() {
        assertTrue(workspaceConfig.restrictToWorkspace(), "restrictToWorkspace should default to true");
    }

    @Test
    void envFile_notBlank() {
        assertFalse(workspaceConfig.envFile().isBlank(), "envFile should not be blank");
    }

    @Test
    void envFile_defaultIsQlawkusEnv() {
        assertEquals(".qlawkus.env", workspaceConfig.envFile(), "Default env file should be .qlawkus.env");
    }
}
