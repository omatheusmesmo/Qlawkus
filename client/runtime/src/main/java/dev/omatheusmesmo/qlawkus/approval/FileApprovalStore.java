package dev.omatheusmesmo.qlawkus.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.markdown.WriteProbe;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * The default approval store: one JSON file per request under {@code state.root}. It is the store a
 * database-free distribution gets, and it survives restarts because every change is written to disk
 * before the call returns.
 *
 * <p>A write goes to a sibling file and is moved into place, so a crash mid-write leaves the previous
 * version intact. A file that cannot be read is skipped with a warning rather than stopping boot: the
 * other requests stay decidable, and the damaged one is simply not visible.
 *
 * <p>Retention is bounded. Only settled requests are dropped, oldest first, so a pending or
 * needs-review request is never lost to pruning.
 */
@DefaultBean
@ApplicationScoped
public class FileApprovalStore implements ApprovalStore {

    public static final String DIRECTORY = "approvals";
    public static final int DEFAULT_MAX_TERMINAL = 500;

    private static final String SUFFIX = ".json";
    private static final ObjectMapper JSON = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final Comparator<ApprovalRequest> OLDEST_FIRST =
            Comparator.comparing(ApprovalRequest::createdAt).thenComparing(ApprovalRequest::id);

    private final Path directory;
    private final int maxTerminal;
    private final Map<String, ApprovalRequest> requests = new HashMap<>();

    @Inject
    public FileApprovalStore(AgentConfig config) {
        this(Path.of(config.state().root(), DIRECTORY), DEFAULT_MAX_TERMINAL);
    }

    public FileApprovalStore(Path directory, int maxTerminal) {
        this.directory = directory;
        this.maxTerminal = maxTerminal;
        load();
    }

    @Override
    public synchronized void create(ApprovalRequest request) {
        if (requests.containsKey(request.id())) {
            throw new IllegalStateException("Approval request " + request.id() + " already exists");
        }
        write(request);
        requests.put(request.id(), request);
        prune();
    }

    @Override
    public synchronized Optional<ApprovalRequest> find(String id) {
        if (!ApprovalRequest.isValidId(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(requests.get(id));
    }

    @Override
    public synchronized List<ApprovalRequest> list() {
        return requests.values().stream()
                .sorted(OLDEST_FIRST.reversed())
                .toList();
    }

    @Override
    public synchronized Optional<ApprovalRequest> transition(String id, ApprovalStatus from,
            UnaryOperator<ApprovalRequest> change) {
        Optional<ApprovalRequest> current = find(id);
        if (current.isEmpty() || current.get().status() != from) {
            return Optional.empty();
        }
        ApprovalRequest next = change.apply(current.get());
        if (!id.equals(next.id())) {
            throw new IllegalArgumentException("A transition must keep the request id");
        }
        write(next);
        requests.put(id, next);
        prune();
        return Optional.of(next);
    }

    @Override
    public void verifyWritable() {
        WriteProbe.verify(directory);
    }

    private void write(ApprovalRequest request) {
        if (!ApprovalRequest.isValidId(request.id())) {
            throw new IllegalArgumentException("Approval request ids are 32 lower-case hex characters");
        }
        Path file = fileFor(request.id());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            Files.writeString(temp, JSON.writeValueAsString(request), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write approval request " + request.id(), e);
        }
    }

    private void prune() {
        List<ApprovalRequest> settled = requests.values().stream()
                .filter(request -> request.status().isTerminal())
                .sorted(OLDEST_FIRST)
                .toList();
        int excess = settled.size() - maxTerminal;
        settled.stream().limit(Math.max(0, excess)).forEach(request -> {
            requests.remove(request.id());
            try {
                Files.deleteIfExists(fileFor(request.id()));
            } catch (IOException e) {
                Log.warnf("Could not delete pruned approval request %s: %s", request.id(), e.getMessage());
            }
        });
    }

    private void load() {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(file -> file.getFileName().toString().endsWith(SUFFIX))
                    .forEach(this::loadOne);
        } catch (IOException e) {
            Log.warnf("Approval requests at %s cannot be listed; starting empty: %s", directory, e.getMessage());
        }
    }

    private void loadOne(Path file) {
        try {
            ApprovalRequest request = JSON.readValue(file.toFile(), ApprovalRequest.class);
            if (!ApprovalRequest.isValidId(request.id())
                    || !file.getFileName().toString().equals(request.id() + SUFFIX)) {
                Log.warnf("Approval request file %s does not match its id; skipped", file);
                return;
            }
            requests.put(request.id(), request);
        } catch (IOException | RuntimeException e) {
            Log.warnf("Approval request file %s is unreadable; skipped: %s", file, e.getMessage());
        }
    }

    private Path fileFor(String id) {
        return directory.resolve(id + SUFFIX);
    }
}
