package dev.omatheusmesmo.qlawkus.it.console;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.LocalDate;
import java.util.List;

/**
 * An episodic store whose every read fails, enabled only by {@link OverviewErrorStateTest}'s
 * profile, so a card can be shown what an unreadable source looks like.
 */
@Alternative
@ApplicationScoped
public class FailingEpisodicStore implements EpisodicStore {

    private static final String BROKEN = "the journal volume is unreadable";

    @Override
    public boolean existsForDate(LocalDate date) {
        throw new IllegalStateException(BROKEN);
    }

    @Override
    public void storeEpisode(LocalDate date, String summary, int messageCount) {
        throw new IllegalStateException(BROKEN);
    }

    @Override
    public List<JournalSummary> listJournals() {
        throw new IllegalStateException(BROKEN);
    }

    @Override
    public long count() {
        throw new IllegalStateException(BROKEN);
    }

    @Override
    public long purgeAll() {
        throw new IllegalStateException(BROKEN);
    }

    @Override
    public void verifyWritable() {
    }
}
