package dev.omatheusmesmo.qlawkus.messaging.permission;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.permission.PermissionOverview;
import dev.omatheusmesmo.qlawkus.permission.PermissionService;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The {@code /permissions} command: show the matrix, set a cell, apply a preset, or explain a tool. It
 * is handled before the model sees the message, so the model has no path to it: the text it writes is
 * never parsed as a command.
 *
 * <p>The sender must pass the same allowlist as every other message and acts as a chat account, never
 * as the admin. Tightening applies at once; loosening is recorded as an owner approval request and the
 * reply says which id to {@code /approve}.
 */
@ApplicationScoped
public class PermissionCommands {

    private static final Pattern COMMAND = Pattern.compile("/permissions(?:\\s+.*)?", Pattern.CASE_INSENSITIVE
            | Pattern.DOTALL);
    private static final String USAGE = "Usage: /permissions, /permissions <group> <operation> <allow|ask|deny>, "
            + "/permissions default <operation> <allow|ask|deny>, /permissions tool <name> <allow|ask|deny>, "
            + "/permissions preset <readonly|ask-writes|trusted> or /permissions explain <tool>.";

    private final PermissionService permissions;

    @Inject
    public PermissionCommands(PermissionService permissions) {
        this.permissions = permissions;
    }

    public static boolean isCommand(String text) {
        return text != null && COMMAND.matcher(text.strip()).matches();
    }

    /**
     * Carries out the command, or refuses it. Authorization is checked here as well as by the caller, so
     * a path that reaches this method without going through the orchestrator still cannot change anything.
     */
    public String handle(MessagingMessage message, Predicate<MessagingMessage> authorized) {
        if (!authorized.test(message)) {
            return "Not authorized.";
        }
        String[] words = message.text().strip().split("\\s+");
        PermissionService.Actor actor = PermissionService.Actor.chat(message.providerId(), message.userId());
        AgentDeliveryContext.Snapshot origin = new AgentDeliveryContext.Snapshot(null, message.providerId(),
                message.chatId());
        if (words.length == 1) {
            return render(permissions.overview());
        }
        String first = words[1].toLowerCase(Locale.ROOT);
        if (words.length == 3 && "explain".equals(first)) {
            return render(permissions.explain(words[2]));
        }
        if (words.length == 3 && "preset".equals(first)) {
            return render(permissions.applyPreset(words[2], actor, origin));
        }
        if (words.length == 4 && "tool".equals(first)) {
            return render(permissions.setTool(words[2], words[3], actor, origin));
        }
        if (words.length == 4) {
            return render(permissions.set(words[1], words[2], words[3], actor, origin));
        }
        return USAGE;
    }

    private static String render(PermissionService.Outcome outcome) {
        return switch (outcome.status()) {
            case APPLIED -> "Done: " + outcome.message();
            case UNCHANGED -> outcome.message();
            case REJECTED -> "Not changed: " + outcome.message() + ".";
            case PENDING -> "Waiting for the owner: " + outcome.message() + ". Reply /approve " + outcome.requestId()
                    + " to apply it or /deny " + outcome.requestId() + " to drop it.";
        };
    }

    private static String render(PermissionService.Explanation explanation) {
        StringBuilder text = new StringBuilder(explanation.tool()).append(": ").append(explanation.level().label())
                .append(" (group ").append(explanation.group()).append(", ")
                .append(explanation.accessClass().name().toLowerCase(Locale.ROOT)).append(")");
        explanation.reasons().forEach(reason -> text.append('\n').append(reason));
        return text.toString();
    }

    private static String render(PermissionOverview overview) {
        StringBuilder text = new StringBuilder("Permissions (preset: ")
                .append(overview.preset() == null ? "custom" : overview.preset()).append(")\n");
        text.append(row("default", overview.defaults()));
        overview.groups().stream()
                .filter(group -> group.cells().values().stream().anyMatch(cell -> cell.tools() > 0))
                .forEach(group -> text.append('\n').append(row(group.group(), group.cells())));
        overview.tools().forEach((tool, level) -> text.append("\ntool ").append(tool).append(": ")
                .append(level.label()));
        return text.toString();
    }

    private static String row(String name, Map<AccessClass, PermissionOverview.Cell> cells) {
        StringBuilder row = new StringBuilder(name).append(": ");
        boolean first = true;
        for (AccessClass operation : AccessClass.values()) {
            if (!first) {
                row.append(", ");
            }
            first = false;
            row.append(operation.name().toLowerCase(Locale.ROOT)).append(' ').append(cells.get(operation).level().label());
        }
        return row.toString();
    }
}
