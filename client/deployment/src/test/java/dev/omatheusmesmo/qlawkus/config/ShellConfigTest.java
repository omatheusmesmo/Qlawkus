package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ShellConfigTest {

    @Inject
    ShellConfig shellConfig;

    @Test
    void maxConcurrent_defaultIsPositive() {
        assertTrue(shellConfig.maxConcurrent() > 0, "maxConcurrent should be positive");
    }

    @Test
    void defaultTimeout_defaultIsPositive() {
        assertTrue(shellConfig.defaultTimeout() > 0, "defaultTimeout should be positive");
    }

    @Test
    void maxOutputBytes_defaultIsPositive() {
        assertTrue(shellConfig.maxOutputBytes() > 0, "maxOutputBytes should be positive");
    }

    @Test
    void maxOutputLines_defaultIsPositive() {
        assertTrue(shellConfig.maxOutputLines() > 0, "maxOutputLines should be positive");
    }

    @Test
    void denylist_notEmpty() {
        assertFalse(shellConfig.denylist().isEmpty(), "denylist should have entries");
    }

    @Test
    void denylist_containsSudo() {
        assertTrue(shellConfig.denylist().stream().anyMatch(p -> p.contains("sudo")),
            "denylist should contain sudo pattern");
    }

    @Test
    void denylist_containsShutdown() {
        assertTrue(shellConfig.denylist().stream().anyMatch(p -> p.contains("shutdown")),
            "denylist should contain shutdown pattern");
    }

    @Test
    void allowlistMode_defaultIsFalse() {
        assertFalse(shellConfig.allowlistMode(), "allowlistMode should default to false");
    }

    @Test
    void defaultShell_notBlank() {
        assertFalse(shellConfig.defaultShell().isBlank(), "defaultShell should not be blank");
    }

    @Test
    void cleanProfile_defaultIsTrue() {
        assertTrue(shellConfig.cleanProfile(), "cleanProfile should default to true");
    }

    @Test
    void ptyConfig_maxSessionsPositive() {
        ShellConfig.PtyConfig pty = shellConfig.pty();
        assertNotNull(pty, "PtyConfig should not be null");
        assertTrue(pty.maxSessions() > 0, "maxSessions should be positive");
    }

    @Test
    void ptyConfig_idleTimeoutPositive() {
        ShellConfig.PtyConfig pty = shellConfig.pty();
        assertTrue(pty.idleTimeoutMinutes() > 0, "idleTimeoutMinutes should be positive");
    }

    @Test
    void ptyConfig_bufferLinesPositive() {
        ShellConfig.PtyConfig pty = shellConfig.pty();
        assertTrue(pty.bufferLines() > 0, "bufferLines should be positive");
    }

    @Test
    void ptyConfig_defaultColsPositive() {
        ShellConfig.PtyConfig pty = shellConfig.pty();
        assertTrue(pty.defaultCols() > 0, "defaultCols should be positive");
    }

    @Test
    void ptyConfig_defaultRowsPositive() {
        ShellConfig.PtyConfig pty = shellConfig.pty();
        assertTrue(pty.defaultRows() > 0, "defaultRows should be positive");
    }
}
