package dev.omatheusmesmo.qlawkus.tool.shell;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChildEnvironmentTest {

    private static final List<String> ALLOWLIST =
            List.of("PATH", "HOME", "USER", "LANG", "LC_*", "TZ", "TERM", "SHELL", "JAVA_HOME", "TMPDIR");

    private static Map<String, String> processEnv() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("PATH", "/usr/bin");
        env.put("HOME", "/home/agent");
        env.put("LC_ALL", "C.UTF-8");
        env.put("LC_CTYPE", "C.UTF-8");
        env.put("LLM_API_KEY", "nvapi-secret-key-value");
        env.put("QUARKUS_DATASOURCE_PASSWORD", "db-password-value");
        env.put("DISCORD_BOT_TOKEN", "discord-token-value");
        return env;
    }

    @Test
    void compose_dropsEveryNameThatIsNotAllowlisted() {
        Map<String, String> env = ChildEnvironment.compose(processEnv(), ALLOWLIST, Map.of(), Map.of());

        assertFalse(env.containsKey("LLM_API_KEY"));
        assertFalse(env.containsKey("QUARKUS_DATASOURCE_PASSWORD"));
        assertFalse(env.containsKey("DISCORD_BOT_TOKEN"));
    }

    @Test
    void compose_keepsAllowlistedNamesAndLcPrefixMatches() {
        Map<String, String> env = ChildEnvironment.compose(processEnv(), ALLOWLIST, Map.of(), Map.of());

        assertEquals("/usr/bin", env.get("PATH"));
        assertEquals("/home/agent", env.get("HOME"));
        assertEquals("C.UTF-8", env.get("LC_ALL"));
        assertEquals("C.UTF-8", env.get("LC_CTYPE"));
    }

    @Test
    void compose_prefixRuleDoesNotMatchNamesThatMerelyContainIt() {
        Map<String, String> process = new LinkedHashMap<>();
        process.put("XLC_ALL", "x");
        process.put("LC", "x");

        Map<String, String> env = ChildEnvironment.compose(process, ALLOWLIST, Map.of(), Map.of());

        assertTrue(env.isEmpty(), "got " + env);
    }

    @Test
    void compose_addsWorkspaceEnvOnTopOfTheAllowlist() {
        Map<String, String> env = ChildEnvironment.compose(processEnv(), ALLOWLIST,
                Map.of("PROJECT_FLAG", "on", "HOME", "/workspace"), Map.of());

        assertEquals("on", env.get("PROJECT_FLAG"));
        assertEquals("/workspace", env.get("HOME"));
    }

    @Test
    void compose_passesConfiguredSecretsAndSkipsBlankOnes() {
        Map<String, String> secrets = new LinkedHashMap<>();
        secrets.put("NPM_TOKEN", "npm-token-value");
        secrets.put("EMPTY_ONE", "");
        secrets.put("BLANK_ONE", "   ");

        Map<String, String> env = ChildEnvironment.compose(processEnv(), ALLOWLIST, Map.of(), secrets);

        assertEquals("npm-token-value", env.get("NPM_TOKEN"));
        assertFalse(env.containsKey("EMPTY_ONE"));
        assertFalse(env.containsKey("BLANK_ONE"));
    }

    @Test
    void compose_skipsSecretNamesThatAreNotValidEnvironmentNames() {
        Map<String, String> secrets = new LinkedHashMap<>();
        secrets.put("lower_case", "value-one-1234");
        secrets.put("1LEADING_DIGIT", "value-two-1234");
        secrets.put("HAS-DASH", "value-three-1234");
        secrets.put("_UNDERSCORE_OK", "value-four-1234");

        Map<String, String> env = ChildEnvironment.compose(processEnv(), ALLOWLIST, Map.of(), secrets);

        assertEquals("value-four-1234", env.get("_UNDERSCORE_OK"));
        assertFalse(env.containsKey("lower_case"));
        assertFalse(env.containsKey("1LEADING_DIGIT"));
        assertFalse(env.containsKey("HAS-DASH"));
    }

    @Test
    void compose_withGhTokenSetsCommandScopeGitCredentialConfig() {
        Map<String, String> env = ChildEnvironment.compose(processEnv(), ALLOWLIST, Map.of(),
                Map.of("GH_TOKEN", "ghp_" + "a".repeat(36)));

        assertEquals("2", env.get("GIT_CONFIG_COUNT"));
        assertEquals("credential.helper", env.get("GIT_CONFIG_KEY_0"));
        assertEquals("", env.get("GIT_CONFIG_VALUE_0"));
        assertEquals("credential.https://github.com.helper", env.get("GIT_CONFIG_KEY_1"));
        assertEquals("!gh auth git-credential", env.get("GIT_CONFIG_VALUE_1"));
        assertEquals("0", env.get("GIT_TERMINAL_PROMPT"));
    }

    @Test
    void compose_withoutGhTokenAddsNoGitCredentialConfig() {
        Map<String, String> env = ChildEnvironment.compose(processEnv(), ALLOWLIST, Map.of(),
                Map.of("NPM_TOKEN", "npm-token-value"));

        assertFalse(env.containsKey("GIT_CONFIG_COUNT"));
        assertFalse(env.containsKey("GIT_TERMINAL_PROMPT"));
    }

    @Test
    void compose_withBlankGhTokenAddsNoGitCredentialConfig() {
        Map<String, String> env = ChildEnvironment.compose(processEnv(), ALLOWLIST, Map.of(),
                Map.of("GH_TOKEN", ""));

        assertFalse(env.containsKey("GH_TOKEN"));
        assertFalse(env.containsKey("GIT_CONFIG_COUNT"));
    }

    @Test
    void compose_workspaceEnvCannotOverrideTheGitCredentialConfigWhenGhTokenIsPassed() {
        Map<String, String> env = ChildEnvironment.compose(processEnv(), ALLOWLIST,
                Map.of("GIT_CONFIG_COUNT", "0"), Map.of("GH_TOKEN", "ghp_" + "b".repeat(36)));

        assertEquals("2", env.get("GIT_CONFIG_COUNT"));
    }
}
