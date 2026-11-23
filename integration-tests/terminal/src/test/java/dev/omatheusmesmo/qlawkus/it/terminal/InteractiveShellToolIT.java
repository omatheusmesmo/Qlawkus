package dev.omatheusmesmo.qlawkus.it.terminal;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@QuarkusIntegrationTest
@EnabledOnOs(OS.LINUX)
class InteractiveShellToolIT extends InteractiveShellToolTest {
}
