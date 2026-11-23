package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Reads every {@code @Readiness} check the app has for the Health card. The health API is reached only
 * through {@link ReadinessChecks}, never in this class's own signature, so the container can create this
 * bean in a distribution that has no health API on its classpath: {@link #available()} then answers
 * {@code false} and the console hides the card instead of failing at boot.
 */
@ApplicationScoped
public class ReadinessSource {

    private static final String HEALTH_CHECK = "org.eclipse.microprofile.health.HealthCheck";

    private final SecretRedactor redactor;

    public ReadinessSource(SecretRedactor redactor) {
        this.redactor = redactor;
    }

    public boolean available() {
        try {
            Class.forName(HEALTH_CHECK, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    public List<ReadinessView> views() {
        return ReadinessChecks.read(redactor::redact);
    }
}
