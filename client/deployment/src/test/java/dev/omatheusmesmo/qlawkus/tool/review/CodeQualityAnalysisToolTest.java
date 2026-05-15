package dev.omatheusmesmo.qlawkus.tool.review;

import dev.omatheusmesmo.qlawkus.store.FactStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CodeQualityAnalysisToolTest {

    private FactStore factStore;
    private CodeQualityAnalysisTool tool;

    @BeforeEach
    void setUp() {
        factStore = Mockito.mock(FactStore.class);
        tool = new CodeQualityAnalysisTool();
        tool.factStore = factStore;
    }

    @Test
    void analyzeCodeQuality_returnsDiffAndRulesFromMemory() {
        List<String> rules = List.of("Functions should do one thing", "Avoid magic numbers");
        when(factStore.search(
                eq(CodeQualityAnalysisTool.RULES_QUERY),
                eq(CodeQualityAnalysisTool.MAX_RULES),
                eq(CodeQualityAnalysisTool.MIN_SCORE)))
                .thenReturn(rules);

        String diff = "@@ -1,3 +1,5 @@\n+int x = 42;\n+doSomethingAndAlsoSomethingElse();";
        CodeQualityContext ctx = tool.analyzeCodeQuality(diff);

        assertEquals(diff, ctx.diff());
        assertEquals(rules, ctx.rules());
        assertFalse(ctx.note().isBlank(), "note should guide the agent");
        verify(factStore).search(anyString(), anyInt(), anyDouble());
    }

    @Test
    void analyzeCodeQuality_emptyDiff_returnsGuidanceNote() {
        CodeQualityContext ctx = tool.analyzeCodeQuality("   ");

        assertEquals("", ctx.diff());
        assertTrue(ctx.rules().isEmpty());
        assertTrue(ctx.note().contains("diff"), "note should mention how to get a diff");
        verify(factStore, never()).search(any(), anyInt(), anyDouble());
    }

    @Test
    void analyzeCodeQuality_nullDiff_returnsGuidanceNote() {
        CodeQualityContext ctx = tool.analyzeCodeQuality(null);

        assertEquals("", ctx.diff());
        verify(factStore, never()).search(any(), anyInt(), anyDouble());
    }

    @Test
    void analyzeCodeQuality_noRulesInMemory_stillReturnsDiff() {
        when(factStore.search(any(), anyInt(), anyDouble())).thenReturn(List.of());

        String diff = "+public void doEverything() { ... }";
        CodeQualityContext ctx = tool.analyzeCodeQuality(diff);

        assertEquals(diff, ctx.diff());
        assertTrue(ctx.rules().isEmpty());
        assertFalse(ctx.note().isBlank());
    }

    @Test
    void analyzeCodeQuality_memorySearchFails_gracefullyReturnsEmptyRules() {
        when(factStore.search(any(), anyInt(), anyDouble())).thenThrow(new RuntimeException("vector store unavailable"));

        CodeQualityContext ctx = tool.analyzeCodeQuality("+int x = 1;");

        assertNotNull(ctx);
        assertTrue(ctx.rules().isEmpty(), "On memory failure, rules should be empty not thrown");
        assertFalse(ctx.diff().isBlank());
    }

    @Test
    void analyzeCodeQuality_storeReceivesCorrectSearchParameters() {
        when(factStore.search(any(), anyInt(), anyDouble())).thenReturn(List.of());

        tool.analyzeCodeQuality("+code");

        verify(factStore).search(
                eq(CodeQualityAnalysisTool.RULES_QUERY),
                eq(CodeQualityAnalysisTool.MAX_RULES),
                eq(CodeQualityAnalysisTool.MIN_SCORE));
    }

    @Test
    void codeQualityContext_isUnmodifiableRecord() {
        CodeQualityContext ctx = new CodeQualityContext("diff", List.of("rule"), "note");

        assertEquals("diff", ctx.diff());
        assertEquals(List.of("rule"), ctx.rules());
        assertEquals("note", ctx.note());
    }
}
