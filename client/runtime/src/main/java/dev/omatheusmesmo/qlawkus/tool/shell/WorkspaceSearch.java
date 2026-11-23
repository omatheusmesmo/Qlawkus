package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.config.ShellConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.ignore.IgnoreNode;

/** One bounded, read-only search session shared by glob and grep. It never follows symbolic links. */
final class WorkspaceSearch {
    private static final int MAX_IGNORE_BYTES = 65536;
    private static final int MAX_PATH_CHARS = 4096;
    private static final int MAX_SEGMENT_CHARS = 255;
    final Path workspace;
    final boolean restricted;
    final int maxFileBytes;
    private final int maxVisited;
    private final int maxDepth;
    private final int maxTotalBytes;
    private final long deadline;
    int skipped;
    int longLines;
    private int unsafeIgnoreFiles;
    private int discovered = 1;
    private int bytesRead;
    private String stopReason;

    WorkspaceSearch(WorkspaceConfinement confinement, ShellConfig.Search config) throws IOException {
        workspace = confinement.getWorkspacePath().toRealPath();
        restricted = confinement.isRestrictToWorkspace();
        maxVisited = Math.clamp(config.maxVisitedPaths(), 1, 100000);
        maxDepth = Math.clamp(config.maxDepth(), 1, 128);
        maxFileBytes = Math.clamp(config.maxFileBytes(), 1, 16 * 1024 * 1024);
        maxTotalBytes = Math.clamp(config.maxTotalBytes(), 1, 64 * 1024 * 1024);
        Duration timeout = config.timeout();
        long nanos = timeout.isNegative() || timeout.isZero() ? 1
                : timeout.compareTo(Duration.ofSeconds(30)) >= 0 ? Duration.ofSeconds(30).toNanos() : timeout.toNanos();
        deadline = System.nanoTime() + nanos;
    }

    Scan scan(String input, WorkspaceGlob glob, boolean allowFile) throws IOException {
        if (input != null && input.length() > 4096) {
            throw new IllegalArgumentException("Search path is too long");
        }
        Path requested = input == null || input.isBlank() ? workspace : Path.of(input);
        if (!requested.isAbsolute()) {
            requested = workspace.resolve(requested);
        }
        requested = requested.normalize();
        if (restricted && !requested.startsWith(workspace)) {
            throw new IllegalArgumentException("Search path escapes the workspace");
        }
        rejectSymlinkComponents(requested);
        Path root = requested.toRealPath();
        if (!allowed(root)) {
            throw new IllegalArgumentException("Search path escapes the workspace");
        }
        boolean directory = Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS);
        if (!directory && (!allowFile || !Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalArgumentException(allowFile ? "Search path must be a regular file or directory" : "Search path must be a directory");
        }
        Path boundary = root.startsWith(workspace) ? workspace : directory ? root : root.getParent();
        List<IgnoreFile> inherited = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try {
            Path parent = root.getParent();
            if (!root.equals(boundary) && parent != null && parent.startsWith(boundary)) {
                Path current = boundary;
                while (true) {
                    if (ignored(current, true, inherited)) {
                        return new Scan(root, files);
                    }
                    inherited = withIgnore(current, inherited);
                    if (current.equals(parent)) {
                        break;
                    }
                    current = current.resolve(current.relativize(parent).getName(0));
                }
            }
            visit(root, root, 0, inherited, glob, files);
        } catch (Stopped ignored) {
            // The result contains only files discovered before the explicit limit.
        } catch (UnsafeIgnoreRules ignored) {
            skipped++;
        }
        files.sort(Comparator.comparing(this::display));
        return new Scan(root, files);
    }

