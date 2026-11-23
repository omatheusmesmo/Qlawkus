package dev.omatheusmesmo.qlawkus.messaging.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.approval.ApprovalService;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ApprovalCommandsTest {

    private static final String ID = "0123456789abcdef0123456789abcdef";

    @Test
    void onlyTheApproveAndDenyCommandsWithAnIdAreRecognised() {
        assertTrue(ApprovalCommands.isCommand("/approve " + ID));
        assertTrue(ApprovalCommands.isCommand("  /deny " + ID + "  "));
        assertFalse(ApprovalCommands.isCommand("/approved " + ID));
        assertFalse(ApprovalCommands.isCommand("please approve " + ID));
        assertFalse(ApprovalCommands.isCommand("/verbose"));
    }

    @Test
    void aSenderOutsideTheAllowlistGetsNoDecisionAtAll() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        ApprovalCommands commands = new ApprovalCommands(approvals);

        String reply = commands.handle(message("/approve " + ID), message -> false);

        assertEquals("Not authorized.", reply);
        verifyNoInteractions(approvals);
    }

    @Test
    void anAllowlistedOwnerApprovesAndTheDecisionIsAttributedToTheSender() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        when(approvals.approve(ID, "messaging:telegram:42"))
                .thenReturn(new ApprovalService.Decision(true, null, "Approval accepted."));
        ApprovalCommands commands = new ApprovalCommands(approvals);

        String reply = commands.handle(message("/approve " + ID), message -> true);

        verify(approvals).approve(ID, "messaging:telegram:42");
        assertTrue(reply.contains(ID));
    }

    @Test
    void aDenyDecisionUsesTheDenyPath() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        when(approvals.deny(ID, "messaging:telegram:42"))
                .thenReturn(new ApprovalService.Decision(true, null, "Denied."));
        ApprovalCommands commands = new ApprovalCommands(approvals);

        commands.handle(message("/deny " + ID), message -> true);

        verify(approvals).deny(ID, "messaging:telegram:42");
    }

    @Test
    void aMalformedIdIsRejectedBeforeReachingTheService() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        ApprovalCommands commands = new ApprovalCommands(approvals);

        String reply = commands.handle(message("/approve ../../etc"), message -> true);

        assertTrue(reply.startsWith("Usage"));
        verifyNoInteractions(approvals);
    }

    @Test
    void aRefusedDecisionIsReportedAsItIs() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        when(approvals.approve(anyString(), anyString()))
                .thenReturn(new ApprovalService.Decision(false, null, "Request is already denied."));
        ApprovalCommands commands = new ApprovalCommands(approvals);

        assertEquals("Request is already denied.", commands.handle(message("/approve " + ID), message -> true));
    }

    private static MessagingMessage message(String text) {
        return MessagingMessage.text("telegram", "7", "42", text);
    }
}
