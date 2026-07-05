package dev.omatheusmesmo.qlawkus.it.argon2auth;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs {@link Argon2NativeAuthTest} against the packaged application, including the GraalVM native
 * image under {@code -Pnative}. This is the coverage the JVM {@code @QuarkusTest} cannot give: the
 * Bouncy Castle Argon2id verifier executing inside a native binary.
 */
@QuarkusIntegrationTest
class Argon2NativeAuthIT extends Argon2NativeAuthTest {
}
