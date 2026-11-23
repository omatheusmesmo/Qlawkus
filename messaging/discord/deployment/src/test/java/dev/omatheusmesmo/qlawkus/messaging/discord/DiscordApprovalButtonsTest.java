package dev.omatheusmesmo.qlawkus.messaging.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.approval.ApprovalCallbacks;
import dev.omatheusmesmo.qlawkus.messaging.auth.MessagingAuthService;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DiscordApprovalButtonsTest {

    private static final String ID = "0123456789abcdef0123456789abcdef";

    private MessagingAuthService auth;

    @BeforeEach
    void setUp() {
        auth = mock(MessagingAuthService.class);
    }

    @Test
    void thePromptRowHoldsAnApproveAndADenyButtonWithTheCallbackPayloads() {
        ActionRow row = DiscordApprovalButtons.row(ID);

        List<Button> buttons = row.getChildren().stream().map(Button.class::cast).toList();
        assertEquals(2, buttons.size());
        assertEquals(ApprovalCallbacks.approve(ID), buttons.get(0).getCustomId());
        assertEquals(ApprovalCallbacks.deny(ID), buttons.get(1).getCustomId());
        assertEquals("Approve", buttons.get(0).getLabel().orElseThrow());
        assertEquals("Deny", buttons.get(1).getLabel().orElseThrow());
    }

    @Test
    void anAllowlistedUsersApprovePressIsTheTypedApproveCommandFromThatUser() {
        when(auth.isAuthorized(any())).thenReturn(true);

        DiscordApprovalButtons.Press press = DiscordApprovalButtons.classify(ApprovalCallbacks.approve(ID), "9", "7", auth);

        assertEquals(DiscordApprovalButtons.Outcome.DECIDE, press.outcome());
        MessagingMessage command = press.command();
        assertEquals("/approve " + ID, command.text());
        assertEquals("discord", command.providerId());
        assertEquals("9", command.chatId());
        assertEquals("7", command.userId());
    }

    @Test
    void aDenyPressIsTheTypedDenyCommand() {
        when(auth.isAuthorized(any())).thenReturn(true);

        DiscordApprovalButtons.Press press = DiscordApprovalButtons.classify(ApprovalCallbacks.deny(ID), "9", "7", auth);

        assertEquals("/deny " + ID, press.command().text());
    }

    @Test
    void aPressByAUserOutsideTheAllowlistIsRejectedAndCarriesNoCommand() {
        when(auth.isAuthorized(any())).thenReturn(false);

        DiscordApprovalButtons.Press press = DiscordApprovalButtons.classify(ApprovalCallbacks.approve(ID), "9", "666", auth);

        assertEquals(DiscordApprovalButtons.Outcome.REJECT, press.outcome());
        assertNull(press.command());
    }

    @Test
    void theAllowlistIsAskedAboutThePressingUserNotAboutThePromptsRecipient() {
        when(auth.isAuthorized(any())).thenReturn(true);

        DiscordApprovalButtons.classify(ApprovalCallbacks.approve(ID), "9", "7", auth);

        ArgumentCaptor<MessagingMessage> asked = ArgumentCaptor.forClass(MessagingMessage.class);
        verify(auth).isAuthorized(asked.capture());
        assertEquals("7", asked.getValue().userId());
        assertEquals("discord", asked.getValue().providerId());
    }

    @Test
    void aButtonThisFeatureDidNotCreateOrAForgedPayloadIsIgnoredWithoutAskingTheAllowlist() {
        when(auth.isAuthorized(any())).thenReturn(true);

        assertEquals(DiscordApprovalButtons.Outcome.IGNORE,
                DiscordApprovalButtons.classify("poll:vote:1", "9", "7", auth).outcome());
        assertEquals(DiscordApprovalButtons.Outcome.IGNORE,
                DiscordApprovalButtons.classify("qa:approve:forged", "9", "7", auth).outcome());
        verify(auth, never()).isAuthorized(any());
    }
}
