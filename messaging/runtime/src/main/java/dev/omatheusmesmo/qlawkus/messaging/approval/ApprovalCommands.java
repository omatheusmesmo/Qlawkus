package dev.omatheusmesmo.qlawkus.messaging.approval;

import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.approval.ApprovalService;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code /approve <id>} and {@code /deny <id>} commands. They are handled before the model sees the
 * message, so the model has no path to them: the text it writes is never parsed as a decision.
 *
 * <p>The sender must pass the same allowlist as every other message. The decision is attributed to the
 * sender, so the record says which chat account made it.
 */
@ApplicationScoped
public class ApprovalCommands {

    private static final Pattern COMMAND = Pattern.compile("/(approve|deny)(?:\\s+(\\S+))?", Pattern.CASE_INSENSITIVE);
    private static final String USAGE = "Usage: /approve <id> or /deny <id>.";

    private final ApprovalService approvals;

    @Inject
    public ApprovalCommands(ApprovalService approvals) {
        this.approvals = approvals;
    }

    public static boolean isCommand(String text) {
        return text != null && COMMAND.matcher(text.strip()).matches();
    }

    /**
     * Carries out the command, or refuses it. Authorization is checked here as well as by the caller, so
     * a path that reaches this method without going through the orchestrator still cannot decide.
     */
    public String handle(MessagingMessage message, Predicate<MessagingMessage> authorized) {
        if (!authorized.test(message)) {
            return "Not authorized.";
        }
        Matcher command = COMMAND.matcher(message.text().strip());
        if (!command.matches()) {
            return USAGE;
        }
        String verb = command.group(1).toLowerCase(Locale.ROOT);
        String id = command.group(2);
        if (!ApprovalRequest.isValidId(id)) {
            return USAGE;
        }
        String actor = "messaging:" + message.providerId() + ":" + message.userId();
        boolean approve = "approve".equals(verb);
        ApprovalService.Decision decision = approve ? approvals.approve(id, actor) : approvals.deny(id, actor);
        if (!decision.accepted()) {
            return decision.message();
        }
        return approve ? "Approved request " + id + "." : "Denied request " + id + ".";
    }
}
