package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.config.FileConfig;
import dev.omatheusmesmo.qlawkus.dto.FileEntry;
import dev.omatheusmesmo.qlawkus.dto.FileResult;
import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@ClawTool
public class FileTool {

    private static final long KB = 1024;
    private static final long MB = 1024 * KB;

    @Inject
    FileConfig fileConfig;

    @Inject
    WorkspaceConfinement workspaceConfinement;

    /**
     * Maximum file size in bytes allowed for read operations. Default: 5242880 (5MB).
     */
    long maxReadSize;

    /**
     * Maximum file size in bytes allowed for write operations. Default: 10485760 (10MB).
     */
    long maxWriteSize;

    /**
     * Character encoding for file read/write operations. Default: {@code UTF-8}.
     */
    String encoding;

    @PostConstruct
    void init() {
        this.maxReadSize = fileConfig.maxReadSize();
        this.maxWriteSize = fileConfig.maxWriteSize();
        this.encoding = fileConfig.encoding();
    }

    @Tool("Read a file's content within the workspace. " +
            "Parameters: path (required) — file path relative to workspace root. " +
            "Returns file content as string. Binary files return an error suggesting xxd via ShellTool. " +
            "Large files are truncated beyond the configured max-read-size (default 5MB).")
    public FileResult readFile(String path) {
        SecurityResult security = checkPath(path);
        if (security.blocked()) {
            return FileResult.fail(security.reason());
        }

        Path resolved = resolve(path);
        if (resolved == null) {
            return FileResult.fail("Cannot resolve path: " + path);
        }

        if (!Files.exists(resolved)) {
            return FileResult.fail("File not found: " + path);
        }

        if (Files.isDirectory(resolved)) {
            return FileResult.fail("Path is a directory, not a file: " + path);
        }

        if (isBinary(resolved)) {
            return FileResult.fail("Binary file detected. Use ShellTool.runCommand('xxd " + path + "') to inspect binary content.");
        }

        try {
            long fileSize = Files.size(resolved);
            if (fileSize > maxReadSize) {
                String content = Files.readString(resolved, resolveCharset());
                int truncateAt = (int) Math.min(maxReadSize, content.length());
                Log.infof("FILE_READ | path=%s | size=%d | truncated=true", path, fileSize);
                return FileResult.ok(content.substring(0, truncateAt)
                        + "\n[TRUNCATED — file exceeds " + (maxReadSize / MB) + "MB read limit]");
            }
            String content = Files.readString(resolved, resolveCharset());
            Log.infof("FILE_READ | path=%s | size=%d", path, fileSize);
            return FileResult.ok(content);
        } catch (IOException e) {
            Log.errorf(e, "FILE_READ | path=%s | error=%s", path, e.getMessage());
            return FileResult.fail("Failed to read file: " + e.getMessage());
        }
    }

