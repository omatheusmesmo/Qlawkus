package dev.omatheusmesmo.qlawkus.agent;

import dev.omatheusmesmo.qlawkus.model.ServedTier;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import io.quarkus.logging.Log;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.logmanager.MDC;

/**
 * One agent turn as the operator sees it: a single INFO line on the way out, carrying the channel,
 * the conversation, how long it took, which model tier answered, the tools it ran, the facts active
 * memory injected, the token counts when the provider reported them, and the outcome. Never the
 * message, the reply, the arguments or the results.
 *
 * <p>The turn is bound to the thread that runs the model call through {@link #bind()}, which also puts
 * {@code channel}, {@code conversation} and {@code workload} into the MDC for the duration. A
 * streamed turn cannot rely on that thread for everything: the tool loop continues on the provider's
 * callback threads, so the stream seam reports tools and tiers itself and the tool decorators stay
 * out of its way. The conversation id is the lookup key for work that has no thread in common with
 * the turn, such as a tool call, which receives the memory id and nothing else.
 */
public final class AgentTurn {

    private static final String CHANNEL_KEY = "channel";
    private static final String CONVERSATION_KEY = "conversation";
    private static final String WORKLOAD_KEY = "workload";
    private static final String CHANNEL_REST = "rest";
    private static final String CHANNEL_SYSTEM = "system";
    private static final String UNKNOWN = "unknown";
    private static final ThreadLocal<AgentTurn> BOUND = new ThreadLocal<>();
    private static final Map<String, AgentTurn> OPEN = new ConcurrentHashMap<>();

    private final String conversation;
    private final String channel;
    private final String workload;
    private final boolean streamed;
    private final long startedAt = System.nanoTime();
    private final List<String> tools = new CopyOnWriteArrayList<>();
    private final AtomicInteger facts = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Integer inputTokens;
    private volatile Integer outputTokens;
    private volatile String tier;

    private AgentTurn(String conversation, String channel, String workload, boolean streamed) {
        this.conversation = conversation;
        this.channel = channel;
        this.workload = workload;
        this.streamed = streamed;
    }

    /**
     * Opens a turn for {@code conversation}. Opening does not bind it to the thread; {@link #bind()}
     * does, and every turn must be closed exactly once.
     */
    public static AgentTurn open(String conversation, String channel, boolean streamed) {
        AgentTurn turn = new AgentTurn(conversation, channel, WorkloadContext.current(), streamed);
        OPEN.put(conversation, turn);
        return turn;
    }

    /** The channel a turn arrived on: the messaging provider when one delivers it, else REST. */
    public static String channelOf(String conversation, AgentDeliveryContext delivery) {
        if (ConversationId.SYSTEM.equals(conversation)) {
            return CHANNEL_SYSTEM;
        }
        if (delivery != null && delivery.hasDeliveryInfo()) {
            return delivery.providerId();
        }
        return CHANNEL_REST;
    }

    /** The turn bound to this thread, or {@code null} when none is. */
    public static AgentTurn current() {
        return BOUND.get();
    }

    /** The open turn of {@code conversation}, which work on other threads can reach by its id. */
    public static Optional<AgentTurn> forConversation(String conversation) {
        return conversation == null ? Optional.empty() : Optional.ofNullable(OPEN.get(conversation));
    }

    /** Outcome word for a finished stream: a failure, a cancellation or a clean completion. */
    public static String outcomeOf(Throwable failure, boolean cancelled) {
        if (failure != null) {
            return "error";
        }
        return cancelled ? "cancelled" : "ok";
    }

    /**
     * Binds this turn to the current thread and the MDC. Closing the returned handle unbinds it and
     * reads the tier the last synchronous model call on this thread was served by, so a turn that
     * never streams gets its tier without a callback.
     */
    public Binding bind() {
        AgentTurn previous = BOUND.get();
        BOUND.set(this);
        ServedTier.clear();
        putMdc();
        return new Binding(previous);
    }

    public boolean streamed() {
        return streamed;
    }

    public void tool(String name) {
        tools.add(name);
    }

    public void facts(int count) {
        facts.addAndGet(count);
    }

    public void usage(Integer input, Integer output) {
        if (input != null) {
            inputTokens = input;
        }
        if (output != null) {
            outputTokens = output;
        }
    }

    public void tier(String servedBy) {
        if (servedBy != null) {
            tier = servedBy;
        }
    }

    /**
     * Logs the turn's one INFO line and removes it from the open turns. Idempotent, so the seam that
     * finishes a turn and a failure path can both call it.
     */
    public void close(String outcome) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        OPEN.remove(conversation, this);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        putMdc();
        try {
            Log.infof("Agent turn channel=%s conversation=%s workload=%s outcome=%s tier=%s duration=%dms "
                            + "tools=%s facts=%d tokens=%s",
                    channel, conversation, workload, outcome, orUnknown(tier), durationMs, describeTools(),
                    facts.get(), describeTokens());
        } finally {
            clearMdc();
        }
    }

    private void putMdc() {
        MDC.put(CHANNEL_KEY, channel);
        MDC.put(CONVERSATION_KEY, conversation);
        MDC.put(WORKLOAD_KEY, workload);
    }

    private static void clearMdc() {
        MDC.remove(CHANNEL_KEY);
        MDC.remove(CONVERSATION_KEY);
        MDC.remove(WORKLOAD_KEY);
    }

    private String describeTools() {
        if (tools.isEmpty()) {
            return "0";
        }
        return tools.size() + " [" + String.join(",", tools) + "]";
    }

    private String describeTokens() {
        if (inputTokens == null && outputTokens == null) {
            return UNKNOWN;
        }
        return orUnknown(inputTokens) + "/" + orUnknown(outputTokens);
    }

    private static String orUnknown(Object value) {
        return value == null ? UNKNOWN : value.toString();
    }

    /** The thread binding of a turn. Closing it restores the previous binding and clears the MDC. */
    public final class Binding implements AutoCloseable {

        private final AgentTurn previous;

        private Binding(AgentTurn previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (!streamed && tier == null) {
                tier(ServedTier.lastOnThread());
            }
            if (previous == null) {
                BOUND.remove();
            } else {
                BOUND.set(previous);
            }
            clearMdc();
        }
    }
}
