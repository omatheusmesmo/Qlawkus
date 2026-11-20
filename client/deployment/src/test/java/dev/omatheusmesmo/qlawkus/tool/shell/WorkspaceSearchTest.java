package dev.omatheusmesmo.qlawkus.tool.shell;

import static org.junit.jupiter.api.Assertions.*;

import dev.omatheusmesmo.qlawkus.config.FileConfig;
import dev.omatheusmesmo.qlawkus.config.ShellConfig;
import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pure filesystem tests: no provider, Quarkus server, child process or external binary. */
class WorkspaceSearchTest {
    @TempDir
    Path root;

    @TempDir
    Path outside;

    WorkspaceConfinement workspace;

    @BeforeEach
    void workspace() {
        workspace = new WorkspaceConfinement();
        workspace.workspaceRoot = root.toString();
        workspace.restrictToWorkspace = true;
    }

    @Test
    void globstarMatchesZeroDirectoriesAndCharacterClassesWork() throws Exception {
        write("Main.java", "root");
        write("src/a/A1.java", "first");
        write("src/a/B2.java", "second");
        write("src/a/C3.java", "third");
        assertEquals("Main.java\nsrc/a/A1.java\nsrc/a/B2.java\nsrc/a/C3.java", glob().globFiles("**/*.java", null, null));
        assertEquals("src/a/A1.java\nsrc/a/B2.java", glob().globFiles("src/**/[AB]?.java", null, null));
        assertEquals("Main.java", glob().globFiles("*.java", null, null));
    }

    @Test
    void nestedGitignoreNegationsAndIgnoredParentsFollowGitSemantics() throws Exception {
        write(".gitignore", "*.log\n!/keep.log\nhidden/\n!hidden/keep.log\n/cache\n");
        write("nested/.gitignore", "!keep.log\nsecret.txt\n");
        write("drop.log", "ignored");
        write("keep.log", "kept");
        write("nested/drop.log", "ignored");
        write("nested/keep.log", "kept");
        write("nested/secret.txt", "ignored");
        write("nested/readme.txt", "kept");
        write("hidden/keep.log", "ignored parent");
        write("cache/root.txt", "ignored anchored directory");
        write("nested/cache/child.txt", "kept");
        assertEquals("keep.log\nnested/keep.log", glob().globFiles("**/*.log", null, null));
        assertEquals("nested/keep.log", glob().globFiles("**/*.log", "nested", null));
        assertEquals("nested/cache/child.txt\nnested/readme.txt", glob().globFiles("**/*.txt", null, null));
        assertEquals("No matches found.", glob().globFiles("**", "hidden", null));
    }

    @Test
    void gitignoreSupportsEscapedSpacesAndNegationOfAnOpenParent() throws Exception {
        write(".gitignore", "literal\\ space.txt\noutputs/*\n!outputs/open/\n");
        write("literal space.txt", "hidden");
        write("outputs/closed/file.txt", "hidden");
        write("outputs/open/file.txt", "visible");
        assertEquals("outputs/open/file.txt", glob().globFiles("**/*.txt", null, null));
    }

    @Test
    void grepSupportsRegexLiteralCaseAndBasenameInclude() throws Exception {
        write("a.java", "alpha\nFoO[bar]\nomega\n");
        write("nested/b.java", "FOO[bar]\n");
        write("ignore.txt", "foo[bar]\n");
        String found = grep().grepFiles("foo\\[bar\\]", null, "*.java", true, false, null, null);
        assertEquals("a.java:2:FoO[bar]\nnested/b.java:1:FOO[bar]", found);
        assertEquals("ignore.txt:1:foo[bar]", grep().grepFiles("foo[bar]", "ignore.txt", null, false, true, 0, null));
        assertEquals("a.java:1:alpha\na.java:3:omega", grep().grepFiles("^(alpha|omega)$", "a.java", null, null, null, 0, null));
    }

    @Test
    void grepContextIsBoundedAndOverlappingContextIsNotDuplicated() throws Exception {
        write("one.txt", "before\nhit one\nbetween\nhit two\nafter\nlast\n");
        String result = grep().grepFiles("hit", null, null, false, false, 1, null);
        assertEquals("one.txt-1-before\none.txt:2:hit one\none.txt-3-between\none.txt:4:hit two\none.txt-5-after", result);
        String bounded = grep("qlawkus.shell.search.max-context-lines", "1")
                .grepFiles("hit", null, null, null, null, 100, 1);
        assertTrue(bounded.contains("one.txt-3-between"), bounded);
        assertFalse(bounded.contains("one.txt:4:"), bounded);
        assertTrue(bounded.contains("result limit reached"), bounded);
    }

