package dev.omatheusmesmo.qlawkus.messaging.discord;

import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.approval.ApprovalCallbacks;
import dev.omatheusmesmo.qlawkus.messaging.auth.MessagingAuthService;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import java.util.Optional;

/**
 * The Approve and Deny buttons of an approval prompt, and what a press of one means. A press is never a
 * decision by itself: it becomes the typed {@code /approve} or {@code /deny} command from the user who
 * pressed it, and only a user on the messaging allowlist gets that far.
 */
final class DiscordApprovalButtons {

    enum Outcome {
        /** Not a button this feature created (or a forged payload): nothing to do. */
        IGNORE,
        /** A well-formed press by someone who is not on the allowlist: refused, the buttons stay. */
        REJECT,
        /** A well-formed press by an allowlisted user: run the command. */
        DECIDE
    }

    record Press(Outcome outcome, MessagingMessage command) {
    }

    private DiscordApprovalButtons() {
    }

    static ActionRow row(String requestId) {
        return ActionRow.of(
                Button.success(ApprovalCallbacks.approve(requestId), "Approve"),
                Button.danger(ApprovalCallbacks.deny(requestId), "Deny"));
    }

    static Press classify(String customId, String channelId, String userId, MessagingAuthService auth) {
        Optional<MessagingMessage> command = ApprovalCallbacks.toCommand("discord", channelId, userId, customId);
        if (command.isEmpty()) {
            return new Press(Outcome.IGNORE, null);
        }
        if (!auth.isAuthorized(command.get())) {
            return new Press(Outcome.REJECT, null);
        }
        return new Press(Outcome.DECIDE, command.get());
    }
}
