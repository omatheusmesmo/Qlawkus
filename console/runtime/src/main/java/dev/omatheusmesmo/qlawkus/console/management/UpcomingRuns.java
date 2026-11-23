package dev.omatheusmesmo.qlawkus.console.management;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Picks what the Upcoming card shows from every run that has a next fire time: the earliest few,
 * soonest first. Runs with no next fire (a job the scheduler dropped, a finished one-shot) never
 * reach it.
 */
final class UpcomingRuns {

    static final int LIMIT = 3;

    private UpcomingRuns() {
    }

    static List<UpcomingRun> next(Collection<UpcomingRun> candidates) {
        return candidates.stream()
                .filter(run -> run.at() != null)
                .sorted(Comparator.comparing(UpcomingRun::at).thenComparing(UpcomingRun::label))
                .limit(LIMIT)
                .toList();
    }
}
