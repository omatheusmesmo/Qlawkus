package dev.omatheusmesmo.qlawkus.permission;

import static dev.omatheusmesmo.qlawkus.permission.PermissionLevel.ALLOW;
import static dev.omatheusmesmo.qlawkus.permission.PermissionLevel.ASK;
import static dev.omatheusmesmo.qlawkus.permission.PermissionLevel.DENY;
import static dev.omatheusmesmo.qlawkus.tool.access.AccessClass.DELETE;
import static dev.omatheusmesmo.qlawkus.tool.access.AccessClass.READ;
import static dev.omatheusmesmo.qlawkus.tool.access.AccessClass.SEND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import dev.omatheusmesmo.qlawkus.tool.access.AccessSource;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessEntry;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionMatrixTest {

    private static final ToolAccessRegistry REGISTRY = new ToolAccessRegistry(List.of(
            entry("deleteEvent", DELETE, "tools.google.calendar.CalendarTool"),
            entry("listEvents", READ, "tools.google.calendar.CalendarTool"),
            entry("trashFile", DELETE, "tools.google.drive.DriveTool"),
            entry("sendEmail", SEND, "tools.google.gmail.GmailTool")));

    private static ToolAccessEntry entry(String tool, AccessClass accessClass,
            String bean) {
        return new ToolAccessEntry(tool, accessClass, AccessSource.INFERRED,
                "dev.omatheusmesmo.qlawkus." + bean + "#" + tool);
    }

    private static PermissionMatrix matrix(PermissionState configured, PermissionState runtime,
            PermissionDefaults... extensions) {
        return new PermissionMatrix(configured, runtime, List.of(extensions), REGISTRY);
    }

    @Test
    void withNothingConfiguredEveryToolIsAllowed() {
        PermissionMatrix matrix = matrix(PermissionState.empty(), PermissionState.empty());

        assertEquals(ALLOW, matrix.resolve("deleteEvent").level());
        assertEquals(ALLOW, matrix.resolve("sendEmail").level());
        assertEquals(PermissionMatrix.Layer.BUILT_IN, matrix.resolve("sendEmail").decision().layer());
    }

    @Test
    void anUnknownToolIsExecuteInTheExternalGroup() {
        PermissionMatrix matrix = matrix(PermissionState.empty().withDefault(AccessClass.EXECUTE, DENY),
                PermissionState.empty());

        PermissionMatrix.ToolResolution resolution = matrix.resolve("somePluginTool");

        assertEquals("external", resolution.group());
        assertEquals(AccessClass.EXECUTE, resolution.accessClass());
        assertEquals(DENY, resolution.level());
    }

    @Test
    void theOperationDefaultAppliesToEveryGroup() {
        PermissionState configured = PermissionState.empty().withDefault(DELETE, ASK);
        PermissionMatrix matrix = matrix(configured, PermissionState.empty());

        assertEquals(ASK, matrix.resolve("deleteEvent").level());
        assertEquals(ASK, matrix.resolve("trashFile").level());
        assertEquals(ALLOW, matrix.resolve("listEvents").level());
    }

    @Test
    void aGroupCellBeatsTheOperationDefault() {
        PermissionState configured = PermissionState.empty()
                .withDefault(DELETE, ASK)
                .withCell("calendar", DELETE, DENY);
        PermissionMatrix matrix = matrix(configured, PermissionState.empty());

        assertEquals(DENY, matrix.resolve("deleteEvent").level());
        assertEquals(PermissionMatrix.Layer.CELL_CONFIG, matrix.resolve("deleteEvent").decision().layer());
        assertEquals(ASK, matrix.resolve("trashFile").level(), "another group keeps the default");
    }

    @Test
    void anExactToolBeatsItsGroupCell() {
        PermissionState configured = PermissionState.empty()
                .withCell("calendar", DELETE, DENY)
                .withTool("deleteEvent", ASK);
        PermissionMatrix matrix = matrix(configured, PermissionState.empty());

        assertEquals(ASK, matrix.resolve("deleteEvent").level());
        assertEquals(PermissionMatrix.Layer.TOOL_CONFIG, matrix.resolve("deleteEvent").decision().layer());
    }

    @Test
    void aRuntimeChangeBeatsTheConfigurationAtTheSameSpecificity() {
        PermissionState configured = PermissionState.empty().withCell("calendar", DELETE, DENY);
        PermissionState runtime = PermissionState.empty().withCell("calendar", DELETE, ASK);
        PermissionMatrix matrix = matrix(configured, runtime);

        assertEquals(ASK, matrix.resolve("deleteEvent").level());
        assertEquals(PermissionMatrix.Layer.CELL_RUNTIME, matrix.resolve("deleteEvent").decision().layer());
    }

    @Test
    void aMoreSpecificConfiguredLayerStillBeatsALessSpecificRuntimeOne() {
        PermissionState configured = PermissionState.empty().withCell("calendar", DELETE, DENY);
        PermissionState runtime = PermissionState.empty().withDefault(DELETE, ALLOW);
        PermissionMatrix matrix = matrix(configured, runtime);

        assertEquals(DENY, matrix.resolve("deleteEvent").level());
    }

    @Test
    void anExtensionDefaultAppliesOnlyWhereTheOperationDefaultIsNotSet() {
        PermissionDefaults calendar = () -> List.of(new PermissionDefaults.Cell("calendar", DELETE, ASK));

        PermissionMatrix untouched = matrix(PermissionState.empty(), PermissionState.empty(), calendar);
        assertEquals(ASK, untouched.resolve("deleteEvent").level());
        assertEquals(PermissionMatrix.Layer.EXTENSION, untouched.resolve("deleteEvent").decision().layer());
        assertEquals(ALLOW, untouched.resolve("trashFile").level());

        PermissionMatrix explicit = matrix(PermissionState.empty().withDefault(DELETE, ALLOW),
                PermissionState.empty(), calendar);
        assertEquals(ALLOW, explicit.resolve("deleteEvent").level(),
                "an operation default the operator set wins over what an extension suggests");
    }

    @Test
    void aGroupCellBeatsAnExtensionDefault() {
        PermissionDefaults calendar = () -> List.of(new PermissionDefaults.Cell("calendar", DELETE, ASK));
        PermissionMatrix matrix = matrix(PermissionState.empty().withCell("calendar", DELETE, ALLOW),
                PermissionState.empty(), calendar);

        assertEquals(ALLOW, matrix.resolve("deleteEvent").level());
    }

    @Test
    void presetsFillTheOperationDefaults() {
        PermissionState runtime = PermissionState.empty().withPreset("readonly", PermissionPresets.find("readonly").orElseThrow());
        PermissionMatrix matrix = matrix(PermissionState.empty(), runtime);

        assertEquals(ALLOW, matrix.resolve("listEvents").level());
        assertEquals(DENY, matrix.resolve("deleteEvent").level());
        assertEquals(DENY, matrix.resolve("sendEmail").level());
    }

    @Test
    void aTightenedRuntimeStateReportsWhyEachToolIsDecided() {
        PermissionState runtime = PermissionState.empty().withCell("calendar", DELETE, DENY);
        PermissionMatrix matrix = matrix(PermissionState.empty(), runtime);

        String basis = matrix.resolve("deleteEvent").decision().basis();

        assertTrue(basis.contains("calendar") && basis.contains("delete"), basis);
    }

    @Test
    void knownGroupsComeFromTheRegistryAndTheExtensionDefaults() {
        PermissionDefaults extra = () -> List.of(new PermissionDefaults.Cell("notes", DELETE, ASK));
        PermissionMatrix matrix = matrix(PermissionState.empty(), PermissionState.empty(), extra);

        assertTrue(matrix.groups().containsAll(List.of("calendar", "drive", "gmail", "notes", "external")),
                matrix.groups().toString());
    }
}
