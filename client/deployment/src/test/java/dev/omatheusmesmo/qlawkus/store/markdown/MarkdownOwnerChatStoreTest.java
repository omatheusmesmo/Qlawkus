package dev.omatheusmesmo.qlawkus.store.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownOwnerChatStoreTest {

    private static final Instant SEEN = Instant.parse("2026-11-01T10:00:00Z");

    @TempDir
    Path root;

    @Test
    void aSavedChatIsLoadedAgainByANewStoreOverTheSameDirectory() {
        Path dir = root.resolve("owner-chats");
        new MarkdownOwnerChatStore(dir).save(new OwnerChat("telegram", "42", SEEN));

        assertEquals(List.of(new OwnerChat("telegram", "42", SEEN)), new MarkdownOwnerChatStore(dir).loadAll());
    }

    @Test
    void aLaterChatForTheSameProviderReplacesTheEarlierOne() {
        Path dir = root.resolve("owner-chats");
        MarkdownOwnerChatStore store = new MarkdownOwnerChatStore(dir);
        store.save(new OwnerChat("telegram", "42", SEEN));
        store.save(new OwnerChat("telegram", "43", SEEN.plusSeconds(60)));

        List<OwnerChat> loaded = new MarkdownOwnerChatStore(dir).loadAll();

        assertEquals(List.of(new OwnerChat("telegram", "43", SEEN.plusSeconds(60))), loaded);
    }

    @Test
    void anOlderObservationDoesNotOverwriteTheFileAndAProbeLeavesNoChat() {
        var store = new MarkdownOwnerChatStore(root.resolve("owner-chats"));
        store.verifyWritable();
        assertTrue(store.loadAll().isEmpty());

        var newer = new OwnerChat("telegram", "43", SEEN.plusSeconds(60));
        store.save(newer);
        store.save(new OwnerChat("telegram", "42", SEEN));

        assertEquals(List.of(newer), store.loadAll());
    }

    @Test
    void anUnreadableFileIsSkippedAndTheOtherProvidersStillLoad() throws Exception {
        Path dir = root.resolve("owner-chats");
        MarkdownOwnerChatStore store = new MarkdownOwnerChatStore(dir);
        store.save(new OwnerChat("telegram", "42", SEEN));
        Files.writeString(dir.resolve("discord.json"), "{not json", StandardCharsets.UTF_8);

        assertEquals(List.of(new OwnerChat("telegram", "42", SEEN)), store.loadAll());
    }

    @Test
    void aProviderNameThatCouldLeaveTheDirectoryIsRefusedAndWritesNothing() throws Exception {
        Path dir = root.resolve("owner-chats");
        MarkdownOwnerChatStore store = new MarkdownOwnerChatStore(dir);

        assertThrows(IllegalArgumentException.class,
                () -> store.save(new OwnerChat("../escape", "42", SEEN)));

        try (Stream<Path> files = Files.walk(root)) {
            List<Path> written = files.filter(Files::isRegularFile).toList();
            assertTrue(written.isEmpty(), written.toString());
        }
    }
}