    @Test
    void resultAndOutputBudgetsAreVisibleAndSorted() throws Exception {
        write("c.txt", "hit\n");
        write("a.txt", "hit\n");
        write("b.txt", "hit\n");
        String glob = glob("qlawkus.shell.glob.max-results", "2").globFiles("**/*.txt", null, 999);
        assertTrue(glob.startsWith("a.txt\nb.txt"), glob);
        assertTrue(glob.contains("result limit reached"), glob);
        String grep = grep("qlawkus.shell.grep.max-results", "2").grepFiles("hit", null, null, null, null, 0, 999);
        assertTrue(grep.startsWith("a.txt:1:hit\nb.txt:1:hit"), grep);
        assertTrue(grep.contains("result limit reached"), grep);
        write("a.txt", "hit " + "x".repeat(3000));
        String output = grep("qlawkus.shell.search.max-output-chars", "1024")
                .grepFiles("hit", null, null, null, null, 0, 100);
        assertTrue(output.length() <= 1024, "length=" + output.length());
        assertTrue(output.startsWith("No matches returned."), output);
        assertTrue(output.contains("output character budget reached"), output);
    }

    @Test
    void visitsDepthAndBytesHaveExplicitLimits() throws Exception {
        write("a.txt", "abc\n");
        write("b.txt", "def\n");
        write("deep/nested/end.txt", "ghi\n");
        assertTrue(glob("qlawkus.shell.search.max-visited-paths", "1").globFiles("**", null, null)
                .contains("visited-path limit reached"));
        String depth = glob("qlawkus.shell.search.max-depth", "1").globFiles("**", null, null);
        assertFalse(depth.contains("end.txt"), depth);
        assertTrue(depth.contains("depth limit reached"), depth);
        String bytes = grep("qlawkus.shell.search.max-total-bytes", "5")
                .grepFiles(".", null, null, null, null, 0, null);
        assertTrue(bytes.contains("a.txt:1:abc"), bytes);
        assertFalse(bytes.contains("b.txt:1:def"), bytes);
        assertTrue(bytes.contains("total byte budget reached"), bytes);
    }

    @Test
    void skipsBinaryInvalidEncodingLargeFilesAndOversizedLines() throws Exception {
        Files.write(root.resolve("binary.dat"), new byte[] { 'h', 'i', 't', 0 });
        Files.write(root.resolve("invalid.dat"), new byte[] { (byte) 0xff, (byte) 0xfe, 'h', 'i', 't' });
        write("large.txt", "hit".repeat(100));
        write("small.txt", "hit ação\n");
        String result = grep("qlawkus.shell.search.max-file-bytes", "32")
                .grepFiles("hit", null, null, null, null, 0, null);
        assertTrue(result.contains("small.txt:1:hit ação"), result);
        assertFalse(result.contains("large.txt:"), result);
        assertFalse(result.contains("binary.dat:"), result);
        assertFalse(result.contains("invalid.dat:"), result);
        assertTrue(result.contains("Skipped 3"), result);
        write("long.txt", "x".repeat(65537) + "hit\n");
        assertTrue(grep().grepFiles("hit", "long.txt", null, null, null, 0, null)
                .contains("1 lines longer than 65536"));
    }

    @Test
    void preservesConfiguredEncodingAndBomWithoutReplacingInvalidText() throws Exception {
        Files.writeString(root.resolve("utf16.txt"), "\ufeffação\r\nnext\r\n", StandardCharsets.UTF_16LE);
        assertEquals("utf16.txt:1:ação", grep("qlawkus.file.encoding", "UTF-16LE")
                .grepFiles("ação", "utf16.txt", null, null, true, 0, null));
    }

    @Test
    void refusesTraversalAndDoesNotFollowFileDirectoryOrIgnoreSymlinks() throws Exception {
        Files.writeString(outside.resolve("outside.txt"), "outside-secret");
        Files.createSymbolicLink(root.resolve("file-link.txt"), outside.resolve("outside.txt"));
        Files.createSymbolicLink(root.resolve("dir-link"), outside);
        write("real.txt", "visible");
        write(".git/private.txt", "must not search");
        assertEquals("real.txt", glob().globFiles("**/*.txt", null, null));
        assertTrue(glob().globFiles("**", "../", null).startsWith("Error:"));
        assertTrue(grep().grepFiles("outside", "file-link.txt", null, null, null, 0, null).startsWith("Error:"));
        assertTrue(grep().grepFiles("outside", "dir-link", null, null, null, 0, null).startsWith("Error:"));
        write("unsafe/secret.txt", "must not search");
        Files.createSymbolicLink(root.resolve("unsafe/.gitignore"), outside.resolve("outside.txt"));
        String result = glob().globFiles("**/*.txt", null, null);
        assertFalse(result.contains("unsafe/secret.txt"), result);
    }

