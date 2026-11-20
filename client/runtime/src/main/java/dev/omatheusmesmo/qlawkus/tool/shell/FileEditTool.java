package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.config.FileConfig;
import dev.omatheusmesmo.qlawkus.dto.FileEdit;
import dev.omatheusmesmo.qlawkus.dto.FileResult;
import dev.omatheusmesmo.qlawkus.dto.SecurityResult;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Local file editing; no shell, subprocess, database or network capability is required. */
@QlawTool
@ApplicationScoped
public class FileEditTool {

    private static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    private static final int MAX_EDITS = 50;
    private static final Object[] LOCKS = new Object[64];

    static {
        Arrays.setAll(LOCKS, ignored -> new Object());
    }

    @Inject
    FileConfig config;

    @Inject
    WorkspaceConfinement confinement;

    public FileResult editFile(String path, String oldText, String newText) {
        return editFile(path, oldText, newText, null, null);
    }

    @Tool("Edit an existing workspace text file by exact replacement. Supply oldText/newText OR an edits array "
            + "of {oldText,newText} pairs (max 50), applied in order. Optional replaceAll defaults false: ambiguous "
            + "or missing matches reject the whole batch without writing. No fuzzy whitespace matching. "
            + "Preserves UTF-8 BOM and CRLF/LF. Returns a bounded unified diff after an atomic write.")
    public FileResult editFile(@P("Existing file path, relative to the workspace root") String path,
            @P(value = "Exact text to replace; omit when using edits", required = false) String oldText,
            @P(value = "Replacement text, empty to delete; omit when using edits", required = false) String newText,
            @P(value = "Replace every non-overlapping occurrence for each edit; default false", required = false) Boolean replaceAll,
            @P(value = "Ordered batch of oldText/newText replacements, alternative to a single pair", required = false) List<FileEdit> edits) {
        String outcome = "invalid_input";
        int editCount = 0;
        int replacements = 0;
        try {
            if (!config.edit().enabled()) {
                outcome = "disabled";
                return FileResult.fail("File editing is disabled (qlawkus.file.edit.enabled=false).");
            }
            List<FileEdit> batch = validateEdits(oldText, newText, edits);
            editCount = batch.size();
            if (path == null || path.isBlank()) {
                throw new ExactFileEdits.Rejected("invalid_input", "Provide an existing workspace file path.");
            }
            Path requested = Path.of(path);
            Path candidate = (requested.isAbsolute() ? requested : confinement.getWorkspacePath().resolve(requested))
                    .normalize();
            checkPath(candidate);
            Path target = candidate.toRealPath();
            checkPath(target);
            Object lock = LOCKS[Math.floorMod(target.hashCode(), LOCKS.length)];
            synchronized (lock) {
                // Re-resolve after waiting on another edit of this file, including symlink aliases.
                if (!candidate.toRealPath().equals(target)) {
                    throw changed();
                }
                checkPath(target);
                BasicFileAttributes before = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!before.isRegularFile()) {
                    throw new ExactFileEdits.Rejected("invalid_input", "Path must be an existing regular text file.");
                }
                if (!Files.isWritable(target)) {
                    throw new ExactFileEdits.Rejected("invalid_input", "File is not writable. No edits were written.");
                }
                int maxRead = sizeLimit(config.maxReadSize());
                int maxWrite = sizeLimit(config.maxWriteSize());
                byte[] originalBytes = read(target, Math.min(maxRead, maxWrite));
                Charset charset = Charset.forName(config.encoding());
                boolean bom = charset.equals(StandardCharsets.UTF_8) && hasUtf8Bom(originalBytes);
                int prefix = bom ? 3 : 0;
                String original = charset.newDecoder()
                        .decode(ByteBuffer.wrap(originalBytes, prefix, originalBytes.length - prefix)).toString();
                if (original.indexOf('\0') >= 0) {
                    throw new ExactFileEdits.Rejected("binary", "Binary files cannot be edited as text.");
                }
                ExactFileEdits.Result edited = ExactFileEdits.apply(original, batch, Boolean.TRUE.equals(replaceAll), maxWrite);
                byte[] updatedBytes = encode(edited.content(), charset, bom, maxWrite);
                replacements = edited.replacements();
                if (Arrays.equals(originalBytes, updatedBytes)) {
                    outcome = "matched";
                    return FileResult.ok("Exact text matched; no file bytes changed.");
                }
                String name = target.startsWith(confinement.getWorkspacePath())
                        ? confinement.getWorkspacePath().relativize(target).toString() : target.getFileName().toString();
                String summary = "Applied " + batch.size() + " edits (" + replacements + " replacements).\n";
                int maxDiff = Math.clamp(config.edit().maxDiffChars(), 512, 64000);
                String response = summary + FileEditDiff.render(name, original, edited.content(), maxDiff - summary.length());
                commitEdit(candidate, target, before, originalBytes, updatedBytes, maxRead);
                outcome = "matched";
                return FileResult.ok(response);
            }
        } catch (ExactFileEdits.Rejected e) {
            outcome = e.outcome;
            return FileResult.fail(e.getMessage());
        } catch (CharacterCodingException e) {
            outcome = "encoding";
            return FileResult.fail("File or replacement is not valid in the configured encoding. No edits were written.");
        } catch (AtomicMoveNotSupportedException e) {
            outcome = "io_error";
            return FileResult.fail("This filesystem does not support atomic replacement. No edits were written.");
        } catch (IOException e) {
            outcome = "io_error";
            return FileResult.fail("Could not edit the file. Verify that it exists and is accessible. No edits were written.");
        } catch (IllegalArgumentException e) {
            return FileResult.fail("Invalid path or file encoding. No edits were written.");
        } finally {
            // Closed outcomes and counts only: never log paths, file text, or edit arguments.
            Log.infof("FILE_EDIT | outcome=%s | edits=%d | replacements=%d", outcome, editCount, replacements);
        }
    }

    private List<FileEdit> validateEdits(String oldText, String newText, List<FileEdit> edits) {
        List<FileEdit> batch;
        if (edits != null) {
            if (oldText != null || newText != null) {
                throw new ExactFileEdits.Rejected("invalid_input", "Supply either oldText/newText or edits, not both.");
            }
            batch = edits;
        } else {
            batch = List.of(new FileEdit(oldText, newText));
        }
        if (batch.isEmpty() || batch.size() > MAX_EDITS) {
            throw new ExactFileEdits.Rejected("invalid_input", "Supply between 1 and " + MAX_EDITS + " edits.");
        }
        long chars = 0;
        for (FileEdit edit : batch) {
            if (edit == null || edit.oldText() == null || edit.oldText().isEmpty() || edit.newText() == null) {
                throw new ExactFileEdits.Rejected("invalid_input", "Each edit needs non-empty oldText and non-null newText.");
            }
            chars += (long) edit.oldText().length() + edit.newText().length();
        }
        if (chars > 2L * MAX_FILE_BYTES) {
            throw new ExactFileEdits.Rejected("limit", "Edit arguments exceed the 32 MiB character budget.");
        }
        return List.copyOf(batch);
    }

    private void checkPath(Path path) {
        SecurityResult security = confinement.check(path.toString());
        if (security.blocked()) {
            throw new ExactFileEdits.Rejected("blocked", "Path escapes the workspace root. No edits were written.");
        }
    }

    private static int sizeLimit(long configured) {
        if (configured < 1) {
            throw new ExactFileEdits.Rejected("limit", "File read/write size limits must be positive.");
        }
        return (int) Math.min(configured, MAX_FILE_BYTES);
    }

    private static byte[] read(Path target, int maxBytes) throws IOException {
        try (var input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new ExactFileEdits.Rejected("limit", "File exceeds the edit read/write size limit (at most 16 MiB).");
            }
            return bytes;
        }
    }

    private static byte[] encode(String content, Charset charset, boolean bom, int maxBytes) throws CharacterCodingException {
        ByteBuffer encoded = charset.newEncoder().encode(CharBuffer.wrap(content));
        int prefix = bom ? 3 : 0;
        if ((long) encoded.remaining() + prefix > maxBytes) {
            throw new ExactFileEdits.Rejected("limit", "Edited content exceeds the write-size limit. No edits were written.");
        }
        byte[] result = new byte[encoded.remaining() + prefix];
        if (bom) {
            result[0] = (byte) 0xef;
            result[1] = (byte) 0xbb;
            result[2] = (byte) 0xbf;
        }
        encoded.get(result, prefix, encoded.remaining());
        return result;
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf;
    }

    void commitEdit(Path candidate, Path target, BasicFileAttributes before, byte[] original,
            byte[] updated, int maxRead) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), ".qlawkus-edit-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(updated);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            PosixFileAttributeView permissions = Files.getFileAttributeView(target, PosixFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (permissions != null) {
                Files.setPosixFilePermissions(temporary, permissions.readAttributes().permissions());
            }
            if (!candidate.toRealPath().equals(target)) {
                throw changed();
            }
            checkPath(target);
            BasicFileAttributes current = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!current.isRegularFile() || !Files.isWritable(target) || current.size() != before.size()
                    || !Objects.equals(current.fileKey(), before.fileKey())
                    || !current.lastModifiedTime().equals(before.lastModifiedTime())
                    || !Arrays.equals(original, read(target, maxRead))) {
                throw changed();
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static ExactFileEdits.Rejected changed() {
        return new ExactFileEdits.Rejected("file_changed", "File changed during editing. Re-read it and retry; no edits were written.");
    }
}
