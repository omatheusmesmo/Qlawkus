package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.config.ShellConfig;
import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class CommandFilter {

    @Inject
    ShellConfig shellConfig;

    public List<String> denylist;
    public boolean allowlistMode;
    public List<String> allowlist;

    @PostConstruct
    void init() {
        this.denylist = shellConfig.denylist();
        this.allowlistMode = shellConfig.allowlistMode();
        this.allowlist = shellConfig.allowlist();
    }

    /**
     * Checks a command against the active security policy.
     * In allowlist mode, only allowlist patterns are evaluated (denylist is skipped).
     * In denylist mode, only denylist patterns are evaluated.
     *
     * @param command the shell command to validate
     * @return {@link SecurityResult} with {@code blocked=true} if the command violates the policy
     */
    public SecurityResult check(String command) {
        String trimmed = command.trim();

        if (allowlistMode) {
            if (allowlist.isEmpty() || (allowlist.size() == 1 && "none".equals(allowlist.get(0)))) {
                Log.warnf("CommandFilter: allowlist mode active but no allowlist configured — '%s'", trimmed);
                return new SecurityResult(true, "Allowlist mode active but no allowlist configured", "allowlist", trimmed);
            }
            boolean allowed = false;
            for (String pattern : allowlist) {
                String p = pattern.trim();
                if (!p.isEmpty() && globMatch(p, trimmed)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                Log.warnf("CommandFilter: blocked by allowlist — '%s'", trimmed);
                return new SecurityResult(true, "Command not in allowlist", "allowlist", trimmed);
            }
            return new SecurityResult(false, "", "", trimmed);
        }

        for (String pattern : denylist) {
            String p = pattern.trim();
            if (!p.isEmpty() && globMatch(p, trimmed)) {
                Log.warnf("CommandFilter: blocked by denylist pattern '%s' — '%s'", p, trimmed);
                return new SecurityResult(true, "Command matches denied pattern", p, trimmed);
            }
        }

        return new SecurityResult(false, "", "", trimmed);
    }

    /**
     * Glob pattern match against command text. Supports {@code *} (any chars) and {@code ?} (single char).
     * Without wildcards, matches exact text or text starting with the pattern followed by a space.
     */
    public static boolean globMatch(String pattern, String text) {
        if (!pattern.contains("*")) {
            return text.equals(pattern) || text.startsWith(pattern + " ");
        }

        String regex = globToRegex(pattern);
        return text.matches(regex);
    }

    static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        sb.append("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' ->
                        sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }

    public List<String> getDenylist() {
        return List.copyOf(denylist);
    }

    public List<String> getAllowlist() {
        return List.copyOf(allowlist);
    }

    public boolean isAllowlistMode() {
        return allowlistMode;
    }
}
