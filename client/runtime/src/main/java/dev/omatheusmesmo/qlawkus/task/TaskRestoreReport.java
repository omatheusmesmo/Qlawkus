package dev.omatheusmesmo.qlawkus.task;

/**
 * What one restore did with the stored tasks: how many were registered again, how many were marked
 * missed or invalid, and how many were left stored because the task cap was already full.
 */
public record TaskRestoreReport(int restored, int missed, int invalid, int held) {

    public static final TaskRestoreReport NONE = new TaskRestoreReport(0, 0, 0, 0);
}
