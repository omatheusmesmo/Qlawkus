package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consolidator writes the journal and embeds it as two steps, and the guard that skips an
 * already-consolidated day reads only the first. Anything that lands between them - a SIGTERM
 * during a rollout, or an embed failure that only warned - leaves a journal no retrieval can
 * reach, and the guard then skips that day for good.
 */
class EpisodicConsolidatorIdempotencyTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);
    private static final String SUMMARY = "The owner shipped the telemetry work.";

    private EpisodicConsolidatorJob job;
    private EpisodicStore episodicStore;
    private FactStore factStore;
    private WorkingMemoryStore workingMemoryStore;
    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        job = new EpisodicConsolidatorJob();
        episodicStore = Mockito.mock(EpisodicStore.class);
        factStore = Mockito.mock(FactStore.class);
        workingMemoryStore = Mockito.mock(WorkingMemoryStore.class);
        chatModel = Mockito.mock(ChatModel.class);

        job.episodicStore = episodicStore;
        job.factStore = factStore;
        job.workingMemoryStore = workingMemoryStore;
        job.chatModel = chatModel;
        job.meters = AgentMeters.disabled();
    }

    /** The repair: a journal left behind without its embedding is embedded on the next run. */
    @Test
    void journalWrittenWithoutItsEmbeddingIsEmbeddedOnTheNextRun() {
        when(episodicStore.existsForDate(DATE)).thenReturn(true);
        when(episodicStore.listJournals()).thenReturn(List.of(journal(DATE, SUMMARY)));

        job.consolidateDate(DATE);

        ArgumentCaptor<String> embedded = ArgumentCaptor.forClass(String.class);
        verify(factStore).store(embedded.capture(), any());
        assertEquals(SUMMARY, embedded.getValue());
    }

    /** Repairing must not re-summarize: that would spend an LLM call to rebuild what already exists. */
    @Test
    void repairingAnExistingJournalDoesNotCallTheModelAgain() {
        when(episodicStore.existsForDate(DATE)).thenReturn(true);
        when(episodicStore.listJournals()).thenReturn(List.of(journal(DATE, SUMMARY)));

        job.consolidateDate(DATE);

        verify(chatModel, never()).chat(anyString());
        verify(episodicStore, never()).storeEpisode(any(), anyString(), anyInt());
    }

    /** The embed carries the same source and date tags whether it is the first write or a repair. */
    @Test
    void repairedEmbeddingKeepsTheEpisodicSourceAndDate() {
        when(episodicStore.existsForDate(DATE)).thenReturn(true);
        when(episodicStore.listJournals()).thenReturn(List.of(journal(DATE, SUMMARY)));

        job.consolidateDate(DATE);

        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(factStore).store(anyString(), metadata.capture());
        assertEquals(MemorySource.EPISODIC_CONSOLIDATOR.value(), metadata.getValue().get("source"));
        assertEquals(DATE.toString(), metadata.getValue().get("date"));
    }

    /** A day with no journal for it still takes the normal path: summarize, store, embed. */
    @Test
    void aDayWithoutAJournalIsConsolidatedNormally() {
        when(episodicStore.existsForDate(DATE)).thenReturn(false);
        when(workingMemoryStore.findByDateRange(DATE)).thenReturn(List.<ChatMessage>of(UserMessage.from("hi")));
        when(chatModel.chat(anyString())).thenReturn(SUMMARY);

        job.consolidateDate(DATE);

        verify(episodicStore).storeEpisode(DATE, SUMMARY, 1);
        verify(factStore, times(1)).store(anyString(), any());
    }

    /** A journal the store does not list cannot be repaired, and must not blow up the run. */
    @Test
    void anExistingJournalThatCannotBeReadIsSkippedQuietly() {
        when(episodicStore.existsForDate(DATE)).thenReturn(true);
        when(episodicStore.listJournals()).thenReturn(List.of());

        job.consolidateDate(DATE);

        verify(factStore, never()).store(anyString(), any());
    }

    private static JournalSummary journal(LocalDate date, String summary) {
        return new JournalSummary(1L, date, summary, 12, Instant.now());
    }
}
