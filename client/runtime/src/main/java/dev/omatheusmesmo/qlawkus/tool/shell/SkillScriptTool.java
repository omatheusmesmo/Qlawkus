package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.config.SkillScriptsConfig;
import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.skill.SkillScripts;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs a script that ships in a skill's {@code scripts/} folder. The script runs through
 * {@link ShellTool#runCommand}, so the command filter, the child environment (which withholds the
 * agent's secrets), output redaction and caps, the process limit and the audit log all apply as they do
 * to any shell command.
 *
 * <p>Two tools exist because the owner's approval is decided per tool name: scripts of the agent's own
 * skills run at once, scripts a Skill Hub install wrote wait for the owner on every call. Each tool
 * refuses the other kind, so the approval cannot be skipped by picking the other name.
 */
@QlawTool
public class SkillScriptTool {

    public static final String OWN_TOOL = "runSkillScript";

    public static final String INSTALLED_TOOL = "runInstalledSkillScript";

    public static final String SHELL_TOOL = "runCommand";

    static final int MAX_ARGUMENTS = 32;

    static final int MAX_ARGUMENT_LENGTH = 4096;

    @Inject
    SkillStore skills;

    @Inject
    SkillScriptsConfig config;

    @Inject
    @QlawTool
    ShellTool shell;

    @Tool("Run a script from the scripts folder of one of your own skills (the skill's instructions say which). "
            + "Parameters: skill (required) - the exact skill name, script (required) - the path inside the "
            + "skill's scripts folder, args (optional) - arguments passed to the script, timeoutSeconds "
            + "(optional) - lowered to the configured maximum. Scripts of a skill installed from the hub need "
            + "runInstalledSkillScript instead.")
    public CommandResult runSkillScript(@P("The exact skill name") String skill,
            @P("Path of the script inside the skill's scripts folder") String script,
            @P(value = "Arguments for the script", required = false) List<String> args,
            @P(value = "Timeout in seconds", required = false) Integer timeoutSeconds) {
        return run(skill, script, args, timeoutSeconds, false);
    }

    @Tool("Run a script from the scripts folder of a skill that was installed from the skill hub. The owner is "
            + "asked to approve every call, and the script runs only after they do. Parameters: skill "
            + "(required), script (required), args (optional), timeoutSeconds (optional).")
    public CommandResult runInstalledSkillScript(@P("The exact skill name") String skill,
            @P("Path of the script inside the skill's scripts folder") String script,
            @P(value = "Arguments for the script", required = false) List<String> args,
            @P(value = "Timeout in seconds", required = false) Integer timeoutSeconds) {
        return run(skill, script, args, timeoutSeconds, true);
    }

    private CommandResult run(String skill, String script, List<String> args, Integer timeoutSeconds,
            boolean installedTool) {
        if (!config.enabled()) {
            return refused("Skill scripts are disabled (qlawkus.skill-scripts.enabled=false).");
        }
        if (ShellTool.IS_WINDOWS) {
            return refused("Skill scripts need a POSIX shell and are not supported on Windows.");
        }
        if (skill == null || skill.isBlank() || script == null || script.isBlank()) {
            return refused("Give the exact skill name and the script path inside its scripts folder.");
        }
        Optional<SkillScripts> found = skills.scriptsOf(skill.strip());
        if (found.isEmpty()) {
            return refused("The skill '" + skill + "' has no scripts folder. Check the skill's instructions.");
        }
        SkillScripts scripts = found.get();
        if (scripts.installed() && !installedTool) {
            return refused("The scripts of '" + skill + "' were installed from the skill hub. Use "
                    + INSTALLED_TOOL + "; the owner approves each run.");
        }
        if (!scripts.installed() && installedTool) {
            return refused("The scripts of '" + skill + "' are not from the skill hub. Use " + OWN_TOOL + ".");
        }
        Path target;
        try {
            target = resolve(scripts.dir(), script.strip());
        } catch (IllegalArgumentException e) {
            return refused(e.getMessage());
        }
        List<String> arguments = args == null ? List.of() : args;
        if (arguments.size() > MAX_ARGUMENTS
                || arguments.stream().anyMatch(argument -> argument == null || argument.length() > MAX_ARGUMENT_LENGTH)) {
            return refused("Pass at most " + MAX_ARGUMENTS + " arguments of " + MAX_ARGUMENT_LENGTH
                    + " characters each.");
        }
        return shell.runCommand(command(target, arguments), null, timeout(timeoutSeconds));
    }

    /**
     * Resolves a script to a real file inside the real scripts folder. The real path is compared, not the
     * written one, so a symbolic link a script folder holds cannot point the run at a file outside it.
     */
    static Path resolve(Path scriptsDir, String script) {
        if (script.startsWith("/") || script.contains("\\") || script.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("The script path must be relative, inside the skill's scripts folder.");
        }
        Path candidate = scriptsDir.resolve(script).normalize();
        if (!candidate.startsWith(scriptsDir) || candidate.equals(scriptsDir)) {
            throw new IllegalArgumentException("The script path must stay inside the skill's scripts folder.");
        }
        try {
            Path real = candidate.toRealPath();
            if (!real.startsWith(scriptsDir.toRealPath())) {
                throw new IllegalArgumentException("The script resolves outside the skill's scripts folder.");
            }
            if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("'" + script + "' is not a file in the skill's scripts folder.");
            }
            if (real.getFileName().toString().equals(SkillScripts.INSTALLED_MARKER)) {
                throw new IllegalArgumentException("'" + script + "' is not a script.");
            }
            return real;
        } catch (IOException e) {
            throw new IllegalArgumentException("No script '" + script + "' in the skill's scripts folder"
                    + listing(scriptsDir));
        }
    }

    static String command(Path script, List<String> args) {
        return Stream.concat(Stream.of(script.toString()), args.stream())
                .map(SkillScriptTool::quote).collect(Collectors.joining(" "));
    }

    static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private int timeout(Integer requested) {
        int limit = (int) Math.max(1, config.timeout().toSeconds());
        return requested == null || requested < 1 ? limit : Math.min(requested, limit);
    }

    private static String listing(Path scriptsDir) {
        try (var files = Files.walk(scriptsDir, 3)) {
            String names = files.filter(Files::isRegularFile)
                    .filter(file -> !file.getFileName().toString().equals(SkillScripts.INSTALLED_MARKER))
                    .map(file -> scriptsDir.relativize(file).toString()).sorted().limit(20)
                    .collect(Collectors.joining(", "));
            return names.isEmpty() ? "." : ". Available: " + names + ".";
        } catch (IOException e) {
            return ".";
        }
    }

    private static CommandResult refused(String message) {
        return new CommandResult("", message, ShellTool.SECURITY_BLOCK_EXIT_CODE, 0, false);
    }
}
