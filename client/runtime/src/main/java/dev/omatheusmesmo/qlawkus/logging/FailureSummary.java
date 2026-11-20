package dev.omatheusmesmo.qlawkus.logging;

import java.util.function.UnaryOperator;

/**
 * The one-line account of a failure written on an INFO or WARN line: the class of the root cause and
 * its message, one line and at most {@link #MAX_LENGTH} characters, redacted before it is capped so a
 * secret cut at the boundary cannot survive. Callers pass the redactor, so the line never carries a
 * configured secret even when the failure's own message does.
 */
public record FailureSummary(String error, String cause) {

    public static final int MAX_LENGTH = 200;

    private static final String NO_MESSAGE = "(no message)";
    private static final String ELLIPSIS = "...";

    public static FailureSummary of(Throwable failure, UnaryOperator<String> redact) {
        Throwable root = FailureChain.root(failure);
        String message = root.getMessage();
        String cause = message == null || message.isBlank() ? NO_MESSAGE : boundedLine(message, redact);
        return new FailureSummary(root.getClass().getSimpleName(), cause);
    }

    /** Collapses the text to one line, redacts it, then caps it at {@link #MAX_LENGTH} characters. */
    public static String boundedLine(String text, UnaryOperator<String> redact) {
        String line = redact.apply(text.strip().replaceAll("\\s+", " "));
        if (line.length() <= MAX_LENGTH) {
            return line;
        }
        return line.substring(0, MAX_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }
}
