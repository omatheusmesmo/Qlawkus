package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.dto.RedeployReport;
import dev.omatheusmesmo.qlawkus.dto.RedeployRun;
import io.quarkus.qute.TemplateData;

import java.util.List;
import java.util.Locale;

/** The last reported redeploy run, as the schedule page renders it. */
@TemplateData
public record RedeployRunView(
        String outcome,
        boolean failed,
        String failedPhase,
        String recordedAt,
        String startedAt,
        List<PhaseTiming> phases,
        String message,
        String lastSuccessAt,
        String total
) {

    /** One phase that ran, with its duration already formatted for reading. */
    @TemplateData
    public record PhaseTiming(String phase, String duration, boolean failed) {
    }

    static RedeployRunView of(RedeployRun run, String lastSuccessAt) {
        List<PhaseTiming> phases = run.phases().entrySet().stream()
                .map(entry -> new PhaseTiming(entry.getKey(), duration(entry.getValue()),
                        entry.getKey().equals(run.failedPhase())))
                .toList();
        return new RedeployRunView(run.outcome(), RedeployReport.FAILURE.equals(run.outcome()),
                run.failedPhase(), run.recordedAt(), run.startedAt(), phases, run.message(), lastSuccessAt,
                duration(run.phases().values().stream().mapToLong(Long::longValue).sum()));
    }

    static String duration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        return String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
    }
}
