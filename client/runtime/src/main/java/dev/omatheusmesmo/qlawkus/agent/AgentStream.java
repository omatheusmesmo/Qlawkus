package dev.omatheusmesmo.qlawkus.agent;

import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Streams an agent turn as typed {@link AgentEvent}s: the reply text plus the start and end of every
 * tool call and a closing summary.
 *
 * <p>It is built on the AI service's own event stream rather than on {@code QlawToolProvider}'s
 * executor decorators because those only see the {@code @QlawTool} extension tools, while the tools
 * declared on {@code @RegisterAiService(tools = ...)} run through the framework's executors. The
 * stream reports both kinds from one place, with the conversation known.
 *
 * <p>Tool results never enter an event. Each tool event is also fired as a {@link ToolActivityEvent}
 * so a channel that only needs to observe, not consume, can do so.
 */
@ApplicationScoped
public class AgentStream {

    private final AgentService agent;
    private final Event<ToolActivityEvent> activity;
    private final SecretRedactor redactor;

    @Inject
    public AgentStream(AgentService agent, Event<ToolActivityEvent> activity, SecretRedactor redactor) {
        this.agent = agent;
        this.activity = activity;
        this.redactor = redactor;
    }

    public Multi<AgentEvent> stream(String conversationId, String message) {
        return Multi.createFrom().deferred(() -> {
            Turn turn = new Turn(conversationId);
            return agent.chatEvents(conversationId, message)
                    .onItem().transformToIterable(turn::translate)
                    .onCompletion().continueWith(turn::done);
        });
    }

    private final class Turn {

        private final String conversationId;
        private final long startedAt = System.nanoTime();
        private final AtomicInteger sequence = new AtomicInteger();
        private final AtomicInteger toolCalls = new AtomicInteger();
        private final AtomicReference<Integer> inputTokens = new AtomicReference<>();
        private final AtomicReference<Integer> outputTokens = new AtomicReference<>();
        private final Map<String, Deque<Pending>> pending = new ConcurrentHashMap<>();

        private Turn(String conversationId) {
            this.conversationId = conversationId;
        }

        List<AgentEvent> translate(ChatEvent event) {
            List<AgentEvent> out = new ArrayList<>(1);
            if (event instanceof ChatEvent.PartialResponseEvent partial) {
                out.add(new AgentEvent.Text(partial.getChunk()));
            } else if (event instanceof ChatEvent.BeforeToolExecutionEvent before) {
                out.add(publish(started(before)));
            } else if (event instanceof ChatEvent.ToolExecutedEvent executed) {
                out.add(publish(finished(executed)));
            } else if (event instanceof ChatEvent.ChatCompletedEvent completed) {
                recordUsage(completed);
            }
            return out;
        }

        List<AgentEvent> done() {
            return List.of(new AgentEvent.Done(inputTokens.get(), outputTokens.get(), toolCalls.get(),
                    millisSince(startedAt)));
        }

        private AgentEvent.ToolStart started(ChatEvent.BeforeToolExecutionEvent before) {
            var request = before.getRequest();
            String id = hasText(request.id()) ? request.id() : "call-" + sequence.incrementAndGet();
            pending.computeIfAbsent(key(request.id(), request.name()), k -> new ConcurrentLinkedDeque<>())
                    .addLast(new Pending(id, System.nanoTime()));
            toolCalls.incrementAndGet();
            return new AgentEvent.ToolStart(id, request.name(),
                    ToolProgress.VERBOSE.detail(redactor.redact(request.arguments())));
        }

        private AgentEvent.ToolEnd finished(ChatEvent.ToolExecutedEvent executed) {
            var execution = executed.getExecution();
            var request = execution.request();
            Deque<Pending> queue = pending.get(key(request.id(), request.name()));
            Pending start = queue == null ? null : queue.pollFirst();
            String id = start != null ? start.id() : "call-" + sequence.incrementAndGet();
            long durationMs = execution.duration() != null ? execution.duration().toMillis()
                    : start != null ? millisSince(start.startedAt()) : 0L;
            return new AgentEvent.ToolEnd(id, request.name(), !execution.hasFailed(), durationMs);
        }

        private void recordUsage(ChatEvent.ChatCompletedEvent completed) {
            var response = completed.getChatResponse();
            var usage = response == null ? null : response.tokenUsage();
            if (usage != null) {
                inputTokens.set(usage.inputTokenCount());
                outputTokens.set(usage.outputTokenCount());
            }
        }

        private AgentEvent publish(AgentEvent.ToolEvent event) {
            try {
                activity.fire(new ToolActivityEvent(conversationId, event));
            } catch (RuntimeException e) {
                Log.warnf(e, "A tool activity observer failed for %s", event.name());
            }
            return event;
        }
    }

    private record Pending(String id, long startedAt) {
    }

    private static String key(String id, String name) {
        return hasText(id) ? id : "name:" + name;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static long millisSince(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000L;
    }
}
