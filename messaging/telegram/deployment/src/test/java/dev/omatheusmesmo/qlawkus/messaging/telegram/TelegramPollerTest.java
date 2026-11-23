package dev.omatheusmesmo.qlawkus.messaging.telegram;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class TelegramPollerTest {

    private final TelegramBotClient botClient = Mockito.mock(TelegramBotClient.class);

    @Test
    void pollOnce_advancesOffsetPastHighestUpdateId() {
        TelegramPoller poller = new TelegramPoller();
        poller.botClient = botClient;
        when(botClient.getUpdates("tok", 0, 25)).thenReturn(
                new TelegramBotClient.GetUpdatesResponse(true,
                        List.of(new TelegramUpdate(5L, null), new TelegramUpdate(7L, null))));

        assertEquals(8L, poller.pollOnce("tok", 0));
    }

    @Test
    void pollOnce_hands_a_button_press_to_the_adapter_and_ignores_empty_updates() {
        TelegramPoller poller = new TelegramPoller();
        TelegramProviderAdapter adapter = Mockito.mock(TelegramProviderAdapter.class);
        poller.botClient = botClient;
        poller.adapter = adapter;
        TelegramUpdate press = new TelegramUpdate(9L, null,
                new TelegramUpdate.TelegramCallbackQuery("cb", null, null, "qa:approve:x"));
        when(adapter.handleCallback(press)).thenReturn(Uni.createFrom().voidItem());
        when(botClient.getUpdates("tok", 0, 25)).thenReturn(
                new TelegramBotClient.GetUpdatesResponse(true, List.of(press, new TelegramUpdate(10L, null))));

        assertEquals(11L, poller.pollOnce("tok", 0));
        Mockito.verify(adapter, Mockito.timeout(2_000)).handleCallback(press);
        Mockito.verifyNoMoreInteractions(adapter);
    }

    @Test
    void pollOnce_emptyBatchKeepsOffset() {
        TelegramPoller poller = new TelegramPoller();
        poller.botClient = botClient;
        when(botClient.getUpdates("tok", 3, 25)).thenReturn(
                new TelegramBotClient.GetUpdatesResponse(true, List.of()));

        assertEquals(3L, poller.pollOnce("tok", 3));
    }
}
