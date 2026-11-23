package dev.omatheusmesmo.qlawkus.it.console;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
@QuarkusTestResource(value = HeartbeatConsoleTest.StateRoot.class, restrictToAnnotatedClass = true)
class HeartbeatConsoleIT extends HeartbeatConsoleTest {
}
