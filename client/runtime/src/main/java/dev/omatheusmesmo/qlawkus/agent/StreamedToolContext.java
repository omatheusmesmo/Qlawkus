package dev.omatheusmesmo.qlawkus.agent;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.ManagedContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carries the caller's context onto the threads a streamed turn runs its tools on.
 *
 * <p>A streamed turn returns before its tool loop runs, and the loop continues on the model client's
 * callback thread, which has neither the caller's request context nor its {@link AgentDeliveryContext}.
 * {@link AgentStream} captures both on the caller's thread when the turn starts, keyed by the
 * conversation, and releases them when the turn ends. Each tool call then restores them for its own
 * duration through {@link #enter(Object)}, so a tool sees the same context it would on the synchronous
 * path, and the executing thread is left as it was found.
 *
 * <p>With no request context on the caller a fresh one is used and terminated afterwards, so
 * {@code @RequestScoped} beans still work for that call without outliving it. A thread that already has
 * its own context is left alone (see {@link #enter(Object)}).
 */
@ApplicationScoped
public class StreamedToolContext {

    private final AgentDeliveryContext delivery;
    private final Map<String, Caller> callers = new ConcurrentHashMap<>();

    @Inject
    public StreamedToolContext(AgentDeliveryContext delivery) {
        this.delivery = delivery;
    }

    public void capture(String conversationId) {
        ManagedContext requests = Arc.container().requestContext();
        ContextState request = requests.isActive() ? requests.getState() : null;
        callers.put(conversationId, new Caller(delivery.snapshot(), request));
    }

    public void release(String conversationId) {
        callers.remove(conversationId);
    }

    /**
     * Restores the captured context for a tool call of {@code memoryId}, but only on a thread that has
     * none of its own: the model client's callback thread. A thread that already carries a request
     * context or delivery values is a synchronous caller (REST, console and messaging can share one
     * conversation id), and its context is the right one for its tools.
     */
    public Entered enter(Object memoryId) {
        Caller caller = memoryId instanceof String conversationId ? callers.get(conversationId) : null;
        ManagedContext requests = Arc.container().requestContext();
        if (caller == null || requests.isActive() || delivery.hasDeliveryInfo()) {
            return Entered.NONE;
        }
        AgentDeliveryContext.Snapshot previousDelivery = delivery.snapshot();
        delivery.restore(caller.delivery());
        boolean fresh = caller.request() == null;
        if (fresh) {
            requests.activate();
        } else {
            requests.activate(caller.request());
        }
        return new Scope(previousDelivery, requests, fresh);
    }

    /**
     * Restores the context a turn was started in. Closing it puts the executing thread back the way
     * it was found.
     */
    public interface Entered extends AutoCloseable {

        Entered NONE = () -> {
        };

        @Override
        void close();
    }

    private record Caller(AgentDeliveryContext.Snapshot delivery, ContextState request) {
    }

    private final class Scope implements Entered {

        private final AgentDeliveryContext.Snapshot previousDelivery;
        private final ManagedContext requests;
        private final boolean fresh;

        private Scope(AgentDeliveryContext.Snapshot previousDelivery, ManagedContext requests, boolean fresh) {
            this.previousDelivery = previousDelivery;
            this.requests = requests;
            this.fresh = fresh;
        }

        @Override
        public void close() {
            try {
                if (fresh) {
                    requests.terminate();
                }
                if (requests.isActive()) {
                    requests.deactivate();
                }
            } finally {
                delivery.restore(previousDelivery);
            }
        }
    }
}
