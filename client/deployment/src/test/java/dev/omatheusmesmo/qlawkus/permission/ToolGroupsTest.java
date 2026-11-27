package dev.omatheusmesmo.qlawkus.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.omatheusmesmo.qlawkus.tool.access.ToolGroups;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ToolGroupsTest {

    @ParameterizedTest
    @CsvSource({
            "dev.omatheusmesmo.qlawkus.tools.google.calendar.CalendarTool#createEvent, calendar",
            "dev.omatheusmesmo.qlawkus.tools.google.drive.DriveTool#trashFile, drive",
            "dev.omatheusmesmo.qlawkus.tools.google.gmail.GmailTool#sendEmail, gmail",
            "dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuthTool#mcpAuthorizationStatus, mcp",
            "dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubTool#installSkill, skillhub",
            "dev.omatheusmesmo.qlawkus.tool.shell.ShellTool#runCommand, shell",
            "dev.omatheusmesmo.qlawkus.tool.shell.FileTool#deleteFile, shell",
            "dev.omatheusmesmo.qlawkus.task.TaskSchedulerTool#scheduleTask, task",
            "dev.omatheusmesmo.qlawkus.heartbeat.HeartbeatTool#heartbeatStatus, heartbeat",
            "dev.omatheusmesmo.qlawkus.RootTool#thing, core",
            "org.example.plugin.billing.BillingTool#charge, billing"
    })
    void theGroupIsTheExtensionPackageWithoutTheToolsAndGoogleSegments(String origin, String group) {
        assertEquals(group, ToolGroups.fromOrigin(origin));
    }

    @ParameterizedTest
    @CsvSource({"''", "no-package#method"})
    void anOriginWithoutAPackageFallsIntoTheExternalGroup(String origin) {
        assertEquals(ToolGroups.EXTERNAL, ToolGroups.fromOrigin(origin));
    }
}
