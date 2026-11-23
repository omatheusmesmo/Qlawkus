package dev.omatheusmesmo.qlawkus.artifacts;

import dev.omatheusmesmo.qlawkus.config.WorkspaceConfig;
import dev.omatheusmesmo.qlawkus.tool.shell.ConfinedFileRead;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import dev.omatheusmesmo.qlawkus.tool.shell.WorkspaceConfinement;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.regex.Pattern;

@ApplicationScoped
class WorkspaceArtifact {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("[\\p{L}\\p{M}\\p{N}][\\p{L}\\p{M}\\p{N} _.-]{0,127}");
    private static final Pattern PRIVATE_KEY = Pattern.compile("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----");

    private final WorkspaceConfinement workspace;
    private final WorkspaceConfig workspaceConfig;
    private final SecretRedactor redactor;

    WorkspaceArtifact(WorkspaceConfinement workspace, WorkspaceConfig workspaceConfig, SecretRedactor redactor) {
        this.workspace = workspace;
        this.workspaceConfig = workspaceConfig;
        this.redactor = redactor;
    }

    File read(String input) throws IOException {
        if (input == null || input.isBlank() || input.length() > 4096) {
            throw new IllegalArgumentException("Give a Markdown, text or CSV file in the workspace.");
        }
        Path root = workspace.getWorkspacePath();
        Path requested;
        try {
            requested = Path.of(input);
        } catch (IllegalArgumentException invalidPath) {
            throw new IllegalArgumentException("Give a valid workspace path.");
        }
        Path file = root.resolve(requested);
        if (!file.startsWith(root) || file.equals(root) || file.getNameCount() > 128
                || !file.equals(file.normalize())) {
            throw new IllegalArgumentException("The artifact must be inside the workspace.");
        }
        for (Path segment : root.relativize(file)) {
            if (segment.toString().startsWith(".")) {
                throw new IllegalArgumentException("Hidden workspace files cannot be registered.");
            }
        }
        if (file.equals(root.resolve(workspaceConfig.envFile()).normalize())) {
            throw new IllegalArgumentException("Workspace environment files cannot be registered.");
        }

        String name = file.getFileName().toString();
        if (!SAFE_NAME.matcher(name).matches() || !name.equals(redactor.redact(name))) {
            throw new IllegalArgumentException("Give the artifact a safe file name.");
        }
        String mediaType = mediaType(name);
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IllegalArgumentException("Give a regular workspace file.");
        }
        if (attributes.size() > MAX_BYTES) {
            throw new IllegalArgumentException("The artifact exceeds 2 MiB.");
        }
        byte[] bytes;
        try (var stream = ConfinedFileRead.open(file, () -> {})) {
            bytes = stream.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("The artifact exceeds 2 MiB.");
        }
        String text = decode(bytes);
        if (text.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t')) {
            throw new IllegalArgumentException("The artifact must contain plain UTF-8 text.");
        }
        if (!text.equals(redactor.redact(text)) || PRIVATE_KEY.matcher(text).find()) {
            throw new IllegalArgumentException("The file contains a credential pattern and was refused.");
        }
        return new File(name, mediaType, bytes);
    }

    private static String mediaType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        if (lower.endsWith(".csv")) {
            return "text/csv";
        }
        throw new IllegalArgumentException("Supported artifact types are .md, .txt and .csv.");
    }

    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("The artifact must contain plain UTF-8 text.");
        }
    }

    record File(String name, String mediaType, byte[] bytes) {
    }
}
