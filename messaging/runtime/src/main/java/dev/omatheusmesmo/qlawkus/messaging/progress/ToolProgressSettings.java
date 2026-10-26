package dev.omatheusmesmo.qlawkus.messaging.progress;

import dev.omatheusmesmo.qlawkus.agent.ToolProgress;
import dev.omatheusmesmo.qlawkus.messaging.MessagingConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tool-progress mode of each chat. The configured mode is the default; {@code /verbose} changes
 * one chat's mode in memory, so the override lasts until the process restarts.
 */
@ApplicationScoped
public class ToolProgressSettings {

    static final String VERBOSE_COMMAND = "/verbose";

    @Inject
    MessagingConfig config;

    private final Map<String, ToolProgress> overrides = new ConcurrentHashMap<>();

    public static String key(String providerId, String chatId) {
        return providerId + ":" + chatId;
    }

    public ToolProgress modeFor(String key) {
        ToolProgress override = overrides.get(key);
        return override != null ? override : config.toolProgress().mode();
    }

    public ToolProgress cycle(String key) {
        ToolProgress next = next(modeFor(key));
        overrides.put(key, next);
        return next;
    }

    public ProgressCompletion completion() {
        return ProgressCompletion.parse(config.toolProgress().onComplete());
    }

    public int maxLines() {
        return config.toolProgress().maxLines();
    }

    static ToolProgress next(ToolProgress current) {
        ToolProgress[] modes = ToolProgress.values();
        return modes[(current.ordinal() + 1) % modes.length];
    }

    public static boolean isVerboseCommand(String text) {
        if (text == null) {
            return false;
        }
        String command = text.strip().toLowerCase(Locale.ROOT);
        int at = command.indexOf('@');
        if (at >= 0) {
            command = command.substring(0, at);
        }
        return command.equals(VERBOSE_COMMAND);
    }
}
