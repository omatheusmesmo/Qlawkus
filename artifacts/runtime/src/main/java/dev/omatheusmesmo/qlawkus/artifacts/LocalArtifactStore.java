package dev.omatheusmesmo.qlawkus.artifacts;

import dev.omatheusmesmo.qlawkus.artifact.ArtifactHandle;
import dev.omatheusmesmo.qlawkus.artifact.ArtifactLibrary;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Each artifact is one atomic file. Listing reads only the metadata header. */
@ApplicationScoped
class LocalArtifactStore implements ArtifactLibrary {

    static final Duration TTL = Duration.ofHours(24);
    static final int MAX_COUNT = 25;
    static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;
    private static final String SUFFIX = ".artifact";
    private static final int FORMAT_VERSION = 1;

    private final Path directory;
    private final Clock clock;

    @Inject
    LocalArtifactStore(AgentConfig config) {
        this(Path.of(config.state().root(), "artifacts"), Clock.systemUTC());
    }

    LocalArtifactStore(Path directory, Clock clock) {
        this.directory = directory;
        this.clock = clock;
    }

    synchronized ArtifactHandle save(String owner, Destination destination, WorkspaceArtifact.File file) throws IOException {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        List<ArtifactHandle> live = scan(now, null);
        if (live.size() >= MAX_COUNT || live.stream().mapToLong(ArtifactHandle::size).sum() + file.bytes().length > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("Artifact storage is full. Try after older files expire.");
        }
        var handle = new ArtifactHandle(UUID.randomUUID(), file.name(), file.mediaType(), file.bytes().length, now, now.plus(TTL));
        ensureDirectory();
        Path staging = Files.createTempFile(directory, "artifact-", ".part");
        try {
            try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(staging)))) {
                writeHeader(out, new Header(handle, owner, destination));
                out.write(file.bytes());
            }
            Files.move(staging, path(handle.id()), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(staging);
        }
        return handle;
    }

    @Override
    public synchronized List<ArtifactHandle> list(String owner) throws IOException {
        Objects.requireNonNull(owner);
        List<ArtifactHandle> artifacts = scan(clock.instant(), owner);
        artifacts.sort(Comparator.comparing(ArtifactHandle::createdAt).reversed().thenComparing(ArtifactHandle::id));
        return List.copyOf(artifacts);
    }

    @Override
    public Optional<Content> read(UUID id, String owner) throws IOException {
        return read(id, owner, null);
    }

    synchronized Optional<Content> read(UUID id, String owner, Destination destination) throws IOException {
        if (!directoryExists() || !Files.isRegularFile(path(id), LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        Path path = path(id);
        boolean expired;
        try (var in = open(path)) {
            Header header = readHeader(in, id);
            expired = !header.handle().expiresAt().isAfter(clock.instant());
            if (!expired && owner.equals(header.owner())
                    && (destination == null || destination.equals(header.destination()))) {
                byte[] bytes = in.readNBytes(header.handle().size());
                if (bytes.length != header.handle().size() || in.read() != -1) {
                    throw new IOException("Invalid artifact size");
                }
                return Optional.of(new Content(header.handle(), bytes));
            }
        }
        if (expired) {
            Files.deleteIfExists(path);
        }
        return Optional.empty();
    }

    @Scheduled(identity = "artifact-cleanup", every = "15m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    synchronized void expire() throws IOException {
        scan(clock.instant(), null);
    }

    private List<ArtifactHandle> scan(Instant now, String owner) throws IOException {
        List<ArtifactHandle> live = new ArrayList<>();
        if (!directoryExists()) {
            return live;
        }
        try (var paths = Files.newDirectoryStream(directory, "{*.artifact,artifact-*.part}")) {
            for (Path entry : paths) {
                String name = entry.getFileName().toString();
                if (name.endsWith(".part")) {
                    Files.deleteIfExists(entry);
                    continue;
                }
                UUID id = parseId(name.substring(0, name.length() - SUFFIX.length()));
                if (id == null || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                Header header;
                try (var in = open(entry)) {
                    header = readHeader(in, id);
                }
                if (!header.handle().expiresAt().isAfter(now)) {
                    Files.deleteIfExists(entry);
                } else if (owner == null || owner.equals(header.owner())) {
                    live.add(header.handle());
                }
            }
        }
        return live;
    }

    private boolean directoryExists() throws IOException {
        if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Artifact directory is unavailable");
        }
        return true;
    }

    private void ensureDirectory() throws IOException {
        if (!directoryExists()) {
            Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        }
    }

    private Path path(UUID id) {
        return directory.resolve(id + SUFFIX);
    }

    private static DataInputStream open(Path path) throws IOException {
        return new DataInputStream(new BufferedInputStream(Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)));
    }

    private static void writeHeader(DataOutputStream out, Header header) throws IOException {
        ArtifactHandle handle = header.handle();
        out.writeInt(FORMAT_VERSION);
        out.writeUTF(header.owner());
        out.writeBoolean(header.destination() != null);
        if (header.destination() != null) {
            out.writeUTF(header.destination().providerId());
            out.writeUTF(header.destination().chatId());
        }
        out.writeUTF(handle.name());
        out.writeUTF(handle.mediaType());
        out.writeInt(handle.size());
        out.writeLong(handle.createdAt().toEpochMilli());
    }

    private static Header readHeader(DataInputStream in, UUID id) throws IOException {
        if (in.readInt() != FORMAT_VERSION) {
            throw new IOException("Unknown artifact format");
        }
        String owner = in.readUTF();
        Destination destination = in.readBoolean() ? new Destination(in.readUTF(), in.readUTF()) : null;
        String name = in.readUTF();
        String mediaType = in.readUTF();
        int size = in.readInt();
        if (size < 0 || size > WorkspaceArtifact.MAX_BYTES) {
            throw new IOException("Invalid artifact size");
        }
        Instant createdAt = Instant.ofEpochMilli(in.readLong());
        return new Header(new ArtifactHandle(id, name, mediaType, size, createdAt, createdAt.plus(TTL)), owner, destination);
    }

    static UUID parseId(String value) {
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalidId) {
            return null;
        }
    }

    record Destination(String providerId, String chatId) {
    }

    private record Header(ArtifactHandle handle, String owner, Destination destination) {
    }
}
