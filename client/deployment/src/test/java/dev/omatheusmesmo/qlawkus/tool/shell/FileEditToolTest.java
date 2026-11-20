package dev.omatheusmesmo.qlawkus.tool.shell;

import static org.junit.jupiter.api.Assertions.*;

import dev.omatheusmesmo.qlawkus.config.FileConfig;
import dev.omatheusmesmo.qlawkus.dto.FileEdit;
import dev.omatheusmesmo.qlawkus.dto.FileResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class FileEditToolTest {

    @TempDir
    Path directory;

    private Path workspace;
    private FileEditTool tool;

    @BeforeEach
    void setup() throws Exception {
        workspace = Files.createDirectory(directory.resolve("workspace"));
        tool = tool(true, 5_242_880, 10_485_760, 12000, "UTF-8");
    }

    @Test
    void exactReplacementReturnsUnifiedDiffAndPreservesOtherContent() throws Exception {
        Path file = file("sample.txt", "first\nold\nlast\n");
        FileResult result = tool.editFile("sample.txt", "old", "new");

        assertTrue(result.success(), result.error());
        assertEquals("first\nnew\nlast\n", Files.readString(file));
        assertEquals("Applied 1 edits (1 replacements).\n--- a/sample.txt\n+++ b/sample.txt\n"
                + "@@ -1,3 +1,3 @@\n first\n-old\n+new\n last\n", result.content());
    }

    @Test
    void validatesTheWholeOrderedBatchBeforeWriting() throws Exception {
        Path file = file("batch.txt", "one two\n");
        FileResult rejected = tool.editFile("batch.txt", null, null, false,
                List.of(new FileEdit("one", "ONE"), new FileEdit("missing", "TWO")));
        assertFalse(rejected.success());
        assertTrue(rejected.error().contains("Edit 2"), rejected.error());
        assertEquals("one two\n", Files.readString(file));

        FileResult accepted = tool.editFile("batch.txt", null, null, false,
                List.of(new FileEdit("one", "ONE"), new FileEdit("ONE two", "final")));
        assertTrue(accepted.success(), accepted.error());
        assertEquals("final\n", Files.readString(file));
    }

    @Test
    void ambiguousAndOverlappingMatchesNeverGuess() throws Exception {
        Path file = file("repeat.txt", "aaa");
        FileResult ambiguous = tool.editFile("repeat.txt", "aa", "x");
        assertFalse(ambiguous.success());
        assertTrue(ambiguous.error().contains("more than once"));
        assertEquals("aaa", Files.readString(file));
    }

    @Test
    void replaceAllUsesNonOverlappingMatchesAcrossTheBatch() throws Exception {
        Path file = file("repeat.txt", "one one\ntwo two\n");
        FileResult result = tool.editFile("repeat.txt", null, null, true,
                List.of(new FileEdit("one", "uno"), new FileEdit("two", "dos")));
        assertTrue(result.success(), result.error());
        assertEquals("uno uno\ndos dos\n", Files.readString(file));
        assertTrue(result.content().startsWith("Applied 2 edits (4 replacements)."));
    }

    @Test
    void preservesUtf8BomAndCrLfWhileAcceptingLfArguments() throws Exception {
        Path file = file("windows.txt", "\ufeffone\r\ntwo\r\nlast\r\n");
        FileResult result = tool.editFile("windows.txt", "one\ntwo", "ONE\nTWO");
        assertTrue(result.success(), result.error());
        assertArrayEquals("\ufeffONE\r\nTWO\r\nlast\r\n".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(file));
    }

    @Test
    void whitespaceNearMissIsRejectedWithoutFuzzyEdits() throws Exception {
        Path file = file("format.txt", "    value=1\n");
        FileResult result = tool.editFile("format.txt", "  value = 1", "value=2");
        assertFalse(result.success());
        assertTrue(result.error().contains("copy exact text"));
        assertEquals("    value=1\n", Files.readString(file));
    }

    @Test
    void pathsAreRelativeToTheConfiguredWorkspaceAndSymlinksCannotEscape() throws Exception {
        Path outside = directory.resolve("outside.txt");
        Files.writeString(outside, "outside");
        assertFalse(tool.editFile("../outside.txt", "outside", "changed").success());
        Files.createSymbolicLink(workspace.resolve("outside-link"), outside);
        assertFalse(tool.editFile("outside-link", "outside", "changed").success());
        Files.createSymbolicLink(workspace.resolve("outside-dir"), directory);
        assertFalse(tool.editFile("outside-dir/outside.txt", "outside", "changed").success());
        assertEquals("outside", Files.readString(outside));

        Path inside = file("actual.txt", "inside");
        Files.createSymbolicLink(workspace.resolve("alias"), inside);
        assertTrue(tool.editFile("alias", "inside", "changed").success());
        assertTrue(Files.isSymbolicLink(workspace.resolve("alias")));
        assertEquals("changed", Files.readString(inside));
    }

    @Test
    void enforcesReadAndEncodedWriteBudgetsWithoutChangingTheFile() throws Exception {
        Path file = file("bounded.txt", "old");
        FileEditTool smallRead = tool(true, 2, 20, 512, "UTF-8");
        assertFalse(smallRead.editFile("bounded.txt", "old", "new").success());
        FileEditTool smallWrite = tool(true, 20, 4, 512, "UTF-8");
        assertFalse(smallWrite.editFile("bounded.txt", "old", "ação").success());
        assertEquals("old", Files.readString(file));
    }

    @Test
    void rejectsBinaryOrInvalidEncodedTextAndUnrepresentableReplacement() throws Exception {
        Path binary = workspace.resolve("binary.bin");
        byte[] bytes = { 0, 1, 2 };
        Files.write(binary, bytes);
        assertFalse(tool.editFile("binary.bin", "x", "y").success());
        assertArrayEquals(bytes, Files.readAllBytes(binary));

        byte[] invalid = { (byte) 0xc3, 0x28 };
        Files.write(binary, invalid);
        assertFalse(tool.editFile("binary.bin", "x", "y").success());
        assertArrayEquals(invalid, Files.readAllBytes(binary));

        Path latin = file("latin.txt", "old");
        FileEditTool latinTool = tool(true, 100, 100, 512, "ISO-8859-1");
        assertFalse(latinTool.editFile("latin.txt", "old", "🐥").success());
        assertEquals("old", Files.readString(latin));
    }

    @Test
    void validatesArgumentsAndEnabledFlagBeforeAccessingTheFile() {
        assertFalse(tool.editFile("missing", "", "new").success());
        assertFalse(tool.editFile("missing", "old", null).success());
        assertFalse(tool.editFile("missing", "old", "new", null, List.of(new FileEdit("old", "new"))).success());
        assertFalse(tool.editFile("missing", null, null, null, List.of()).success());
        assertFalse(tool.editFile("missing", null, null, null, Arrays.asList((FileEdit) null)).success());
        FileEditTool disabled = tool(false, 100, 100, 512, "UTF-8");
        disabled.confinement = null;
        FileResult result = disabled.editFile("missing", "old", "new");
        assertFalse(result.success());
        assertTrue(result.error().contains("disabled"));
    }

    @Test
    void capsTheDiffPreviewAndMarksTruncation() throws Exception {
        Path file = file("long.txt", "old\n");
        tool = tool(true, 10000, 10000, 512, "UTF-8");
        FileResult result = tool.editFile("long.txt", "old", "x".repeat(4000));
        assertTrue(result.success(), result.error());
        assertEquals("x".repeat(4000) + "\n", Files.readString(file));
        assertTrue(result.content().length() <= 512);
        assertTrue(result.content().contains("[diff preview truncated]"));
    }

    @Test
    void concurrentCallsCannotBothReplaceTheSameOriginalText() throws Exception {
        Path file = file("concurrent.txt", "original");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return tool.editFile("concurrent.txt", "original", "first");
            });
            var second = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return tool.editFile("concurrent.txt", "original", "second");
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            FileResult firstResult = first.get(10, TimeUnit.SECONDS);
            FileResult secondResult = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(firstResult.success(), secondResult.success());
            assertEquals(firstResult.success() ? "first" : "second", Files.readString(file));
        }
    }

    @Test
    void preservesPosixPermissionsAndDoesNotRewriteUnchangedText() throws Exception {
        Path file = file("permissions.sh", "echo old\n");
        Set<PosixFilePermission> permissions = Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ);
        Files.setPosixFilePermissions(file, permissions);
        assertTrue(tool.editFile("permissions.sh", "old", "new").success());
        assertEquals(permissions, Files.getPosixFilePermissions(file));
        var modified = Files.getLastModifiedTime(file);
        assertTrue(tool.editFile("permissions.sh", "new", "new").success());
        assertEquals(modified, Files.getLastModifiedTime(file));
    }

    @Test
    void commitRetainsExternalChangesEvenWhenSizeAndTimestampAreUnchanged() throws Exception {
        Path file = file("external.txt", "first");
        var snapshot = Files.readAttributes(file, BasicFileAttributes.class);
        byte[] original = Files.readAllBytes(file);
        Files.writeString(file, "other");
        Files.setLastModifiedTime(file, snapshot.lastModifiedTime());

        var rejected = assertThrows(ExactFileEdits.Rejected.class,
                () -> tool.commitEdit(file, file, snapshot, original, "edit!".getBytes(StandardCharsets.UTF_8), 100));
        assertEquals("file_changed", rejected.outcome);
        assertEquals("other", Files.readString(file));
        try (var files = Files.list(workspace)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".qlawkus-edit-")));
        }
    }

    private Path file(String name, String content) throws Exception {
        Path path = workspace.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private FileEditTool tool(boolean enabled, long maxRead, long maxWrite, int maxDiff, String encoding) {
        FileEditTool result = new FileEditTool();
        result.config = new FileConfig() {
            public long maxReadSize() { return maxRead; }
            public long maxWriteSize() { return maxWrite; }
            public String encoding() { return encoding; }
            public Read read() { return null; }
            public Edit edit() {
                return new Edit() {
                    public boolean enabled() { return enabled; }
                    public int maxDiffChars() { return maxDiff; }
                };
            }
        };
        WorkspaceConfinement confinement = new WorkspaceConfinement();
        confinement.workspaceRoot = workspace.toString();
        confinement.restrictToWorkspace = true;
        result.confinement = confinement;
        return result;
    }
}
