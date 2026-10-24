package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.config.ShellConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds the environment every shell command and PTY session starts with, so the two cannot drift:
 * only allowlisted process variables, then the workspace env file, then the secrets configured under
 * {@code qlawkus.shell.secret-env}. The process environment itself is never inherited.
 */
@ApplicationScoped
public class ChildEnvironment {

    static final String GH_TOKEN = "GH_TOKEN";

    private static final Pattern VALID_NAME = Pattern.compile("[A-Z_][A-Z0-9_]*");

    @Inject
    ShellConfig shellConfig;

    @Inject
    WorkspaceConfinement workspaceConfinement;

    /**
     * The environment for one new child process, built from the live process environment.
     */
    public Map<String, String> build() {
        return compose(System.getenv(), shellConfig.envAllowlist(), workspaceConfinement.getWorkspaceEnv(),
                configuredSecrets());
    }

    /**
     * The secrets configured under {@code qlawkus.shell.secret-env} that actually have a value.
     */
    public Map<String, String> configuredSecrets() {
        Map<String, String> secrets = new LinkedHashMap<>();
        shellConfig.secretEnv().forEach((name, value) -> value.ifPresent(v -> secrets.put(name, v)));
        return secrets;
    }

    static Map<String, String> compose(Map<String, String> processEnv, List<String> allowlist,
                                       Map<String, String> workspaceEnv, Map<String, String> secrets) {
        Map<String, String> env = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : processEnv.entrySet()) {
            if (isAllowlisted(entry.getKey(), allowlist)) {
                env.put(entry.getKey(), entry.getValue());
            }
        }
        env.putAll(workspaceEnv);

        boolean passesGitHubToken = false;
        for (Map.Entry<String, String> secret : secrets.entrySet()) {
            String name = secret.getKey();
            String value = secret.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!VALID_NAME.matcher(name).matches()) {
                Log.warnf("ShellTool: qlawkus.shell.secret-env name '%s' is not a valid environment variable name, skipped", name);
                continue;
            }
            env.put(name, value);
            passesGitHubToken |= GH_TOKEN.equals(name);
        }

        if (passesGitHubToken) {
            env.put("GIT_CONFIG_COUNT", "2");
            env.put("GIT_CONFIG_KEY_0", "credential.helper");
            env.put("GIT_CONFIG_VALUE_0", "");
            env.put("GIT_CONFIG_KEY_1", "credential.https://github.com.helper");
            env.put("GIT_CONFIG_VALUE_1", "!gh auth git-credential");
            env.put("GIT_TERMINAL_PROMPT", "0");
        }
        return env;
    }

    private static boolean isAllowlisted(String name, List<String> allowlist) {
        String candidate = ShellTool.IS_WINDOWS ? name.toUpperCase(Locale.ROOT) : name;
        for (String rule : allowlist) {
            String entry = ShellTool.IS_WINDOWS ? rule.toUpperCase(Locale.ROOT) : rule;
            if (entry.endsWith("*") ? candidate.startsWith(entry.substring(0, entry.length() - 1)) : candidate.equals(entry)) {
                return true;
            }
        }
        return false;
    }
}
