package dev.omatheusmesmo.qlawkus.it.markdown;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs {@link ReadinessCheckTest} against the packaged application, including the GraalVM native
 * image under {@code -Pnative}. This is the coverage the JVM {@code @QuarkusTest} cannot give: that
 * {@code TypedGuard}'s {@code ServiceLoader}-based {@code Spi} lookup (see {@link
 * dev.omatheusmesmo.qlawkus.model.PrimaryChatGuard}) actually resolves inside a native binary, where
 * reflective and service-loading behavior can diverge from the JVM.
 */
@QuarkusIntegrationTest
class ReadinessCheckIT extends ReadinessCheckTest {
}
