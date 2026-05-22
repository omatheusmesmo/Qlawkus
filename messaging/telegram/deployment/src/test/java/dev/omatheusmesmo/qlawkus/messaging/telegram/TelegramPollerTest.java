package dev.omatheusmesmo.qlawkus.messaging.telegram;

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
    void pollOnce_emptyBatchKeepsOffset() {
        TelegramPoller poller = new TelegramPoller();
        poller.botClient = botClient;
        when(botClient.getUpdates("tok", 3, 25)).thenReturn(
                new TelegramBotClient.GetUpdatesResponse(true, List.of()));

        assertEquals(3L, poller.pollOnce("tok", 3));
    }
}
