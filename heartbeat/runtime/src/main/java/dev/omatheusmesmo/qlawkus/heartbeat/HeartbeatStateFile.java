package dev.omatheusmesmo.qlawkus.heartbeat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@ApplicationScoped
class HeartbeatStateFile {

    private static final int MAX_BYTES = 16 * 1024;

    private final Path path;
    private final ObjectMapper json;

    @Inject
    HeartbeatStateFile(AgentConfig config, ObjectMapper json) {
        this(Path.of(config.state().root()).resolve("heartbeat.json"), json);
    }

    HeartbeatStateFile(Path path, ObjectMapper json) {
        this.path = path;
        this.json = json;
    }

    HeartbeatState load() throws IOException {
        if (Files.notExists(path)) {
            return null;
        }
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                throw new IOException("Heartbeat state exceeds its size limit.");
            }
            return json.readValue(bytes, HeartbeatState.class);
        }
    }

    void save(HeartbeatState state) {
        Path temp = null;
        try {
            Files.createDirectories(path.getParent());
            temp = Files.createTempFile(path.getParent(), ".heartbeat-", ".json");
            json.writeValue(temp.toFile(), state);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Heartbeat settings could not be saved.");
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Stale temporary files are ignored when loading the state.
                }
            }
        }
    }
}
