package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.dto.FileEntry;
import dev.omatheusmesmo.qlawkus.dto.FileResult;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class FileToolTest {

    @Inject
    @QlawTool
    FileTool fileTool;

    @Inject
    WorkspaceConfinement workspaceConfinement;

    @Test
    void writeFile_andReadFile_roundTrip() {
        String testPath = "filetool-test-write.txt";
        FileResult writeResult = fileTool.writeFile(testPath, "hello from FileTool");
        assertTrue(writeResult.success(), "Write should succeed: " + writeResult.error());

        FileResult readResult = fileTool.readFile(testPath);
        assertTrue(readResult.success(), "Read should succeed: " + readResult.error());
        assertTrue(readResult.content().contains("hello from FileTool"),
                "Content should match what was written, got: " + readResult.content());

        cleanup(testPath);
    }

    @Test
    void readFile_notFound_returnsError() {
        FileResult result = fileTool.readFile("nonexistent-file-xyz.txt");
        assertFalse(result.success(), "Read of nonexistent file should fail");
        assertTrue(result.error().contains("not found"), "Error should mention 'not found'");
    }

    @Test
    void readFile_directory_returnsError() {
        FileResult result = fileTool.readFile(".");
        assertFalse(result.success(), "Read of directory should fail");
        assertTrue(result.error().contains("directory"), "Error should mention 'directory'");
    }

    @Test
    void writeFile_createsParentDirectories() {
        String testPath = "filetool-test-dir/sub/deep.txt";
        FileResult result = fileTool.writeFile(testPath, "deep content");
        assertTrue(result.success(), "Write with nested dirs should succeed: " + result.error());

        FileResult read = fileTool.readFile(testPath);
        assertTrue(read.success(), "Should read the nested file");
        assertEquals("deep content", read.content());

        cleanup("filetool-test-dir");
    }

    @Test
    void writeFile_pathTraversal_blocked() {
        FileResult result = fileTool.writeFile("../../../tmp/evil.txt", "should be blocked");
        assertFalse(result.success(), "Path traversal write should be blocked");
    }

    @Test
    void readFile_pathTraversal_blocked() {
        FileResult result = fileTool.readFile("../../../etc/passwd");
        assertFalse(result.success(), "Path traversal read should be blocked");
    }

    @Test
    void listFiles_returnsEntries() {
        fileTool.writeFile("filetool-list-test/a.txt", "aaa");
        fileTool.writeFile("filetool-list-test/b.txt", "bbb");

        List<FileEntry> entries = fileTool.listFiles("filetool-list-test");
        assertFalse(entries.isEmpty(), "Should list at least 2 entries");
        assertTrue(entries.stream().anyMatch(e -> e.name().equals("a.txt")), "Should find a.txt");
        assertTrue(entries.stream().anyMatch(e -> e.name().equals("b.txt")), "Should find b.txt");

        cleanup("filetool-list-test");
    }

    @Test
    void listFiles_notFound_returnsError() {
        List<FileEntry> entries = fileTool.listFiles("nonexistent-dir-xyz");
        assertFalse(entries.isEmpty(), "Should return error entry");
        assertTrue(entries.get(0).name().contains("ERROR"), "Entry name should contain ERROR");
    }

    @Test
    void listFiles_pathTraversal_blocked() {
        List<FileEntry> entries = fileTool.listFiles("../../../etc");
        assertFalse(entries.isEmpty(), "Should return error entry");
        assertTrue(entries.get(0).name().contains("ERROR"), "Entry should indicate blocked path");
    }

    @Test
    void makeDirectory_createsDir() {
        String testPath = "filetool-mkdir-test";
        FileResult result = fileTool.makeDirectory(testPath);
        assertTrue(result.success(), "mkdir should succeed: " + result.error());

        List<FileEntry> entries = fileTool.listFiles(".");
        assertTrue(entries.stream().anyMatch(e -> e.name().equals("filetool-mkdir-test")),
                "New directory should appear in listing");

        cleanup(testPath);
    }

    @Test
    void makeDirectory_alreadyExists_returnsError() {
        fileTool.makeDirectory("filetool-mkdir-exists");
        FileResult result = fileTool.makeDirectory("filetool-mkdir-exists");
        assertFalse(result.success(), "mkdir on existing dir should fail");

        cleanup("filetool-mkdir-exists");
    }

    @Test
    void makeDirectory_pathTraversal_blocked() {
        FileResult result = fileTool.makeDirectory("../../../tmp/evil-dir");
        assertFalse(result.success(), "Path traversal mkdir should be blocked");
    }

    @Test
    void deleteFile_removesFile() {
        fileTool.writeFile("filetool-delete-test.txt", "to be deleted");
        FileResult result = fileTool.deleteFile("filetool-delete-test.txt");
        assertTrue(result.success(), "Delete should succeed: " + result.error());

        FileResult read = fileTool.readFile("filetool-delete-test.txt");
        assertFalse(read.success(), "Deleted file should not be readable");
    }

    @Test
    void deleteFile_notFound_returnsError() {
        FileResult result = fileTool.deleteFile("nonexistent-delete-xyz.txt");
        assertFalse(result.success(), "Delete of nonexistent file should fail");
    }

    @Test
    void deleteFile_directory_returnsError() {
        fileTool.makeDirectory("filetool-delete-dir-test");
        FileResult result = fileTool.deleteFile("filetool-delete-dir-test");
        assertFalse(result.success(), "Delete of directory should fail");
        assertTrue(result.error().contains("directory"), "Error should mention directory");

        cleanup("filetool-delete-dir-test");
    }

    @Test
    void deleteFile_pathTraversal_blocked() {
        FileResult result = fileTool.deleteFile("../../../etc/passwd");
        assertFalse(result.success(), "Path traversal delete should be blocked");
    }

    @Test
    void isBinary_detectsBinaryContent(@TempDir Path tempDir) throws IOException {
        Path binaryFile = tempDir.resolve("test.bin");
        byte[] binaryBytes = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
        Files.write(binaryFile, binaryBytes);
        assertTrue(FileTool.isBinary(binaryFile), "File with 0xFF byte should be detected as binary");
    }

    @Test
    void isBinary_textFile_returnsFalse(@TempDir Path tempDir) throws IOException {
        Path textFile = tempDir.resolve("test.txt");
        Files.writeString(textFile, "Hello, this is plain text\nLine 2\n");
        assertFalse(FileTool.isBinary(textFile), "Plain text file should not be detected as binary");
    }

    @Test
    void readFile_binaryFile_returnsError() throws IOException {
        String binaryPath = "filetool-binary-test.bin";
        Path resolved = workspaceConfinement.getWorkspacePath().resolve(binaryPath);
        try {
            Files.write(resolved, new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE});
            FileResult result = fileTool.readFile(binaryPath);
            assertFalse(result.success(), "Binary file read should fail");
            assertTrue(result.error().contains("Binary") || result.error().contains("binary"),
                "Error should mention binary, got: " + result.error());
        } finally {
            cleanup(binaryPath);
        }
    }

    @Test
    void readFile_largeFile_isTruncated() {
        long savedMaxRead = fileTool.maxReadSize;
        fileTool.maxReadSize = 100;
        try {
            String largeContent = "A".repeat(200);
            String largePath = "filetool-large-read-test.txt";
            fileTool.writeFile(largePath, largeContent);

            FileResult result = fileTool.readFile(largePath);
            assertTrue(result.success(), "Large file read should succeed (with truncation)");
            assertTrue(result.content().contains("TRUNCATED"), "Large file should be truncated, got: " + result.content());

            cleanup(largePath);
        } finally {
            fileTool.maxReadSize = savedMaxRead;
        }
    }

    @Test
    void writeFile_overwriteExistingFile_succeeds() {
        String path = "filetool-overwrite-test.txt";
        try {
            fileTool.writeFile(path, "original content");
            FileResult overwriteResult = fileTool.writeFile(path, "updated content");
            assertTrue(overwriteResult.success(), "Overwrite should succeed: " + overwriteResult.error());

            FileResult readResult = fileTool.readFile(path);
            assertTrue(readResult.content().contains("updated content"),
                "File should have updated content, got: " + readResult.content());
        } finally {
            cleanup(path);
        }
    }

    @Test
    void writeFile_existingLargeFile_refusesOverwrite() {
        long savedMaxWrite = fileTool.maxWriteSize;
        fileTool.maxWriteSize = 10;
        try {
            String path = "filetool-large-overwrite-test.txt";
            fileTool.writeFile(path, "A".repeat(50));

            FileResult result = fileTool.writeFile(path, "tiny");
            assertFalse(result.success(), "Should refuse to overwrite file larger than maxWriteSize");
            assertTrue(result.error().contains("write limit"), "Error should mention write limit, got: " + result.error());

            cleanup(path);
        } finally {
            fileTool.maxWriteSize = savedMaxWrite;
        }
    }

    @Test
    void readFile_emptyFile_returnsEmptyContent() {
        String path = "filetool-empty-test.txt";
        try {
            fileTool.writeFile(path, "");
            FileResult result = fileTool.readFile(path);
            assertTrue(result.success(), "Read of empty file should succeed: " + result.error());
            assertEquals("", result.content(), "Empty file should return empty content");
        } finally {
            cleanup(path);
        }
    }

    @Test
    void listFiles_emptyDirectory_returnsEmptyList() {
        String path = "filetool-empty-dir-test";
        try {
            fileTool.makeDirectory(path);
            List<FileEntry> entries = fileTool.listFiles(path);
            assertTrue(entries.isEmpty(), "Empty directory should return empty list");
        } finally {
            cleanup(path);
        }
    }

    @Test
    void fileConfig_encoding_defaultsToUtf8() {
        assertNotNull(fileTool.encoding, "Encoding should not be null");
        assertEquals("UTF-8", fileTool.encoding, "Default encoding should be UTF-8");
    }

    private void cleanup(String path) {
        Path resolved = workspaceConfinement.getWorkspacePath().resolve(path);
        try {
            if (Files.isDirectory(resolved)) {
                try (var stream = Files.walk(resolved)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                            });
                }
            } else {
                Files.deleteIfExists(resolved);
            }
        } catch (IOException ignored) {}
    }
}
