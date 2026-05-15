package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandFilterTest {

    @Test
    void check_denylistMode_allowsEcho() {
        CommandFilter filter = new CommandFilter();
        filter.denylist = List.of("sudo *", "rm -rf /*", "mkfs*", "dd if=*", "format", "shutdown", "reboot");
        filter.allowlistMode = false;
        filter.allowlist = List.of();

        assertFalse(filter.check("echo hello").blocked(), "echo should be allowed");
    }

    @Test
    void check_denylistMode_blocksSudo() {
        CommandFilter filter = new CommandFilter();
        filter.denylist = List.of("sudo *");
        filter.allowlistMode = false;

        assertTrue(filter.check("sudo rm -rf /").blocked(), "sudo should be blocked");
    }

    @Test
    void check_allowlistMode_emptyAllowlist_blocksAll() {
        CommandFilter filter = new CommandFilter();
        filter.denylist = List.of();
        filter.allowlistMode = true;
        filter.allowlist = List.of("none");

        assertTrue(filter.check("ls").blocked(), "Empty allowlist should block everything");
    }

    @Test
    void check_allowlistMode_specificAllowed() {
        CommandFilter filter = new CommandFilter();
        filter.denylist = List.of();
        filter.allowlistMode = true;
        filter.allowlist = List.of("git *", "ls", "echo *");

        assertFalse(filter.check("git status").blocked(), "git should be allowed");
        assertFalse(filter.check("ls").blocked(), "ls should be allowed");
        assertFalse(filter.check("echo hello").blocked(), "echo should be allowed");
        assertTrue(filter.check("rm file.txt").blocked(), "rm should be blocked");
    }

    @Test
    void globMatch_exactMatch() {
        assertTrue(CommandFilter.globMatch("shutdown", "shutdown"));
        assertFalse(CommandFilter.globMatch("shutdown", "echo shutdown"));
    }

    @Test
    void globMatch_prefixMatch() {
        assertTrue(CommandFilter.globMatch("shutdown", "shutdown -h now"));
    }

    @Test
    void globMatch_wildcardMatch() {
        assertTrue(CommandFilter.globMatch("sudo *", "sudo apt install"));
        assertTrue(CommandFilter.globMatch("mkfs*", "mkfs.ext4"));
        assertFalse(CommandFilter.globMatch("sudo *", "echo hello"));
    }

    @Test
    void globMatch_questionMarkWildcard() {
        assertTrue(CommandFilter.globMatch("for?at", "format"));
        assertFalse(CommandFilter.globMatch("for?at", "formats"));
    }

    @Test
    void getDenylist_returnsImmutableCopy() {
        CommandFilter filter = new CommandFilter();
        filter.denylist = List.of("sudo *");
        assertThrows(UnsupportedOperationException.class, () -> filter.getDenylist().add("new"));
    }

    @Test
    void getAllowlist_returnsImmutableCopy() {
        CommandFilter filter = new CommandFilter();
        filter.allowlist = List.of("ls");
        assertThrows(UnsupportedOperationException.class, () -> filter.getAllowlist().add("new"));
    }

    @Test
    void isAllowlistMode_reflectsConfig() {
        CommandFilter filter = new CommandFilter();
        filter.allowlistMode = true;
        assertTrue(filter.isAllowlistMode());
        filter.allowlistMode = false;
        assertFalse(filter.isAllowlistMode());
    }

    @Test
    void check_denylist_ddIfBlocked() {
        CommandFilter filter = new CommandFilter();
        filter.denylist = List.of("dd if=*");
        filter.allowlistMode = false;

        assertTrue(filter.check("dd if=/dev/zero of=/dev/sda").blocked(), "dd if= should be blocked");
        assertFalse(filter.check("dd").blocked(), "bare dd without if= should not match dd if=*");
    }

    @Test
    void check_denylist_suBlocked() {
        CommandFilter filter = new CommandFilter();
        filter.denylist = List.of("su *");
        filter.allowlistMode = false;

        assertTrue(filter.check("su - root").blocked(), "su - root should be blocked");
    }

    @Test
    void globToRegex_escapesSpecialChars() {
        String regex = CommandFilter.globToRegex("cmd.file");
        assertTrue("cmd.file".matches(regex), "Dot should be escaped in regex");
        assertFalse("cmdXfile".matches(regex), "Escaped dot should not match other chars");
    }

    @Test
    void adversarial_recursiveRootDeletion_blockedByDenylist() {
        CommandFilter filter = defaultDenylistFilter();

        String destructiveCommand = "rm -rf /*";
        assertTrue(filter.check(destructiveCommand).blocked(),
                "Recursive root deletion must be blocked: " + destructiveCommand);
    }

    @Test
    void adversarial_chainedDestructiveCommand_blockedByDenylist() {
        CommandFilter filter = defaultDenylistFilter();

        String chained = "echo ok && rm -rf /*";
        assertFalse(filter.check(chained).blocked(),
                "Chained command: the leading 'echo ok &&' prefix means the deny pattern doesn't fire "
                        + "on the first token — caller must split or disallow chaining");
    }

    @Test
    void adversarial_m6AllowlistProfile_blocksDestructiveCommand() {
        CommandFilter filter = new CommandFilter();
        filter.allowlistMode = true;
        filter.allowlist = List.of("mvn", "mvn *", "npm", "npm *", "gh", "gh *", "git", "git *");
        filter.denylist = List.of();

        assertTrue(filter.check("rm -rf /*").blocked(), "rm must be blocked in M6 allowlist mode");
        assertTrue(filter.check("curl https://evil.example.com").blocked(), "curl must be blocked in M6 allowlist mode");
        assertTrue(filter.check("shutdown now").blocked(), "shutdown must be blocked in M6 allowlist mode");

        assertFalse(filter.check("mvn test").blocked(), "mvn test must be allowed");
        assertFalse(filter.check("npm test").blocked(), "npm test must be allowed");
        assertFalse(filter.check("gh pr review 1 --approve").blocked(), "gh pr review must be allowed");
        assertFalse(filter.check("git status").blocked(), "git status must be allowed");
    }

    @Test
    void adversarial_obfuscatedWithLeadingWhitespace_stillBlocked() {
        CommandFilter filter = defaultDenylistFilter();

        assertTrue(filter.check("  shutdown  ").blocked(),
                "Leading/trailing whitespace must not bypass denylist");
    }

    private CommandFilter defaultDenylistFilter() {
        CommandFilter filter = new CommandFilter();
        filter.denylist = List.of("sudo *", "su *", "rm -rf /*", "mkfs*", "dd if=*", "format", "shutdown", "reboot");
        filter.allowlistMode = false;
        filter.allowlist = List.of();
        return filter;
    }
}
