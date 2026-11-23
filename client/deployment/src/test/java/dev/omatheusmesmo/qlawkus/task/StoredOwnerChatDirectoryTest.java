package dev.omatheusmesmo.qlawkus.task;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.store.OwnerChatStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownOwnerChatStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoredOwnerChatDirectoryTest {

    @TempDir
    Path root;

    @Test
    void theLastChatOfAProviderIsTheOneTheOwnerWroteToLast() {
        StoredOwnerChatDirectory directory = directory(new MarkdownOwnerChatStore(root), new StepClock());

        directory.remember("telegram", "42");
        directory.remember("telegram", "43");

        assertEquals("43", directory.lastChat("telegram").orElseThrow().chatId());
        assertTrue(directory.lastChat("discord").isEmpty());
    }

    @Test
    void theMostRecentChatIsTheLastOneWrittenToAcrossProviders() {
        StepClock clock = new StepClock();
        StoredOwnerChatDirectory directory = directory(new MarkdownOwnerChatStore(root), clock);

        directory.remember("telegram", "42");
        clock.advance(Duration.ofMinutes(5));
        directory.remember("discord", "99");
        assertEquals(new OwnerChat("discord", "99", clock.instant()), directory.mostRecent().orElseThrow());

        clock.advance(Duration.ofMinutes(5));
        directory.remember("telegram", "42");
        assertEquals("telegram", directory.mostRecent().orElseThrow().providerId());
    }

    @Test
    void noChatIsKnownBeforeTheOwnerWritesAnywhere() {
        StoredOwnerChatDirectory directory = directory(new MarkdownOwnerChatStore(root), new StepClock());

        assertTrue(directory.mostRecent().isEmpty());
        assertTrue(directory.lastChat("telegram").isEmpty());
    }

    @Test
    void theChatsSurviveARestartOfTheAgent() {
        StepClock clock = new StepClock();
        directory(new MarkdownOwnerChatStore(root), clock).remember("discord", "99");

        StoredOwnerChatDirectory restarted = directory(new MarkdownOwnerChatStore(root), clock);

        assertEquals("99", restarted.lastChat("discord").orElseThrow().chatId());
        assertEquals("discord", restarted.mostRecent().orElseThrow().providerId());
    }

    @Test
    void theProviderNameDoesNotDependOnItsCase() {
        StoredOwnerChatDirectory directory = directory(new MarkdownOwnerChatStore(root), new StepClock());

        directory.remember("Telegram", "42");

        assertEquals("42", directory.lastChat("telegram").orElseThrow().chatId());
    }

    @Test
    void aBlankProviderOrChatIsIgnored() {
        StoredOwnerChatDirectory directory = directory(new MarkdownOwnerChatStore(root), new StepClock());

        directory.remember(" ", "42");
        directory.remember("telegram", "");
        directory.remember("telegram", null);

        assertTrue(directory.mostRecent().isEmpty());
    }

    @Test
    void aChatThatCannotBeSavedIsStillRememberedForThisRun() {
        StoredOwnerChatDirectory directory = directory(new FailingStore(), new StepClock());

        assertDoesNotThrow(() -> directory.remember("telegram", "42"));

        assertEquals("42", directory.lastChat("telegram").orElseThrow().chatId());
    }

    private static StoredOwnerChatDirectory directory(OwnerChatStore store, Clock clock) {
        return new StoredOwnerChatDirectory(store, clock);
    }

    private static final class StepClock extends Clock {

        private Instant now = Instant.parse("2026-11-01T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class FailingStore implements OwnerChatStore {

        @Override
        public void save(OwnerChat chat) {
            throw new IllegalStateException("read-only file system");
        }

        @Override
        public List<OwnerChat> loadAll() {
            return List.of();
        }

        @Override
        public void verifyWritable() {
            throw new IllegalStateException("read-only file system");
        }
    }
}
