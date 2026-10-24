package dev.omatheusmesmo.qlawkus.dto;

import java.util.List;

/**
 * The redeploy history the composition control plane persists and serves. It is what survives the
 * restart a redeploy performs, so it carries the recent runs and the running totals the meters are
 * read from.
 *
 * @param lastSuccessAt ISO-8601 instant the last successful run was recorded, or {@code null}; kept
 *                      apart from {@code runs} so a streak of failures cannot push it out
 * @param runs the most recent runs, newest first, bounded
 * @param totals runs per outcome and failed phase, since the history began
 * @param phaseTotals time spent per phase, since the history began
 */
public record RedeployHistory(
    String lastSuccessAt,
    List<RedeployRun> runs,
    List<RedeployTotal> totals,
    List<RedeployPhaseTotal> phaseTotals
) {}
