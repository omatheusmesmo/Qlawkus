package dev.omatheusmesmo.qlawkus.it.toolaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.deployment.ToolAccessReport;
import dev.omatheusmesmo.qlawkus.deployment.ToolAccessScanner;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import dev.omatheusmesmo.qlawkus.tool.access.AccessSource;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccess;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;

class ToolAccessScannerTest {

    @QlawTool
    static class Inferred {
        @Tool("a") public String listThings() { return ""; }
        @Tool("b") public String deleteThing() { return ""; }
        @Tool("c") public String sendThing() { return ""; }
    }

    @QlawTool
    static class FallsBack {
        @Tool("a") public String probeThing() { return ""; }
    }

    @QlawTool
    static class MethodOverride {
        @ToolAccess(AccessClass.READ)
        @Tool("a") public String checkThing() { return ""; }
        @ToolAccess(AccessClass.UPDATE)
        @Tool("b") public String listThing() { return ""; }
    }

    @QlawTool
    @ToolAccess(AccessClass.READ)
    static class BeanLevel {
        @Tool("a") public String checkOne() { return ""; }
        @Tool("b") public String showTwo() { return ""; }
        @ToolAccess(AccessClass.DELETE)
        @Tool("c") public String dropThree() { return ""; }
    }

    @QlawTool
    static class Named {
        @Tool(name = "renamedTool", value = "a") public String listInternal() { return ""; }
    }

    static class ParentTools {
        @Tool("a") public String getInherited() { return ""; }
    }

    @QlawTool
    static class Child extends ParentTools {
        @Tool("b") public String removeOwn() { return ""; }
    }

    static class NotATool {
        @Tool("a") public String sendHidden() { return ""; }
    }

    @QlawTool
    static class NoToolMethods {
        public String sendNothing() { return ""; }
    }

    @QlawTool
    static class DuplicateOne {
        @Tool("a") public String listSame() { return ""; }
    }

    @QlawTool
    static class DuplicateTwo {
        @Tool("a") public String listSame() { return ""; }
    }

    private static Index index(Class<?>... classes) {
        try {
            return Index.of(classes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, ToolAccessEntry> scan(Class<?>... classes) {
        return ToolAccessScanner.scan(index(classes)).stream()
                .collect(Collectors.toMap(ToolAccessEntry::tool, Function.identity()));
    }

    @Test
    void theVerbOfTheMethodNameIsTheInferredClass() {
        var found = scan(Inferred.class);
        assertEquals(AccessClass.READ, found.get("listThings").accessClass());
        assertEquals(AccessClass.DELETE, found.get("deleteThing").accessClass());
        assertEquals(AccessClass.SEND, found.get("sendThing").accessClass());
        assertTrue(found.values().stream().allMatch(entry -> entry.source() == AccessSource.INFERRED));
    }

    @Test
    void aMethodWithNoRecognisedVerbIsExecuteAndSaysItFellBack() {
        var entry = scan(FallsBack.class).get("probeThing");
        assertEquals(AccessClass.EXECUTE, entry.accessClass());
        assertEquals(AccessSource.FALLBACK, entry.source());
    }

    @Test
    void aMethodAnnotationBeatsTheVerbEitherWay() {
        var found = scan(MethodOverride.class);
        assertEquals(AccessClass.READ, found.get("checkThing").accessClass());
        assertEquals(AccessSource.DECLARED, found.get("checkThing").source());
        assertEquals(AccessClass.UPDATE, found.get("listThing").accessClass());
        assertEquals(AccessSource.DECLARED, found.get("listThing").source());
    }

    @Test
    void aBeanAnnotationCoversEveryMethodAndAMethodAnnotationBeatsIt() {
        var found = scan(BeanLevel.class);
        assertEquals(AccessClass.READ, found.get("checkOne").accessClass());
        assertEquals(AccessClass.READ, found.get("showTwo").accessClass());
        assertEquals(AccessClass.DELETE, found.get("dropThree").accessClass());
        assertTrue(found.values().stream().allMatch(entry -> entry.source() == AccessSource.DECLARED));
    }

    @Test
    void theToolNameIsTheAnnotationNameWhenGiven() {
        var found = scan(Named.class);
        assertTrue(found.containsKey("renamedTool"));
        assertFalse(found.containsKey("listInternal"));
        assertEquals(AccessClass.READ, found.get("renamedTool").accessClass());
    }

    @Test
    void toolsInheritedFromASuperclassAreClassified() {
        var found = scan(Child.class, ParentTools.class);
        assertEquals(AccessClass.READ, found.get("getInherited").accessClass());
        assertEquals(AccessClass.DELETE, found.get("removeOwn").accessClass());
    }

    @Test
    void onlyQlawToolBeansAreScanned() {
        assertTrue(scan(NotATool.class, NoToolMethods.class).isEmpty());
    }

    @Test
    void theOriginNamesTheBeanAndTheMethod() {
        assertEquals(Inferred.class.getName() + "#listThings", scan(Inferred.class).get("listThings").origin());
    }

    @Test
    void twoBeansDeclaringTheSameToolNameFailTheBuildNamingBoth() {
        var failure = assertThrows(IllegalStateException.class, () -> scan(DuplicateOne.class, DuplicateTwo.class));
        assertTrue(failure.getMessage().contains("listSame"));
        assertTrue(failure.getMessage().contains(DuplicateOne.class.getName()));
        assertTrue(failure.getMessage().contains(DuplicateTwo.class.getName()));
    }

    @Test
    void theReportListsToolClassSourceAndOriginSortedByTool() {
        List<ToolAccessEntry> entries = ToolAccessScanner.scan(index(Inferred.class, FallsBack.class));
        String report = ToolAccessReport.render(entries);
        List<String> lines = report.lines().toList();
        assertEquals("tool\tclass\tsource\torigin", lines.get(0));
        assertEquals("deleteThing\tDELETE\tINFERRED\t" + Inferred.class.getName() + "#deleteThing", lines.get(1));
        assertEquals("probeThing\tEXECUTE\tFALLBACK\t" + FallsBack.class.getName() + "#probeThing", lines.get(3));
        assertEquals(5, lines.size());
    }
}
