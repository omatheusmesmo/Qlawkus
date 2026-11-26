package dev.omatheusmesmo.qlawkus.tool.quarantine;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks consecutive failures per tool and withholds a tool from the model once it fails in a row.
 *
 * <p>This is a breaker per tool, kept here rather than on SmallRye's because the open state has to be
 * visible to the catalog: {@code QlawToolProvider} asks {@link #isQuarantined} on every turn and leaves
 * the tool out, which a breaker that rejects the call cannot do, since the model would still be offered
 * the tool and keep choosing it. State lives in memory on purpose. A restart is a fresh start for the
 * dependencies too, and a tool that is still broken trips again after one streak.
 *
 * <p>A quarantined tool comes back when its cooldown ends, on probation: the streak resumes one
 * failure short of the threshold, so a single failed call quarantines it again and a success clears it.
 */
@ApplicationScoped
public class ToolQuarantine {

    private final ToolQuarantineConfig config;
    private final Clock clock;
    private final Consumer<ToolQuarantinedEvent> events;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    @Inject
    ToolQuarantine(ToolQuarantineConfig config, Event<ToolQuarantinedEvent> events) {
        this(config, Clock.systemUTC(), events::fireAsync);
    }

    ToolQuarantine(ToolQuarantineConfig config, Clock clock, Consumer<ToolQuarantinedEvent> events) {
        this.config = config;
        this.clock = clock;
        this.events = events;
    }

    /** One tool taken out of the catalog and the instant it returns on probation. */
    public record Entry(String tool, int failures, Instant since, Instant until) {
    }

    public void recordSuccess(String tool) {
        if (config.enabled()) {
            states.remove(tool);
        }
    }

    public void recordFailure(String tool) {
        if (!config.enabled()) {
            return;
        }
        ToolQuarantinedEvent tripped = trip(tool);
        if (tripped != null) {
            Log.warnf("Tool %s quarantined after %d failures in a row, back on probation at %s", tool,
                    tripped.failures(), tripped.until());
            events.accept(tripped);
        }
    }

    private ToolQuarantinedEvent trip(String tool) {
        ToolQuarantinedEvent[] fired = new ToolQuarantinedEvent[1];
        states.compute(tool, (name, current) -> {
            Instant now = clock.instant();
            State state = current == null ? new State(0, null, null) : current;
            if (state.until() != null && now.isBefore(state.until())) {
                return state;
            }
            int failures = state.failures() + 1;
            if (failures < config.failureThreshold()) {
                return new State(failures, null, null);
            }
            Instant until = now.plus(config.cooldown());
            fired[0] = new ToolQuarantinedEvent(tool, failures, until);
            return new State(config.failureThreshold() - 1, now, until);
        });
        return fired[0];
    }

    public boolean isQuarantined(String tool) {
        if (!config.enabled()) {
            return false;
        }
        State state = states.get(tool);
        return state != null && state.until() != null && clock.instant().isBefore(state.until());
    }

    /** The tools out of the catalog right now, soonest to return first. */
    public List<Entry> quarantined() {
        Instant now = clock.instant();
        return states.entrySet().stream()
                .filter(e -> e.getValue().until() != null && now.isBefore(e.getValue().until()))
                .map(e -> new Entry(e.getKey(), config.failureThreshold(), e.getValue().since(),
                        e.getValue().until()))
                .sorted(Comparator.comparing(Entry::until))
                .toList();
    }

    /** Returns the tool to the catalog with a fresh streak. False when it was not quarantined. */
    public boolean release(String tool) {
        boolean wasQuarantined = isQuarantined(tool);
        states.remove(tool);
        if (wasQuarantined) {
            Log.infof("Tool %s released from quarantine", tool);
        }
        return wasQuarantined;
    }

    private record State(int failures, Instant since, Instant until) {
    }
}
