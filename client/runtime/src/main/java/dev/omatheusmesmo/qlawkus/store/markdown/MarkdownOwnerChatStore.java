package dev.omatheusmesmo.qlawkus.store.markdown;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.OwnerChatStore;
import dev.omatheusmesmo.qlawkus.task.DynamicTaskScheduler;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
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
 * File-backed {@link OwnerChatStore}, the default backend. The owner's last chat on each provider is one
 * {@code <provider>.json} file in {@code owner-chats} under {@code qlawkus.agent.state.root}, written through
 * a temporary sibling and moved into place, so a crash mid-write keeps the previous chat.
 *
 * <p>A file that cannot be read, or whose provider does not match its name, is skipped with a warning that
 * names only the file. The provider name is the file name, so it is checked before any path is built.
 */
@ApplicationScoped
@DefaultBean
public class MarkdownOwnerChatStore implements OwnerChatStore {

    public static final String DIRECTORY = "owner-chats";
    public static final int MAX_PROVIDERS = 32;

    private static final String SUFFIX = ".json";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final Pattern SAFE_PROVIDER = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Path directory;

    @Inject
    public MarkdownOwnerChatStore(AgentConfig config) {
        this(Path.of(config.state().root(), DIRECTORY));
    }

    public MarkdownOwnerChatStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public synchronized void save(OwnerChat chat) {
        Path file = fileFor(chat.providerId());
        if (Files.isRegularFile(file) && read(file).filter(saved -> saved.lastSeen().isAfter(chat.lastSeen())).isPresent()) {
            return;
        }
        try {
            Files.createDirectories(directory);
            Path temp = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
            Files.writeString(temp, JSON.writeValueAsString(chat), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save the owner's last chat on " + chat.providerId(), e);
        }
    }

    @Override
    public List<OwnerChat> loadAll() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> listing = Files.list(directory)) {
            files = listing
                    .filter(file -> file.getFileName().toString().endsWith(SUFFIX))
                    .sorted()
                    .limit(MAX_PROVIDERS)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the owner's chats", e);
        }
        return files.stream()
                .map(MarkdownOwnerChatStore::read)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public void verifyWritable() {
        WriteProbe.verify(directory);
    }

    private Path fileFor(String providerId) {
        if (providerId == null || !SAFE_PROVIDER.matcher(providerId).matches()) {
            throw new IllegalArgumentException("not a messaging provider name");
        }
        return directory.resolve(providerId + SUFFIX);
    }

    private static Optional<OwnerChat> read(Path file) {
        String name = file.getFileName().toString();
        String provider = name.substring(0, name.length() - SUFFIX.length());
        try {
            OwnerChat chat = JSON.readValue(file.toFile(), OwnerChat.class);
            if (!SAFE_PROVIDER.matcher(provider).matches() || !provider.equals(chat.providerId())
                    || chat.chatId() == null || chat.chatId().isBlank()
                    || chat.chatId().length() > DynamicTaskScheduler.MAX_DESTINATION_LENGTH
                    || chat.lastSeen() == null) {
                throw new IOException("the record does not belong to its file name");
            }
            return Optional.of(chat);
        } catch (IOException | RuntimeException e) {
            Log.warnf("Owner chat file %s is unreadable, skipping it", name);
            return Optional.empty();
        }
    }
}
