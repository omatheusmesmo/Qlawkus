package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.dto.RedeployReport;
import dev.omatheusmesmo.qlawkus.dto.RedeployRun;
import io.quarkus.qute.TemplateData;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/** The last reported redeploy run, as the schedule page renders it. */
@TemplateData
public record RedeployRunView(
        String outcome,
        boolean failed,
        String failedPhase,
        TimeLabel recordedAt,
        TimeLabel startedAt,
        List<PhaseTiming> phases,
        String message,
        TimeLabel lastSuccessAt,
        String total
) {

    /** One phase that ran, with its duration already formatted for reading. */
    @TemplateData
    public record PhaseTiming(String phase, String duration, boolean failed) {
    }

    static RedeployRunView of(RedeployRun run, String lastSuccessAt, ZoneId zone) {
        List<PhaseTiming> phases = run.phases().entrySet().stream()
                .map(entry -> new PhaseTiming(entry.getKey(), duration(entry.getValue()),
                        entry.getKey().equals(run.failedPhase())))
                .toList();
        return new RedeployRunView(run.outcome(), RedeployReport.FAILURE.equals(run.outcome()),
                run.failedPhase(), TimeLabel.parse(run.recordedAt(), zone), TimeLabel.parse(run.startedAt(), zone), phases,
                run.message(), TimeLabel.parse(lastSuccessAt, zone),
                duration(run.phases().values().stream().mapToLong(Long::longValue).sum()));
    }

    static String duration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        return String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
    }
}
