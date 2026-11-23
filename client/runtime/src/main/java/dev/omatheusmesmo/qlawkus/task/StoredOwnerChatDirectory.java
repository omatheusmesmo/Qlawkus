package dev.omatheusmesmo.qlawkus.task;

import dev.omatheusmesmo.qlawkus.store.OwnerChatStore;
import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The owner's last chat on each messaging provider, kept in memory and written through an
 * {@link OwnerChatStore} so it survives a restart. Messaging records every accepted inbound message here;
 * the scheduling tool reads it to pick a delivery chat when the owner did not name one.
 *
 * <p>A chat that cannot be saved stays in memory for this run, and the failure is logged by exception class
 * only: losing the file must never stop an inbound message.
 */
@ApplicationScoped
@DefaultBean
@Startup
public class StoredOwnerChatDirectory implements OwnerChatDirectory {

    private final OwnerChatStore store;
    private final Clock clock;
    private final Map<String, OwnerChat> chats = new ConcurrentHashMap<>();

    @Inject
    public StoredOwnerChatDirectory(OwnerChatStore store) {
        this(store, Clock.systemUTC());
    }

    public StoredOwnerChatDirectory(OwnerChatStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
        loadStored();
    }

    @Override
    public synchronized void remember(String providerId, String chatId) {
        if (isBlank(providerId) || isBlank(chatId) || chatId.strip().length() > DynamicTaskScheduler.MAX_DESTINATION_LENGTH) {
            return;
        }
        OwnerChat chat = new OwnerChat(normalize(providerId), chatId.strip(), clock.instant());
        OwnerChat previous = chats.get(chat.providerId());
        if (previous != null && previous.lastSeen().isAfter(chat.lastSeen())) {
            return;
        }
        chats.put(chat.providerId(), chat);
        try {
            store.save(chat);
        } catch (RuntimeException e) {
            Log.warnf("Could not fully persist the owner's last chat on %s: error=%s",
                    chat.providerId(), e.getClass().getSimpleName());
        }
    }

    @Override
    public Optional<OwnerChat> lastChat(String providerId) {
        if (isBlank(providerId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(chats.get(normalize(providerId)));
    }

    @Override
    public Optional<OwnerChat> mostRecent() {
        return chats.values().stream().max(Comparator.comparing(OwnerChat::lastSeen));
    }

    @Override
    public List<OwnerChat> knownChats() {
        return List.copyOf(chats.values());
    }

    private void loadStored() {
        try {
            for (OwnerChat stored : store.loadAll()) {
                chats.put(stored.providerId(), stored);
            }
        } catch (RuntimeException e) {
            Log.warnf("Could not load the owner's last chats, starting without them: error=%s",
                    e.getClass().getSimpleName());
        }
    }

    private static String normalize(String providerId) {
        return providerId.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
