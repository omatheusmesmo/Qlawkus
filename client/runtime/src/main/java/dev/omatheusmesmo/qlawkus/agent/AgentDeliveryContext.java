package dev.omatheusmesmo.qlawkus.agent;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AgentDeliveryContext {

    private static final ThreadLocal<Holder> HOLDER = ThreadLocal.withInitial(Holder::new);

    public void set(String memoryId, String providerId, String chatId) {
        restore(new Snapshot(memoryId, providerId, chatId));
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
        return new Snapshot(h.memoryId, h.providerId, h.chatId, h.authenticatedOwner);
    }

    public void restore(Snapshot snapshot) {
        Holder h = HOLDER.get();
        h.memoryId = snapshot.memoryId();
        h.providerId = snapshot.providerId();
        h.chatId = snapshot.chatId();
        h.authenticatedOwner = snapshot.authenticatedOwner();
    }

    public record Snapshot(String memoryId, String providerId, String chatId, String authenticatedOwner) {
        public Snapshot(String memoryId, String providerId, String chatId) {
            this(memoryId, providerId, chatId, null);
        }
    }

    private static class Holder {
        String memoryId;
        String providerId;
        String chatId;
        String authenticatedOwner;
    }
}
