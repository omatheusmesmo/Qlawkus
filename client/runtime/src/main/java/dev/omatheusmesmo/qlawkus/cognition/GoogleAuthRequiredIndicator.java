package dev.omatheusmesmo.qlawkus.cognition;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GoogleAuthRequiredIndicator {

    private static final ThreadLocal<boolean[]> FLAG = ThreadLocal.withInitial(() -> new boolean[1]);

    public void markAuthRequired() {
        FLAG.get()[0] = true;
    }

    public boolean isAuthRequired() {
        return FLAG.get()[0];
    }

    public void reset() {
        FLAG.remove();
    }
}
