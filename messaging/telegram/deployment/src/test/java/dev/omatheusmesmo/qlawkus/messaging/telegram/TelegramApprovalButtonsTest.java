package dev.omatheusmesmo.qlawkus.messaging.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import dev.omatheusmesmo.qlawkus.messaging.approval.ApprovalCallbacks;
import dev.omatheusmesmo.qlawkus.messaging.auth.MessagingAuthService;
import io.smallrye.mutiny.Uni;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TelegramApprovalButtonsTest {

    private static final String ID = "0123456789abcdef0123456789abcdef";
    private static final String TOKEN = "123:token";

    private TelegramProviderAdapter adapter;
    private TelegramBotClient botClient;
    private MessagingOrchestrator orchestrator;
    private MessagingAuthService authService;

    @BeforeEach
    void setUp() {
        adapter = new TelegramProviderAdapter();
        botClient = mock(TelegramBotClient.class);
        orchestrator = mock(MessagingOrchestrator.class);
        authService = mock(MessagingAuthService.class);
        TelegramConfig config = mock(TelegramConfig.class);
        adapter.botClient = botClient;
        adapter.orchestrator = orchestrator;
        adapter.authService = authService;
        adapter.config = config;
        when(config.botToken()).thenReturn(Optional.of(TOKEN));
        when(orchestrator.process(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void thePromptCarriesAnApproveAndADenyButtonAndIsSentAsPlainText() {
        Boolean delivered = adapter.sendApprovalPrompt("42", ID, "Approval needed (id)").await().indefinitely();

        assertTrue(delivered);
        ArgumentCaptor<TelegramBotClient.SendMessageRequest> sent =
                ArgumentCaptor.forClass(TelegramBotClient.SendMessageRequest.class);
        verify(botClient).sendMessage(eq(TOKEN), sent.capture());
        TelegramBotClient.SendMessageRequest request = sent.getValue();
        assertEquals("42", request.chatId());
        assertEquals("Approval needed (id)", request.text());
        assertNull(request.parseMode(), "the notice is not MarkdownV2, so it must not be parsed as such");
        List<TelegramBotClient.InlineKeyboardButton> row = request.replyMarkup().inlineKeyboard().get(0);
        assertEquals(ApprovalCallbacks.approve(ID), row.get(0).callbackData());
        assertEquals(ApprovalCallbacks.deny(ID), row.get(1).callbackData());
    }

    @Test
    void withoutABotTokenThereAreNoButtonsToOffer() {
        when(adapter.config.botToken()).thenReturn(Optional.empty());

        assertEquals(false, adapter.sendApprovalPrompt("42", ID, "Approval needed").await().indefinitely());
        verifyNoInteractions(botClient);
    }

    @Test
    void theOwnersPressRunsThePlainApproveCommandAndRemovesTheButtons() {
        when(authService.isAuthorized(any())).thenReturn(true);

        adapter.handleCallback(press("7", "42", 900L, ApprovalCallbacks.approve(ID))).await().indefinitely();

        ArgumentCaptor<MessagingMessage> processed = ArgumentCaptor.forClass(MessagingMessage.class);
        verify(orchestrator).process(processed.capture());
        assertEquals("/approve " + ID, processed.getValue().text());
        assertEquals("7", processed.getValue().userId());
        assertEquals("42", processed.getValue().chatId());
        verify(botClient).answerCallbackQuery(eq(TOKEN), any());
        ArgumentCaptor<TelegramBotClient.EditMessageReplyMarkupRequest> edit =
                ArgumentCaptor.forClass(TelegramBotClient.EditMessageReplyMarkupRequest.class);
        verify(botClient).editMessageReplyMarkup(eq(TOKEN), edit.capture());
        assertEquals(900L, edit.getValue().messageId());
        assertTrue(edit.getValue().replyMarkup().inlineKeyboard().isEmpty());
    }

    @Test
    void aDenyPressRunsTheDenyCommand() {
        when(authService.isAuthorized(any())).thenReturn(true);

        adapter.handleCallback(press("7", "42", 900L, ApprovalCallbacks.deny(ID))).await().indefinitely();

        ArgumentCaptor<MessagingMessage> processed = ArgumentCaptor.forClass(MessagingMessage.class);
        verify(orchestrator).process(processed.capture());
        assertEquals("/deny " + ID, processed.getValue().text());
    }

    @Test
    void aPressByANonOwnerDecidesNothingAndLeavesTheButtonsForTheOwner() {
        when(authService.isAuthorized(any())).thenReturn(false);

        adapter.handleCallback(press("666", "42", 900L, ApprovalCallbacks.approve(ID))).await().indefinitely();

        verify(orchestrator, never()).process(any());
        verify(botClient, never()).editMessageReplyMarkup(any(), any());
        ArgumentCaptor<TelegramBotClient.AnswerCallbackQueryRequest> answer =
                ArgumentCaptor.forClass(TelegramBotClient.AnswerCallbackQueryRequest.class);
        verify(botClient).answerCallbackQuery(eq(TOKEN), answer.capture());
        assertEquals("cb-1", answer.getValue().callbackQueryId());
    }

    @Test
    void aForgedPayloadIsAcknowledgedAndNeverProcessed() {
        when(authService.isAuthorized(any())).thenReturn(true);

        adapter.handleCallback(press("7", "42", 900L, "qa:approve:not-a-real-id")).await().indefinitely();
        adapter.handleCallback(press("7", "42", 900L, "something-else")).await().indefinitely();

        verify(orchestrator, never()).process(any());
        verify(botClient, never()).editMessageReplyMarkup(any(), any());
    }

    @Test
    void aFailedDecisionKeepsTheButtonsSoTheOwnerCanTryAgain() {
        when(authService.isAuthorized(any())).thenReturn(true);
        when(orchestrator.process(any())).thenReturn(Uni.createFrom().failure(new IllegalStateException("down")));

        adapter.handleCallback(press("7", "42", 900L, ApprovalCallbacks.approve(ID))).await().indefinitely();

        verify(botClient, never()).editMessageReplyMarkup(any(), any());
        verify(botClient).answerCallbackQuery(eq(TOKEN), any());
    }

    @Test
    void aPressWithNoOriginatingMessageIsIgnored() {
        when(authService.isAuthorized(any())).thenReturn(true);
        TelegramUpdate update = new TelegramUpdate(1L, null, new TelegramUpdate.TelegramCallbackQuery("cb-1",
                new TelegramUpdate.TelegramUser(7L, "Owner", null), null, ApprovalCallbacks.approve(ID)));

        adapter.handleCallback(update).await().indefinitely();

        verify(orchestrator, never()).process(any());
    }

    private static TelegramUpdate press(String userId, String chatId, long messageId, String data) {
        TelegramUpdate.TelegramMessage origin = new TelegramUpdate.TelegramMessage(messageId,
                new TelegramUpdate.TelegramUser(1L, "bot", null),
                new TelegramUpdate.TelegramChat(Long.parseLong(chatId), "private"), "Approval needed", null);
        return new TelegramUpdate(1L, null, new TelegramUpdate.TelegramCallbackQuery("cb-1",
                new TelegramUpdate.TelegramUser(Long.parseLong(userId), "Someone", null), origin, data));
    }
}
