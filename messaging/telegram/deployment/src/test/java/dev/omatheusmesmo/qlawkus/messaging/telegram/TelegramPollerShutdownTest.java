package dev.omatheusmesmo.qlawkus.messaging.telegram;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The drain contract for the poll thread. The old {@code @Observes ShutdownEvent} interrupted the
 * thread and returned, so nothing ever established that the thread had actually stopped; these
 * tests pin the waiting half, which is what makes the shutdown listener meaningful.
 */
class TelegramPollerShutdownTest {

    private final TelegramBotClient botClient = Mockito.mock(TelegramBotClient.class);

    /** A poller that never started has nothing to wait for and must not block the shutdown phase. */
    @Test
    void awaitStop_returnsImmediatelyWhenPollingNeverStarted() {
        TelegramPoller poller = new TelegramPoller();

        assertTrue(poller.awaitStop(Duration.ofSeconds(1)));
    }

    /** The running loop must be observed to finish, not merely asked to. */
    @Test
    void requestStop_endsTheLoopAndAwaitStopObservesIt() throws Exception {
        CountDownLatch polling = new CountDownLatch(1);
        when(botClient.getUpdates(anyString(), anyLong(), anyInt())).thenAnswer(invocation -> {
            polling.countDown();
            Thread.sleep(20);
            return new TelegramBotClient.GetUpdatesResponse(true, List.of());
        });

        TelegramPoller poller = startedPoller();
        assertTrue(polling.await(2, TimeUnit.SECONDS), "the poll loop should have issued a request");

        poller.requestStop();

        assertTrue(poller.awaitStop(Duration.ofSeconds(5)), "the poll thread should have finished");
    }

    /** A thread wedged past the budget reports failure, so the drain is logged instead of assumed. */
    @Test
    void awaitStop_reportsFailureWhenTheThreadOutlivesTheBudget() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch polling = new CountDownLatch(1);
        when(botClient.getUpdates(anyString(), anyLong(), anyInt())).thenAnswer(invocation -> {
            polling.countDown();
            release.await();
            return new TelegramBotClient.GetUpdatesResponse(true, List.of());
        });

        TelegramPoller poller = startedPoller();
        assertTrue(polling.await(2, TimeUnit.SECONDS));

        try {
            assertFalse(poller.awaitStop(Duration.ofMillis(200)),
                    "a thread still inside getUpdates must not be reported as drained");
        } finally {
            release.countDown();
            poller.requestStop();
        }
    }

    private TelegramPoller startedPoller() {
        TelegramPoller poller = new TelegramPoller();
        poller.botClient = botClient;
        poller.config = pollingConfig();
        poller.onStart(null);
        return poller;
    }

    private TelegramConfig pollingConfig() {
        TelegramConfig config = Mockito.mock(TelegramConfig.class);
        when(config.mode()).thenReturn("polling");
        when(config.botToken()).thenReturn(Optional.of("tok"));
        return config;
    }
}
