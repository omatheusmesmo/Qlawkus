package dev.omatheusmesmo.qlawkus.cognition;

import io.quarkus.logging.Log;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The start and end lines of a background job, in one shape for all of them: the job's name as its
 * {@code @Scheduled} identity, then the counts the job reports, then the duration. A job that fails
 * logs one warning naming the exception class, never its message, because a message can carry the
 * data the job was working on.
 *
 * <p>Each job opens a {@link Run} inside its own body rather than around the scheduler callback, so a
 * manual trigger through the admin API reads the same as a scheduled run.
 */
public final class JobLog {

    private JobLog() {
    }

    /** Opens a run of {@code job}, logging its start. */
    public static Run start(String job) {
        Log.infof("Job %s started", job);
        return new Run(job);
    }

    /** One execution of a job. Finish or fail it exactly once. */
    public static final class Run {

        private final String job;
        private final long startedAt = System.nanoTime();
        private final Map<String, Object> counts = new LinkedHashMap<>();

        private Run(String job) {
            this.job = job;
        }

        /** Records a count the job reports at its end, such as the number of rows it changed. */
        public Run count(String name, Object value) {
            counts.put(name, value);
            return this;
        }

        /** Logs the end of the run with its counts and duration. */
        public void finish() {
            Log.infof("Job %s finished duration=%dms %s", job, durationMs(), describeCounts());
        }

        /** Logs a failed run by exception class only, leaving the message out of the log. */
        public void fail(Throwable failure) {
            Log.warnf(failure, "Job %s failed duration=%dms error=%s", job, durationMs(),
                    failure.getClass().getSimpleName());
        }

        private long durationMs() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        }

        private String describeCounts() {
            if (counts.isEmpty()) {
                return "";
            }
            StringBuilder text = new StringBuilder();
            counts.forEach((name, value) -> text.append(text.isEmpty() ? "" : " ").append(name).append('=')
                    .append(value));
            return text.toString();
        }
    }
}