    private void visit(Path path, Path root, int depth, List<IgnoreFile> inherited, WorkspaceGlob glob, List<Path> files) {
        checkTime();
        if (!matcherPathAllowed(path)) {
            skipped++;
            mark("path length limit reached");
            return;
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attrs.isSymbolicLink() || path.getFileName() != null && path.getFileName().toString().equals(".git") || !allowed(path.toRealPath())) {
                return;
            }
            if (ignored(path, attrs.isDirectory(), inherited)) {
                return;
            }
            if (attrs.isRegularFile()) {
                String relative = root.equals(path) ? path.getFileName().toString() : portable(root.relativize(path));
                if (glob == null || glob.matches(relative)) {
                    files.add(path);
                }
                return;
            }
            if (!attrs.isDirectory()) {
                return;
            }
            if (depth >= maxDepth) {
                mark("depth limit reached");
                return;
            }
            List<IgnoreFile> rules = withIgnore(path, inherited);
            List<Path> children = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    checkTime();
                    if (discovered >= maxVisited) {
                        mark("visited-path limit reached");
                        break;
                    }
                    discovered++;
                    children.add(child);
                }
            }
            children.sort(Comparator.comparing(Path::toString));
            for (Path child : children) {
                visit(child, root, depth + 1, rules, glob, files);
            }
        } catch (IOException e) {
            skipped++;
        }
    }

    private List<IgnoreFile> withIgnore(Path directory, List<IgnoreFile> inherited) throws IOException {
        Path ignoreFile = directory.resolve(".gitignore");
        if (!Files.exists(ignoreFile, LinkOption.NOFOLLOW_LINKS)) {
            return inherited;
        }
        try {
            byte[] bytes = read(ignoreFile, MAX_IGNORE_BYTES);
            if (bytes == null) {
                throw new IOException("Cannot safely read ignore rules");
            }
            if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
                bytes = Arrays.copyOfRange(bytes, 3, bytes.length);
            }
            checkIgnoreComplexity(bytes);
            IgnoreNode node = new IgnoreNode();
            node.parse(new ByteArrayInputStream(bytes));
            List<IgnoreFile> result = new ArrayList<>(inherited);
            result.add(new IgnoreFile(directory, node));
            return result;
        } catch (IOException e) {
            unsafeIgnoreFiles++;
            throw new UnsafeIgnoreRules();
        }
    }

    private boolean ignored(Path path, boolean directory, List<IgnoreFile> inherited) {
        if (!matcherPathAllowed(path)) {
            mark("path length limit reached");
            throw new Stopped();
        }
        for (int i = inherited.size() - 1; i >= 0; i--) {
            checkTime();
            IgnoreFile file = inherited.get(i);
            Boolean result = file.node.checkIgnored(portable(file.directory.relativize(path)), directory);
            if (result != null) {
                return result;
            }
        }
        return false;
    }

    /** JGit's wildcard matcher backtracks: reject expensive input before handing it to the upstream parser. */
    private static void checkIgnoreComplexity(byte[] bytes) throws IOException {
        String content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        if (content.indexOf('\0') >= 0) {
            throw new IOException("Ignore rules must be UTF-8 text");
        }
        int rules = 0;
        for (String line : content.lines().toList()) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (++rules > 512 || line.length() > 1024) {
                throw new IOException("Ignore rules exceed the complexity budget");
            }
            String pattern = line.startsWith("!") ? line.substring(1) : line;
            int globstars = 0;
            for (String segment : pattern.stripTrailing().split("/")) {
                if (segment.equals("**")) {
                    if (++globstars > 2) {
                        throw new IOException("Ignore rules exceed the complexity budget");
                    }
                    continue;
                }
                int stars = 0;
                for (int i = 0; i < segment.length(); i++) {
                    if (segment.charAt(i) == '*' && ++stars > 2) {
                        throw new IOException("Ignore rules exceed the complexity budget");
                    }
                }
            }
        }
    }

    byte[] read(Path path, int maxBytes) throws IOException {
        checkTime();
        rejectSymlinkComponents(path);
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile() || !allowed(path.toRealPath()) || attrs.size() > maxBytes) {
            skipped++;
            return null;
        }
        int available = Math.min(maxBytes, maxTotalBytes - bytesRead);
        if (attrs.size() > available) {
            mark("total byte budget reached");
            throw new Stopped();
        }
        byte[] bytes;
        try (InputStream stream = ConfinedFileRead.open(path, this::checkTime)) {
            bytes = stream.readNBytes(available + 1);
        }
        bytesRead += bytes.length;
        checkTime();
        if (bytes.length > available || !allowed(path.toRealPath())) {
            mark("file changed or byte budget reached");
            throw new Stopped();
        }
        return bytes;
    }

    private void rejectSymlinkComponents(Path path) throws IOException {
        Path current = path;
        while (current != null && !current.equals(workspace)) {
            checkTime();
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not searched");
            }
            current = current.getParent();
        }
    }

    private boolean allowed(Path path) {
        return !restricted || path.startsWith(workspace);
    }

    private static boolean matcherPathAllowed(Path path) {
        if (path.toString().length() > MAX_PATH_CHARS || path.getNameCount() > 128) {
            return false;
        }
        for (Path segment : path) {
            if (segment.toString().length() > MAX_SEGMENT_CHARS) {
                return false;
            }
        }
        return true;
    }

    void checkTime() {
        if (System.nanoTime() - deadline >= 0) {
            mark("search deadline reached");
            throw new Stopped();
        }
    }

    void mark(String reason) {
        if (stopReason == null) {
            stopReason = reason;
        }
    }

    String notice() {
        StringBuilder text = new StringBuilder();
        if (stopReason != null) {
            text.append("\n[TRUNCATED: ").append(stopReason).append(']');
        }
        if (unsafeIgnoreFiles > 0) {
            text.append("\n[Skipped ").append(unsafeIgnoreFiles)
                    .append(" directories: .gitignore rules were unreadable, oversized or exceeded safety limits]");
        }
        if (skipped > 0 || longLines > 0) {
            text.append("\n[Skipped ").append(skipped).append(" unreadable, binary, oversized or unsafe entries; ")
                    .append(longLines).append(" lines longer than 65536 characters]");
        }
        return text.toString();
    }

    String display(Path path) {
        return portable(path.startsWith(workspace) ? workspace.relativize(path) : path)
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }

    private static String portable(Path path) {
        return path.toString().replace(java.io.File.separatorChar, '/');
    }

    record Scan(Path root, List<Path> files) {}
    private record IgnoreFile(Path directory, IgnoreNode node) {}
    private static final class UnsafeIgnoreRules extends IOException {}
    static final class Stopped extends RuntimeException {
        private Stopped() {
            super(null, null, false, false);
        }
    }
}
