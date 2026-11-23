package dev.omatheusmesmo.qlawkus.messaging.approval;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import java.util.Optional;

/**
 * The payload an Approve or Deny button carries, and how a pressed button becomes the same message the
 * owner would have typed. A press is never a decision by itself: it is turned into
 * {@code /approve <id>} or {@code /deny <id>} for the pressing user and handed to the orchestrator, so
 * the allowlist, the owner attribution and the model-free handling are exactly those of the typed
 * commands. Anything that is not a well-formed payload for a real request id is ignored.
 */
public final class ApprovalCallbacks {

    private static final String PREFIX = "qa";
    private static final String APPROVE = "approve";
    private static final String DENY = "deny";

    private ApprovalCallbacks() {
    }

    /** The payload of the Approve button, short enough for every platform's limit (Telegram allows 64 bytes). */
    public static String approve(String requestId) {
        return PREFIX + ":" + APPROVE + ":" + requestId;
    }

    /** The payload of the Deny button. */
    public static String deny(String requestId) {
        return PREFIX + ":" + DENY + ":" + requestId;
    }

    /**
     * The message the pressing user is treated as having sent, or empty for a payload this feature did not
     * create.
     */
    public static Optional<MessagingMessage> toCommand(String providerId, String chatId, String userId, String data) {
        if (data == null) {
            return Optional.empty();
        }
        String[] parts = data.split(":", -1);
        if (parts.length != 3 || !PREFIX.equals(parts[0]) || !ApprovalRequest.isValidId(parts[2])) {
            return Optional.empty();
        }
        if (!APPROVE.equals(parts[1]) && !DENY.equals(parts[1])) {
            return Optional.empty();
        }
        return Optional.of(MessagingMessage.text(providerId, chatId, userId, "/" + parts[1] + " " + parts[2]));
    }
}
