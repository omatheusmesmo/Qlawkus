package dev.omatheusmesmo.qlawkus.console.voice;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Counts the live voice sessions each owner holds, so one owner cannot open more microphones than
 * {@code qlawkus.console.voice.max-sessions-per-owner} allows. Releasing an owner with no open session
 * is a no-op rather than a negative count.
 */
@ApplicationScoped
public class VoiceSessionLimiter {

    private final int maxPerOwner;
    private final Map<String, Integer> open = new HashMap<>();

    @Inject
    public VoiceSessionLimiter(ConsoleVoiceConfig config) {
        this(config.maxSessionsPerOwner());
    }

    VoiceSessionLimiter(int maxPerOwner) {
        this.maxPerOwner = maxPerOwner;
    }

    synchronized boolean tryAcquire(String owner) {
        int current = open.getOrDefault(owner, 0);
        if (current >= maxPerOwner) {
            return false;
        }
        open.put(owner, current + 1);
        return true;
    }

    synchronized void release(String owner) {
        int current = open.getOrDefault(owner, 0);
        if (current <= 1) {
            open.remove(owner);
        } else {
            open.put(owner, current - 1);
        }
    }
}