    @Test
    void invalidPatternsAndDisabledToolsDoNotSearch() {
        assertTrue(glob().globFiles("[", null, null).startsWith("Error:"));
        assertTrue(glob().globFiles("**", null, 0).startsWith("Error:"));
        assertTrue(grep().grepFiles("(?=x)", null, null, null, null, 0, null).contains("unsupported regex"));
        assertTrue(grep().grepFiles("(x)\\1", null, null, null, null, 0, null).contains("unsupported regex"));
        assertTrue(grep().grepFiles("x", null, null, null, null, -1, null).startsWith("Error:"));
        assertTrue(glob("qlawkus.shell.glob.enabled", "false").globFiles("**", outside.toString(), null).contains("disabled"));
        assertTrue(grep("qlawkus.shell.grep.enabled", "false")
                .grepFiles("x", outside.toString(), null, null, null, 0, null).contains("disabled"));
    }

    @Test
    void hostileRegexIsFiniteAndDeadlineCannotBecomeUnlimited() throws Exception {
        write("line.txt", "a".repeat(60000) + "!");
        assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                assertEquals("No matches found.", grep().grepFiles("(a+)+$", null, null, null, null, 0, null)));
        assertTrue(glob("qlawkus.shell.search.timeout", "0s").globFiles("**", null, null).contains("deadline reached"));
        assertTrue(grep("qlawkus.shell.search.timeout", "0s")
                .grepFiles("x", null, null, null, null, 0, null).contains("deadline reached"));
    }

    @Test
    void hostileIgnoreRulesCannotTrapTheUpstreamBacktrackingMatcher() throws Exception {
        write("safe.txt", "hit");
        write("unsafe/.gitignore", "*a".repeat(14) + "b\n");
        write("unsafe/" + "a".repeat(200), "must not be searched");
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            String result = glob().globFiles("**", null, null);
            assertTrue(result.startsWith("safe.txt"), result);
            assertTrue(result.contains("Skipped 1"), result);
            assertTrue(result.contains(".gitignore rules were unreadable, oversized or exceeded safety limits"), result);
            assertFalse(result.contains("unsafe/"), result);
        });
    }

    @Test
    void ignoreRulesAlsoBoundAdjacentStarsGlobstarCountAndAggregateRules() throws Exception {
        write("safe.txt", "hit");
        write("adjacent/.gitignore", "*".repeat(1000) + "b\n");
        write("globstars/.gitignore", "**/**/**/b\n");
        write("many/.gitignore", "*.log\n".repeat(513));
        write("long/.gitignore", "a".repeat(1025));
        write("binary/.gitignore", "\0");
        String result = glob().globFiles("**", null, null);
        assertTrue(result.startsWith("safe.txt"), result);
        assertTrue(result.contains("Skipped 5 directories"), result);
    }

    @Test
    void gitignoreBomDoesNotTurnItsFirstRuleIntoALiteralBomPrefix() throws Exception {
        write(".gitignore", "\ufeff*.secret\n");
        write("private.secret", "must not be returned");
        write("public.txt", "hit");
        assertEquals("public.txt", glob().globFiles("**/*.txt", null, null));
        assertEquals("No matches found.", glob().globFiles("**/*.secret", null, null));
    }

    @Test
    void unicodeGlobLiteralsAndClippedTextKeepSurrogatePairsValid() throws Exception {
        write("ação😀.txt", "hit😀 text");
        assertEquals("ação😀.txt", glob().globFiles("**/ação😀.txt", null, null));
        String result = grep("qlawkus.shell.search.max-line-length", "4")
                .grepFiles("hit", null, null, null, null, 0, null);
        assertEquals("ação😀.txt:1:hit [line truncated]", result);
        write("line\nbreak.txt", "hit");
        assertEquals("line\\nbreak.txt", glob().globFiles("**/line?break.txt", null, null));
    }

    private GlobTool glob(String... values) {
        SmallRyeConfig config = config(values);
        return new GlobTool(workspace, config.getConfigMapping(ShellConfig.class));
    }

    private GrepTool grep(String... values) {
        SmallRyeConfig config = config(values);
        return new GrepTool(workspace, config.getConfigMapping(ShellConfig.class), config.getConfigMapping(FileConfig.class));
    }

    private SmallRyeConfig config(String... values) {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder().withMapping(ShellConfig.class).withMapping(FileConfig.class)
                .withConverter(Duration.class, 100, new DurationConverter());
        for (int i = 0; i < values.length; i += 2) {
            builder.withDefaultValue(values[i], values[i + 1]);
        }
        return builder.build();
    }

    private void write(String path, String content) throws Exception {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
