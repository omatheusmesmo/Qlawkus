package dev.omatheusmesmo.qlawkus.metrics;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.time.Duration;
import java.util.Locale;

/**
 * Times every call into a cognition store and tags it with which store, which backend, and which
 * operation.
 *
 * <p>This is what gives the {@code markdown | pgvector | hybrid} switch operational meaning. The same
 * call is a local file read on one backend and a network round trip on another, and until now the
 * only way to compare them was to change the setting and judge by feel.
 */
@Metered
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
public class StoreMeterInterceptor {

    private static final String UNKNOWN = "unknown";

    @Inject
    AgentMeters meters;

    @AroundInvoke
    Object measure(InvocationContext context) throws Exception {
        if (!meters.enabled()) {
            return context.proceed();
        }
        Class<?> implementation = implementationOf(context.getTarget());
        String store = storeNameOf(implementation);
        long startedAt = System.nanoTime();
        boolean succeeded = false;
        try {
            Object result = context.proceed();
            succeeded = true;
            return result;
        } finally {
            meters.store(store, backendOf(implementation, store), context.getMethod().getName(),
                    Duration.ofNanos(System.nanoTime() - startedAt), succeeded);
        }
    }

    /** Steps past the generated CDI subclass so the tags describe the real implementation. */
    private static Class<?> implementationOf(Object target) {
        Class<?> type = target.getClass();
        while (type.getSimpleName().contains("_Subclass")) {
            type = type.getSuperclass();
        }
        return type;
    }

    /**
     * The SPI the implementation answers, taken from the interface it implements rather than from its
     * own name, so the tag stays stable if an implementation is ever renamed.
     */
    private static String storeNameOf(Class<?> implementation) {
        for (Class<?> type = implementation; type != null; type = type.getSuperclass()) {
            for (Class<?> candidate : type.getInterfaces()) {
                if (candidate.getSimpleName().endsWith("Store")) {
                    return candidate.getSimpleName();
                }
            }
        }
        return UNKNOWN;
    }

    /**
     * The backend is whatever prefixes the SPI name in the implementation's own name, which the store
     * naming convention makes reliable: {@code MarkdownFactStore}, {@code PgFactStore},
     * {@code HybridFactStore}. {@code Pg} is reported as {@code pgvector} to match the value of
     * {@code qlawkus.cognition.backend}, so the tag and the setting read the same.
     */
    private static String backendOf(Class<?> implementation, String store) {
        String name = implementation.getSimpleName();
        if (store.equals(UNKNOWN) || !name.endsWith(store)) {
            return UNKNOWN;
        }
        String prefix = name.substring(0, name.length() - store.length()).toLowerCase(Locale.ROOT);
        return switch (prefix) {
            case "" -> UNKNOWN;
            case "pg" -> "pgvector";
            default -> prefix;
        };
    }
}
