package dev.omatheusmesmo.qlawkus.cognition;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@ApplicationScoped
public class PendingActionStore {

    private final ConcurrentHashMap<String, Entry> pending = new ConcurrentHashMap<>();

    public void register(String key, Supplier<String> retryAction, String providerId, String chatId) {
        pending.put(key, new Entry(retryAction, providerId, chatId, System.currentTimeMillis()));
        Log.infof("PendingActionStore: registered key=%s provider=%s chatId=%s", key, providerId, chatId);
    }

    public List<Entry> drainAll() {
        List<Entry> entries = new ArrayList<>(pending.values());
        pending.clear();
        return entries;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public record Entry(Supplier<String> retryAction, String providerId, String chatId, long registeredAt) {}
}
