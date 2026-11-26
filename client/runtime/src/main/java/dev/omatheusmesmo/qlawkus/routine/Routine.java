package dev.omatheusmesmo.qlawkus.routine;

import java.util.Arrays;
import java.util.Optional;

/** The two owner-configurable routines: the name the admin API and the owner use, and the job identity. */
public enum Routine {

    WEEKLY("weekly", "routine-weekly", "weekly report"),
    MORNING("morning", "routine-morning", "Monday briefing");

    private final String slug;
    private final String job;
    private final String title;

    Routine(String slug, String job, String title) {
        this.slug = slug;
        this.job = job;
        this.title = title;
    }

    /** The short name used in the admin API path. */
    public String slug() {
        return slug;
    }

    /** The scheduler identity, the job metric label and the log name. */
    public String job() {
        return job;
    }

    /** How the owner is told about this routine in a notice. */
    public String title() {
        return title;
    }

    public static Optional<Routine> of(String slug) {
        return Arrays.stream(values()).filter(routine -> routine.slug.equalsIgnoreCase(slug)).findFirst();
    }
}
