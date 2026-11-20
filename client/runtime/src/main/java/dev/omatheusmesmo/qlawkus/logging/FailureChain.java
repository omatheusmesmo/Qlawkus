package dev.omatheusmesmo.qlawkus.logging;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

/**
 * Walks the cause chain of a failure. Frameworks wrap the exception that matters (reflection, Mutiny,
 * the AI service), so both the root and the nearest instance of a type are found by walking, never by
 * reading the outermost exception.
 */
public final class FailureChain {

    private FailureChain() {
    }

    /** The deepest cause, or the failure itself when it has none. A cycle ends the walk at the repeat. */
    public static Throwable root(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current.getCause() != null && seen.add(current)) {
            current = current.getCause();
        }
        return current;
    }

    /** The first link of the chain, starting at the failure itself, that is an instance of {@code type}. */
    public static <T extends Throwable> Optional<T> first(Throwable failure, Class<T> type) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && seen.add(current)) {
            if (type.isInstance(current)) {
                return Optional.of(type.cast(current));
            }
            current = current.getCause();
        }
        return Optional.empty();
    }
}
