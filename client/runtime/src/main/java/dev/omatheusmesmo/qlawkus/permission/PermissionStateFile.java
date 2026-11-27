package dev.omatheusmesmo.qlawkus.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.omatheusmesmo.qlawkus.store.markdown.WriteProbe;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The runtime layer of the policy as one JSON file, {@code state.json} under the permissions directory
 * of the agent state root. It is read and written through the Jackson tree model, so it needs no
 * reflection in a native image, and a write goes to a sibling file that is moved into place so a crash
 * leaves the previous version intact.
 *
 * <p>An unreadable file stops the start rather than being skipped: starting without it would quietly
 * drop every restriction the owner had set.
 */
public final class PermissionStateFile {

    public static final String DIRECTORY = "permissions";
    static final String FILE = "state.json";
    private static final int VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path directory;

    public PermissionStateFile(Path directory) {
        this.directory = directory;
    }

    PermissionState load() {
        Path file = directory.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return PermissionState.empty();
        }
        try {
            JsonNode root = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            Map<AccessClass, PermissionLevel> defaults = cells(root.path("defaults"));
            Map<String, Map<AccessClass, PermissionLevel>> groups = new LinkedHashMap<>();
            root.path("groups").properties().forEach(group -> groups.put(group.getKey(), cells(group.getValue())));
            Map<String, PermissionLevel> tools = new LinkedHashMap<>();
            root.path("tools").properties().forEach(tool -> tools.put(tool.getKey(), level(tool.getValue())));
            String preset = root.path("preset").isTextual() ? root.path("preset").asText() : null;
            return new PermissionState(preset, defaults, groups, tools, root.path("revision").asLong(0));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("The permission state at " + file
                    + " cannot be read; fix or remove it to start (" + e.getMessage() + ")", e);
        }
    }

    void save(PermissionState state) {
        ObjectNode root = JSON.createObjectNode();
        root.put("version", VERSION);
        root.put("revision", state.revision());
        if (state.preset() != null) {
            root.put("preset", state.preset());
        }
        write(root.putObject("defaults"), state.defaults());
        ObjectNode groups = root.putObject("groups");
        state.groups().forEach((group, cells) -> write(groups.putObject(group), cells));
        ObjectNode tools = root.putObject("tools");
        state.tools().forEach((tool, level) -> tools.put(tool, level.label()));
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
            throw new UncheckedIOException("Cannot write the permission state at " + file, e);
        }
    }

    /** Proves a write can be made without leaving anything behind. Throws when it cannot. */
    public void verifyWritable() {
        WriteProbe.verify(directory);
    }

    private static void write(ObjectNode target, Map<AccessClass, PermissionLevel> cells) {
        for (AccessClass operation : AccessClass.values()) {
            PermissionLevel level = cells.get(operation);
            if (level != null) {
                target.put(operation.name().toLowerCase(Locale.ROOT), level.label());
            }
        }
    }

    private static Map<AccessClass, PermissionLevel> cells(JsonNode node) {
        Map<AccessClass, PermissionLevel> cells = new EnumMap<>(AccessClass.class);
        node.properties().forEach(cell -> cells.put(
                PermissionConfigReader.parseOperation(cell.getKey()).orElseThrow(() ->
                        new IllegalArgumentException("unknown operation " + cell.getKey())),
                level(cell.getValue())));
        return cells;
    }

    private static PermissionLevel level(JsonNode node) {
        return PermissionLevel.parse(node.asText()).orElseThrow(() ->
                new IllegalArgumentException("unknown level " + node.asText()));
    }
}
