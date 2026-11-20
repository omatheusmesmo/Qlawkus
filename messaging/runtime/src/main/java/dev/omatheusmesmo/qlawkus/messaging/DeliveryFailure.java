package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.logging.FailureChain;
import dev.omatheusmesmo.qlawkus.logging.FailureSummary;
import jakarta.ws.rs.WebApplicationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * What a platform said about a rejected reply: the HTTP status and, where the platform's own error body
 * was read, its error code and message. Any field the platform did not provide is {@code null} and is
 * left off the log line. The text is provider output, never reply content, and is bounded and redacted
 * before it is written.
 */
public record DeliveryFailure(Integer httpStatus, String providerCode, String providerMessage) {

    /** The status of a REST call the platform answered with a non-2xx, found anywhere in the cause chain. */
    public static Optional<DeliveryFailure> fromHttpStatus(Throwable failure) {
        return FailureChain.first(failure, WebApplicationException.class)
                .map(rejected -> new DeliveryFailure(rejected.getResponse().getStatus(), null, null));
    }

    public String describe(UnaryOperator<String> redact) {
        List<String> fields = new ArrayList<>();
        if (httpStatus != null) {
            fields.add("httpStatus=" + httpStatus);
        }
        if (providerCode != null) {
            fields.add("providerCode=" + FailureSummary.boundedLine(providerCode, redact));
        }
        if (providerMessage != null && !providerMessage.isBlank()) {
            fields.add("providerMessage=" + FailureSummary.boundedLine(providerMessage, redact));
        }
        return String.join(" ", fields);
    }
}
