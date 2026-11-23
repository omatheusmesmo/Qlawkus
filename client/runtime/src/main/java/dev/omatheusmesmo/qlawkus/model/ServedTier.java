package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Records which tier of the chat chain produced a reply: the primary, the same-provider secondary, or
 * the Ollama fallback. The guards already know this at the moment they accept an answer; this class
 * carries that fact to the turn log, which runs somewhere else.
 *
 * <p>A blocking call answers on the thread that made it, so the last answer on that thread is the
 * tier of the reply. A streamed call answers on the provider's callback thread, where that is no
 * longer true, so the answer is kept against the response object, which the stream hands on to the
 * AI service unchanged.
 */
public final class ServedTier {

    public static final String PRIMARY = "primary";
    public static final String SECONDARY = "secondary";
    public static final String FALLBACK = "fallback";

    /** The tier that answered the owner last and when, for the console. */
    public record Last(String tier, Instant at) {
    }

    private static final ThreadLocal<String> LAST = new ThreadLocal<>();
    private static volatile Last lastInteractive;
    private static final Map<ChatResponse, String> BY_RESPONSE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ServedTier() {
    }

    /** Records that {@code tier} produced {@code response} and returns the response unchanged. */
    public static ChatResponse answered(String tier, ChatResponse response) {
        LAST.set(tier);
        if (WorkloadContext.INTERACTIVE.equals(WorkloadContext.current())) {
            lastInteractive = new Last(tier, Instant.now());
        }
        if (response != null) {
            BY_RESPONSE.put(response, tier);
        }
        return response;
    }

    /** Forgets what was served last, for tests that need a clean slate. */
    public static void forgetLastServed() {
        lastInteractive = null;
    }

    /**
     * The tier that last answered an interactive turn, whatever thread it ran on, so the console can
     * show it. A background answer never counts: it is not what the owner was served. A streamed answer
     * arrives on a callback thread that carries no workload, which reads as interactive.
     */
    public static Optional<Last> lastServed() {
        return Optional.ofNullable(lastInteractive);
    }

    /** Forgets the last answer on this thread, so a new call starts without a stale tier. */
    public static void clear() {
        LAST.remove();
    }

    /** The tier of the last blocking answer on this thread, or {@code null} when there was none. */
    public static String lastOnThread() {
        return LAST.get();
    }

    /** The tier that produced a streamed response, when it came through a guard. */
    public static Optional<String> of(ChatResponse response) {
        if (response == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_RESPONSE.get(response));
    }
}
