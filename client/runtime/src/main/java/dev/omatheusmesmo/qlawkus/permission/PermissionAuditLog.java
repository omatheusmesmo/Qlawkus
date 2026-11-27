package dev.omatheusmesmo.qlawkus.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The persisted record of every policy change, {@code audit.json} next to the state file. It is
 * bounded: past {@code retention} entries the oldest are dropped. Entries are read and written through
 * the Jackson tree model, so nothing needs reflection in a native image.
 */
public final class PermissionAuditLog {

    static final String FILE = "audit.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path directory;
    private final int retention;
    private final List<PermissionAuditEntry> entries = new ArrayList<>();

    public PermissionAuditLog(Path directory, int retention) {
        this.directory = directory;
        this.retention = Math.max(1, retention);
        load();
    }

    /** Appends one entry and persists the log. */
    public synchronized void append(PermissionAuditEntry entry) {
        entries.add(entry);
        while (entries.size() > retention) {
            entries.remove(0);
        }
        save();
    }

    /** Every kept entry, newest first. */
    public synchronized List<PermissionAuditEntry> list() {
        List<PermissionAuditEntry> newestFirst = new ArrayList<>(entries);
        Collections.reverse(newestFirst);
        return List.copyOf(newestFirst);
    }

    private void load() {
        Path file = directory.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonNode root = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            for (JsonNode node : root.path("entries")) {
                entries.add(new PermissionAuditEntry(Instant.parse(node.path("at").asText()),
                        text(node, "actor"), text(node, "channel"), text(node, "target"), text(node, "before"),
                        text(node, "after"), text(node, "approvedBy"), text(node, "requestId")));
            }
        } catch (IOException | RuntimeException e) {
            Log.warnf("The permission audit log at %s cannot be read; starting it empty: %s", file, e.getMessage());
            entries.clear();
        }
    }

    private void save() {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode array = root.putArray("entries");
        for (PermissionAuditEntry entry : entries) {
            ObjectNode node = array.addObject();
            node.put("at", entry.at().toString());
            put(node, "actor", entry.actor());
            put(node, "channel", entry.channel());
            put(node, "target", entry.target());
            put(node, "before", entry.before());
            put(node, "after", entry.after());
            put(node, "approvedBy", entry.approvedBy());
            put(node, "requestId", entry.requestId());
        }
        Path file = directory.resolve(FILE);
        Path temp = file.resolveSibling(FILE + ".tmp");
        try {
            Files.createDirectories(directory);
            Files.writeString(temp, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the permission audit log at " + file, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static void put(ObjectNode node, String field, String value) {
        if (value != null) {
            node.put(field, value);
        }
    }
}
