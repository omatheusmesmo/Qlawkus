package dev.omatheusmesmo.qlawkus.agent;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AgentDeliveryContext {

    private static final ThreadLocal<Holder> HOLDER = ThreadLocal.withInitial(Holder::new);

    public void set(String memoryId, String providerId, String chatId) {
        Holder h = HOLDER.get();
        h.memoryId = memoryId;
        h.providerId = providerId;
        h.chatId = chatId;
    }

    public String memoryId() {
        return HOLDER.get().memoryId;
    }

    public String providerId() {
        return HOLDER.get().providerId;
    }

    public String chatId() {
        return HOLDER.get().chatId;
    }

    public boolean hasDeliveryInfo() {
        Holder h = HOLDER.get();
        return h.providerId != null && h.chatId != null;
    }

    public void clear() {
        HOLDER.remove();
    }

    public Snapshot snapshot() {
        Holder h = HOLDER.get();
        return new Snapshot(h.memoryId, h.providerId, h.chatId);
    }

    public void restore(Snapshot snapshot) {
        set(snapshot.memoryId(), snapshot.providerId(), snapshot.chatId());
    }

    public record Snapshot(String memoryId, String providerId, String chatId) {
    }

    private static class Holder {
        String memoryId;
        String providerId;
        String chatId;
    }
}
