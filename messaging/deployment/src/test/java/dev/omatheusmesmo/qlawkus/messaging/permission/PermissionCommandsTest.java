package dev.omatheusmesmo.qlawkus.messaging.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.permission.PermissionLevel;
import dev.omatheusmesmo.qlawkus.permission.PermissionOverview;
import dev.omatheusmesmo.qlawkus.permission.PermissionService;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PermissionCommandsTest {

    private PermissionService permissions;
    private PermissionCommands commands;

    @BeforeEach
    void setUp() {
        permissions = Mockito.mock(PermissionService.class);
        commands = new PermissionCommands(permissions);
    }

    @Test
    void onlyThePermissionsCommandIsRecognised() {
        assertTrue(PermissionCommands.isCommand("/permissions"));
        assertTrue(PermissionCommands.isCommand("  /Permissions calendar delete ask "));
        assertFalse(PermissionCommands.isCommand("/permissionsx"));
        assertFalse(PermissionCommands.isCommand("show me /permissions"));
        assertFalse(PermissionCommands.isCommand("/approve 0123456789abcdef0123456789abcdef"));
        assertFalse(PermissionCommands.isCommand(null));
    }

    @Test
    void aSenderOutsideTheAllowlistReachesNothing() {
        String reply = commands.handle(message("/permissions calendar delete allow"), message -> false);

        assertEquals("Not authorized.", reply);
        verifyNoInteractions(permissions);
    }

    @Test
    void settingACellPassesTheSendersChatIdentityAndNeverAnAdminOne() {
        when(permissions.set(any(), any(), any(), any(), any()))
                .thenReturn(new PermissionService.Outcome(PermissionService.Status.APPLIED,
                        "calendar delete: allow -> ask", null));

        String reply = commands.handle(message("/permissions calendar delete ask"), message -> true);

        ArgumentCaptor<PermissionService.Actor> actor = ArgumentCaptor.forClass(PermissionService.Actor.class);
        verify(permissions).set(eq("calendar"), eq("delete"), eq("ask"), actor.capture(),
                eq(new AgentDeliveryContext.Snapshot(null, "telegram", "7")));
        assertEquals("messaging:telegram:42", actor.getValue().name());
        assertEquals("telegram", actor.getValue().channel());
        assertFalse(actor.getValue().mayLoosen(), "a chat message can never loosen on its own authority");
        assertTrue(reply.contains("calendar delete: allow -> ask"), reply);
    }

    @Test
    void aLooseningThatIsPendingTellsTheSenderWhatToDoNext() {
        String id = "0123456789abcdef0123456789abcdef";
        when(permissions.set(any(), any(), any(), any(), any()))
                .thenReturn(new PermissionService.Outcome(PermissionService.Status.PENDING,
                        "calendar delete: deny -> allow needs the owner's approval", id));

        String reply = commands.handle(message("/permissions calendar delete allow"), message -> true);

        assertTrue(reply.contains(id), reply);
        assertTrue(reply.contains("/approve"), reply);
    }

    @Test
    void theDefaultRowAndToolExceptionsAndPresetsRouteToTheirOperations() {
        PermissionService.Outcome done = new PermissionService.Outcome(PermissionService.Status.APPLIED, "ok", null);
        when(permissions.set(any(), any(), any(), any(), any())).thenReturn(done);
        when(permissions.setTool(any(), any(), any(), any())).thenReturn(done);
        when(permissions.applyPreset(any(), any(), any())).thenReturn(done);

        commands.handle(message("/permissions default send ask"), message -> true);
        commands.handle(message("/permissions tool deleteEvent deny"), message -> true);
        commands.handle(message("/permissions preset readonly"), message -> true);

        verify(permissions).set(eq("default"), eq("send"), eq("ask"), any(), any());
        verify(permissions).setTool(eq("deleteEvent"), eq("deny"), any(), any());
        verify(permissions).applyPreset(eq("readonly"), any(), any());
    }

    @Test
    void explainPrintsTheLevelTheGroupAndTheReasons() {
        when(permissions.explain("deleteEvent")).thenReturn(new PermissionService.Explanation("deleteEvent",
                "calendar", AccessClass.DELETE, PermissionLevel.ASK,
                List.of("the calendar delete cell, set at runtime")));

        String reply = commands.handle(message("/permissions explain deleteEvent"), message -> true);

        assertTrue(reply.contains("deleteEvent"), reply);
        assertTrue(reply.contains("ask"), reply);
        assertTrue(reply.contains("calendar"), reply);
        assertTrue(reply.contains("the calendar delete cell, set at runtime"), reply);
    }

    @Test
    void withoutArgumentsItShowsTheMatrixOneLinePerGroup() {
        PermissionOverview.Cell allow = new PermissionOverview.Cell(PermissionLevel.ALLOW, "default", 2);
        PermissionOverview.Cell ask = new PermissionOverview.Cell(PermissionLevel.ASK, "preset", 1);
        Map<AccessClass, PermissionOverview.Cell> cells = new EnumMap<>(AccessClass.class);
        for (AccessClass accessClass : AccessClass.values()) {
            cells.put(accessClass, accessClass == AccessClass.DELETE ? ask : allow);
        }
        when(permissions.overview()).thenReturn(new PermissionOverview("ask-writes", cells,
                List.of(new PermissionOverview.Group("calendar", cells)), Map.of()));

        String reply = commands.handle(message("/permissions"), message -> true);

        assertTrue(reply.contains("ask-writes"), reply);
        assertTrue(reply.contains("calendar"), reply);
        assertTrue(reply.contains("delete ask"), reply);
        assertTrue(reply.contains("read allow"), reply);
    }

    @Test
    void aMalformedCommandGetsTheUsageAndTouchesNothing() {
        String reply = commands.handle(message("/permissions calendar delete"), message -> true);

        assertTrue(reply.startsWith("Usage"), reply);
        verifyNoInteractions(permissions);
    }

    private static MessagingMessage message(String text) {
        return MessagingMessage.text("telegram", "7", "42", text);
    }
}