    @Tool("Write content to a file within the workspace, creating parent directories if needed. " +
            "Refuses to overwrite files larger than max-write-size (default 10MB). " +
            "Parameters: path (required) — file path relative to workspace root, " +
            "content (required) — the text content to write")
    public FileResult writeFile(String path, String content) {
        SecurityResult security = checkPath(path);
        if (security.blocked()) {
            return FileResult.fail(security.reason());
        }

        Path resolved = resolve(path);
        if (resolved == null) {
            return FileResult.fail("Cannot resolve path: " + path);
        }

        if (Files.exists(resolved)) {
            try {
                if (Files.size(resolved) > maxWriteSize) {
                    return FileResult.fail("File exceeds " + (maxWriteSize / MB) + "MB write limit (" + (Files.size(resolved) / MB) + "MB). Refusing to overwrite.");
                }
            } catch (IOException e) {
                return FileResult.fail("Failed to check file size: " + e.getMessage());
            }
        }

        try {
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content, resolveCharset(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Log.infof("FILE_WRITE | path=%s | size=%d", path, content.length());
            return FileResult.ok("Written " + content.length() + " chars to " + path);
        } catch (IOException e) {
            Log.errorf(e, "FILE_WRITE | path=%s | error=%s", path, e.getMessage());
            return FileResult.fail("Failed to write file: " + e.getMessage());
        }
    }

    @Tool("List files and directories at a path within the workspace. " +
            "Returns entries with name, type (file/directory), size, and lastModified. " +
            "Parameter: path (required) — directory path relative to workspace root")
    public List<FileEntry> listFiles(String path) {
        SecurityResult security = checkPath(path);
        if (security.blocked()) {
            return List.of(new FileEntry("ERROR: " + security.reason(), "", 0, 0));
        }

        Path resolved = resolve(path);
        if (resolved == null) {
            return List.of(new FileEntry("ERROR: Cannot resolve path: " + path, "", 0, 0));
        }

        if (!Files.exists(resolved)) {
            return List.of(new FileEntry("ERROR: Path not found: " + path, "", 0, 0));
        }

        if (!Files.isDirectory(resolved)) {
            return List.of(new FileEntry("ERROR: Path is not a directory: " + path, "", 0, 0));
        }

        List<FileEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(resolved)) {
            stream.forEach(p -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    entries.add(new FileEntry(
                            p.getFileName().toString(),
                            attrs.isDirectory() ? "directory" : "file",
                            attrs.size(),
                            attrs.lastModifiedTime().toMillis()
                    ));
                } catch (IOException e) {
                    entries.add(new FileEntry(p.getFileName().toString(), "unknown", 0, 0));
                }
            });
        } catch (IOException e) {
            return List.of(new FileEntry("ERROR: " + e.getMessage(), "", 0, 0));
        }

        Log.infof("FILE_LIST | path=%s | entries=%d", path, entries.size());
        return entries;
    }

    @Tool("Create a directory (and parent directories) within the workspace. " +
            "Parameter: path (required) — directory path relative to workspace root")
    public FileResult makeDirectory(String path) {
        SecurityResult security = checkPath(path);
        if (security.blocked()) {
            return FileResult.fail(security.reason());
        }

        Path resolved = resolve(path);
        if (resolved == null) {
            return FileResult.fail("Cannot resolve path: " + path);
        }

        if (Files.exists(resolved)) {
            return FileResult.fail("Path already exists: " + path);
        }

        try {
            Files.createDirectories(resolved);
            Log.infof("FILE_MKDIR | path=%s", path);
            return FileResult.ok("Directory created: " + path);
        } catch (IOException e) {
            Log.errorf(e, "FILE_MKDIR | path=%s | error=%s", path, e.getMessage());
            return FileResult.fail("Failed to create directory: " + e.getMessage());
        }
    }

    @Tool("Delete a file within the workspace. Only deletes files, not directories (use ShellTool for rmdir). " +
            "Parameter: path (required) — file path relative to workspace root")
    public FileResult deleteFile(String path) {
        SecurityResult security = checkPath(path);
        if (security.blocked()) {
            return FileResult.fail(security.reason());
        }

        Path resolved = resolve(path);
        if (resolved == null) {
            return FileResult.fail("Cannot resolve path: " + path);
        }

        if (!Files.exists(resolved)) {
            return FileResult.fail("File not found: " + path);
        }

        if (Files.isDirectory(resolved)) {
            return FileResult.fail("Path is a directory. Use ShellTool.runCommand('rmdir " + path + "') to remove directories.");
        }

        try {
            Files.delete(resolved);
            Log.infof("FILE_DELETE | path=%s", path);
            return FileResult.ok("Deleted: " + path);
        } catch (IOException e) {
            Log.errorf(e, "FILE_DELETE | path=%s | error=%s", path, e.getMessage());
            return FileResult.fail("Failed to delete file: " + e.getMessage());
        }
    }

    private Charset resolveCharset() {
        if (encoding != null && !encoding.isBlank()) {
            try {
                return Charset.forName(encoding.trim());
            } catch (Exception e) {
                Log.warnf("Invalid charset '%s', falling back to UTF-8: %s", encoding, e.getMessage());
            }
        }
        return StandardCharsets.UTF_8;
    }

    private SecurityResult checkPath(String path) {
        return workspaceConfinement.check(path);
    }

    private Path resolve(String path) {
        Path workspace = workspaceConfinement.getWorkspacePath();
        if (path == null || path.isBlank()) {
            return workspace;
        }
        Path resolved = workspace.resolve(path).normalize();
        if (resolved.startsWith(workspace)) {
            return resolved;
        }
        Path canonical = workspaceConfinement.resolveCanonical(path);
        return canonical != null ? canonical : resolved;
    }

    public static boolean isBinary(Path file) {
        try (var is = Files.newInputStream(file)) {
            byte[] bytes = is.readNBytes(8192);
            for (byte b : bytes) {
                if (b < 0 && b != '\t' && b != '\n' && b != '\r') {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
