package dev.omatheusmesmo.qlawkus.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillScripts;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ViewSkillToolScriptsTest {

    @TempDir
    Path scriptsDir;

    private SkillStore store;
    private ViewSkillTool tool;

    @BeforeEach
    void setUp() throws IOException {
        store = mock(SkillStore.class);
        when(store.get("triage")).thenReturn(Optional.of(new Skill("triage", "Triage", "Run the script.")));
        Files.createDirectories(scriptsDir.resolve("lib"));
        Files.writeString(scriptsDir.resolve("run.sh"), "x");
        Files.writeString(scriptsDir.resolve("lib/util.sh"), "x");
        Files.writeString(scriptsDir.resolve(SkillScripts.INSTALLED_MARKER), "acme/skills");
        tool = new ViewSkillTool(store);
    }

    @Test
    void theBodyNamesTheScriptsAndTheToolThatRunsOwnOnes() {
        when(store.scriptsOf("triage")).thenReturn(Optional.of(new SkillScripts(scriptsDir, false)));

        String body = tool.viewSkill("triage");

        assertTrue(body.startsWith("Run the script."));
        assertTrue(body.contains("runSkillScript"), body);
        assertTrue(body.contains("lib/util.sh, run.sh"), body);
        assertFalse(body.contains(SkillScripts.INSTALLED_MARKER), body);
    }

    @Test
    void installedScriptsPointAtTheApprovedTool() {
        when(store.scriptsOf("triage")).thenReturn(Optional.of(new SkillScripts(scriptsDir, true)));

        assertTrue(tool.viewSkill("triage").contains("runInstalledSkillScript"));
    }

    @Test
    void aSkillWithoutScriptsReturnsJustItsBody() {
        when(store.scriptsOf("triage")).thenReturn(Optional.empty());

        assertEquals("Run the script.", tool.viewSkill("triage"));
    }
}
