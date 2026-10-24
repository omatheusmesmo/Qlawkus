package dev.omatheusmesmo.qlawkus.it.cognition;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs {@link MigrationReadinessTest} against the packaged application, including the GraalVM native
 * image under {@code -Pnative}. It is the one check in this module that needs no model, so it is what
 * proves, on every build, that the packaged artifact boots against the Dev Services Postgres and that
 * Flyway finds and applies the bundled migrations there (a resource missing from the native image
 * would leave them pending).
 */
@QuarkusIntegrationTest
class MigrationReadinessIT extends MigrationReadinessTest {
}
