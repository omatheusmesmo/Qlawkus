package dev.omatheusmesmo.qlawkus.it.brag;

import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tools.brag.BragEntry;
import dev.omatheusmesmo.qlawkus.tools.brag.BragTool;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(BragTestProfile.class)
@Execution(ExecutionMode.SAME_THREAD)
class BragToolTest {

    @InjectMock
    ChatModel chatModel;

    @Inject
    @ClawTool
    BragTool bragTool;

    @AfterEach
    @Transactional
    void cleanup() {
        BragEntry.deleteAll();
    }

    @Test
    @Transactional
    void addAchievement_createsEntryWithTranslatedImpact() {
        when(chatModel.chat(anyString())).thenReturn("Improved query performance reducing latency by 40%");

        String result = bragTool.addAchievement("Changed database index on users table", "2026-05-07", "my-project");

        assertTrue(result.contains("Achievement recorded"));
        assertTrue(result.contains("Business impact"));

        BragEntry entry = BragEntry.findDuplicate(LocalDate.of(2026, 5, 7),
                "Changed database index on users table", "my-project");
        assertNotNull(entry);
        assertEquals("Changed database index on users table", entry.achievement);
        assertEquals(LocalDate.of(2026, 5, 7), entry.date);
        assertEquals("my-project", entry.repo);
        assertFalse(entry.deleted);
        assertNotNull(entry.impact);
    }

    @Test
    @Transactional
    void addAchievement_skipsDuplicate() {
        when(chatModel.chat(anyString())).thenReturn("Impact statement");

        bragTool.addAchievement("Fixed null pointer in auth", "2026-05-07", null);
        String result = bragTool.addAchievement("Fixed null pointer in auth", "2026-05-07", null);

        assertTrue(result.contains("Duplicate achievement already recorded"));
        assertEquals(1, BragEntry.count());
    }

    @Test
    @Transactional
    void addAchievement_handlesImpactTranslationFailure() {
        when(chatModel.chat(anyString())).thenThrow(new RuntimeException("LLM unavailable"));

        String result = bragTool.addAchievement("Deployed hotfix to production", "2026-05-07", null);

        assertTrue(result.contains("Achievement recorded"));

        BragEntry entry = BragEntry.findDuplicate(LocalDate.of(2026, 5, 7),
                "Deployed hotfix to production", null);
        assertNotNull(entry);
    }

    @Test
    @Transactional
    void addAchievement_usesTodayWhenDateNotProvided() {
        when(chatModel.chat(anyString())).thenReturn("Impact");

        bragTool.addAchievement("Wrote integration tests", null, null);

        long count = BragEntry.find("achievement = ?1 and date = ?2",
                "Wrote integration tests", LocalDate.now(java.time.ZoneOffset.UTC)).count();
        assertEquals(1, count);
    }

    @Test
    void deleteAchievement_softDeletesEntry() {
        when(chatModel.chat(anyString())).thenReturn("Impact");

        bragTool.addAchievement("Refactored service layer", "2026-05-07", null);

        Long id = findEntryId("Refactored service layer");

        String result = bragTool.deleteAchievement(id);

        assertTrue(result.contains("marked as deleted"));
        assertTrue(isEntryDeleted(id));
    }

    @Test
    void deleteAchievement_returnsNotFoundForMissingId() {
        String result = bragTool.deleteAchievement(99999L);
        assertTrue(result.contains("not found"));
    }

    @Test
    void deleteAchievement_returnsAlreadyDeleted() {
        when(chatModel.chat(anyString())).thenReturn("Impact");

        bragTool.addAchievement("Deployed v2", "2026-05-07", null);

        Long id = findEntryId("Deployed v2");

        bragTool.deleteAchievement(id);
        String result = bragTool.deleteAchievement(id);

        assertTrue(result.contains("already deleted"));
    }

    @Test
    @Transactional
    void generateMarkdownReport_returnsFormattedMarkdown() {
        when(chatModel.chat(anyString())).thenReturn("Improved performance");

        bragTool.addAchievement("Optimized query", "2026-04-01", "api-service");
        bragTool.addAchievement("Fixed memory leak", "2026-04-15", null);

        String report = bragTool.generateMarkdownReport("2026-03-01", "2026-05-01");

        assertTrue(report.contains("# Brag Document"));
        assertTrue(report.contains("Optimized query"));
        assertTrue(report.contains("Fixed memory leak"));
        assertTrue(report.contains("api-service"));
        assertTrue(report.contains("Improved performance"));
        assertTrue(report.contains("Total: 2 achievements"));
    }

    @Test
    void generateMarkdownReport_returnsNoAchievementsMessageWhenEmpty() {
        String report = bragTool.generateMarkdownReport("2020-01-01", "2020-12-31");
        assertTrue(report.contains("No achievements found"));
    }

    @Transactional
    Long findEntryId(String achievement) {
        BragEntry entry = BragEntry.find("achievement", achievement).firstResult();
        return entry != null ? entry.id : null;
    }

    @Transactional
    boolean isEntryDeleted(Long id) {
        BragEntry entry = BragEntry.find("id = ?1", id).firstResult();
        return entry != null && entry.deleted;
    }
}
