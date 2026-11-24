package dev.omatheusmesmo.qlawkus.store.markdown;

import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * File-backed {@link ScheduledTaskStore}, the default when no database backend is present. Each task is
 * one {@code <id>.json} file in {@code scheduled-tasks} under {@code qlawkus.agent.state.root}, so the
 * tasks outlive the process without a database.
 *
 * <p>A save writes a sibling temporary file and moves it into place, so a crash mid-write leaves the
 * previous record intact. A file that cannot be read, or whose id does not match its name, is skipped
 * with a warning that names only the file: the record is owner data, and a damaged one must cost only
 * itself. Loading is bounded so a directory stuffed with files cannot stall boot.
 */
@ApplicationScoped
@DefaultBean
public class MarkdownScheduledTaskStore implements ScheduledTaskStore {

    public static final String DIRECTORY = "scheduled-tasks";
    public static final int MAX_RECORDS = 100;

    private static final String SUFFIX = ".json";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final Path directory;

    @Inject
    public MarkdownScheduledTaskStore(AgentConfig config) {
        this(directoryUnder(config.state().root()));
    }

    public MarkdownScheduledTaskStore(Path directory) {
        this.directory = directory;
    }

    public static Path directoryUnder(String stateRoot) {
        return Path.of(stateRoot, DIRECTORY);
    }

    @Override
    public void save(ScheduledTask task) {
        Path file = fileFor(task.id());
        try {
            Files.createDirectories(directory);
            Path temp = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
            Files.writeString(temp, ScheduledTaskJson.write(task), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save scheduled task " + task.id(), e);
        }
    }

    @Override
    public void delete(String id) {
        try {
            Files.deleteIfExists(fileFor(id));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete scheduled task " + id, e);
        }
    }

    @Override
    public List<ScheduledTask> loadAll() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> listing = Files.list(directory)) {
            files = listing
                    .filter(file -> file.getFileName().toString().endsWith(SUFFIX))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list scheduled tasks", e);
        }
        if (files.size() > MAX_RECORDS) {
            Log.warnf("Found %d scheduled task files, reading only the first %d", files.size(), MAX_RECORDS);
        }
        return files.stream()
                .limit(MAX_RECORDS)
                .map(MarkdownScheduledTaskStore::read)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public void verifyWritable() {
        WriteProbe.verify(directory);
    }

    private Path fileFor(String id) {
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("not a scheduled task id");
        }
        return directory.resolve(id + SUFFIX);
    }

    private static Optional<ScheduledTask> read(Path file) {
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - SUFFIX.length());
        try {
            ScheduledTask task = ScheduledTaskJson.read(Files.readString(file, StandardCharsets.UTF_8));
            if (!id.equals(task.id()) || task.status() == null) {
                throw new IOException("the record does not belong to its file name");
            }
            return Optional.of(task);
        } catch (IOException | RuntimeException e) {
            Log.warnf("Scheduled task file %s is unreadable, skipping it", name);
            return Optional.empty();
        }
    }
}
