package dev.omatheusmesmo.qlawkus.tools.brag;

import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class BragCleanupJob {

    @Inject
    BragConfig config;

    @Inject
    AgentMeters meters;

    @Scheduled(identity = "brag-cleanup", cron = "{qlawkus.brag.cleanup-cron}")
    @Transactional
    public void purgeExpiredEntries() {
        meters.timeJob("brag-cleanup", this::purgeNow);
    }

    /** Returns how many entries were removed, so the job reports items like the cognition jobs. */
    private long purgeNow() {
        Instant cutoff = Instant.now().minus(config.cleanupAgeDays(), ChronoUnit.DAYS);

        long deleted = BragEntry.delete("deleted = true and createdAt < ?1", cutoff);
        if (deleted > 0) {
            Log.infof("Purged %d soft-deleted brag entries older than %d days", deleted, config.cleanupAgeDays());
        }

        long duplicates = removeDuplicates();
        if (duplicates > 0) {
            Log.infof("Removed %d duplicate brag entries", duplicates);
        }
        return deleted + duplicates;
    }

    private long removeDuplicates() {
        var entries = BragEntry.listAllActiveByDateAsc();

        long removed = 0;
        for (int i = 0; i < entries.size(); i++) {
            BragEntry current = entries.get(i);
            for (int j = i + 1; j < entries.size(); j++) {
                BragEntry other = entries.get(j);
                if (isDuplicate(current, other)) {
                    other.deleted = true;
                    removed++;
                }
            }
        }
        return removed;
    }

    private boolean isDuplicate(BragEntry a, BragEntry b) {
        return a.date.equals(b.date)
                && a.achievement != null && a.achievement.equals(b.achievement)
                && (a.repo == null ? b.repo == null : a.repo.equals(b.repo));
    }
}
