package dev.omatheusmesmo.qlawkus.approval;

import java.util.function.Supplier;

/**
 * Marks the one call the approval service makes after the owner approved a request. A tool whose
 * approval mode is {@code hitl} refuses to write unless this mark is set, so a model that passes
 * {@code confirm=true} to a tool that was somehow not behind the gate still changes nothing.
 *
 * <p>The mark lives on the calling thread only and is cleared when the call returns, so it never
 * reaches another turn or another thread.
 */
public final class ApprovedExecution {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private ApprovedExecution() {
    }

    /** Whether the current call is the approved execution of an owner-approved request. */
    public static boolean active() {
        return DEPTH.get() > 0;
    }

    /** Runs {@code call} marked as an approved execution and clears the mark afterwards. */
    public static <T> T run(Supplier<T> call) {
        int outer = DEPTH.get();
        DEPTH.set(outer + 1);
        try {
            return call.get();
        } finally {
            if (outer == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(outer);
            }
        }
    }
}
