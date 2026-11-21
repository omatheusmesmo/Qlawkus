package dev.omatheusmesmo.qlawkus.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.validator.BeanValidationConfigValidatorImpl;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The resource budgets are bounded by configuration validation, so a bad value stops boot. */
class McpConfigValidationTest {
    private static McpConfig load(String name, String value) {
        return new SmallRyeConfigBuilder().withValidator(new BeanValidationConfigValidatorImpl()).withMapping(McpConfig.class)
                .withConverter(Duration.class, 100, new DurationConverter())
                .withDefaultValue(name, value).build().getConfigMapping(McpConfig.class);
    }

    @Test
    void defaultsAndBoundaryValuesAreAccepted() {
        assertDoesNotThrow(() -> load("qlawkus.mcp.max-servers", "64"));
        assertDoesNotThrow(() -> load("qlawkus.mcp.discovery-timeout", "60s"));
        assertDoesNotThrow(() -> load("qlawkus.mcp.call-timeout", "300s"));
        assertDoesNotThrow(() -> load("qlawkus.mcp.retry-backoff", "1h"));
        assertDoesNotThrow(() -> load("qlawkus.mcp.max-output-chars", "128"));
    }

    @Test
    void outOfRangeBudgetsAreRefused() {
        for (String[] bad : new String[][] {
                {"qlawkus.mcp.max-servers", "0"}, {"qlawkus.mcp.max-servers", "65"},
                {"qlawkus.mcp.max-tools-per-server", "129"}, {"qlawkus.mcp.max-tools", "513"},
                {"qlawkus.mcp.max-output-chars", "127"}, {"qlawkus.mcp.max-output-chars", "1000001"},
                {"qlawkus.mcp.discovery-timeout", "61s"}, {"qlawkus.mcp.discovery-timeout", "0s"},
                {"qlawkus.mcp.call-timeout", "301s"}, {"qlawkus.mcp.retry-backoff", "3601s"}}) {
            assertThrows(ConfigValidationException.class, () -> load(bad[0], bad[1]), bad[0] + "=" + bad[1]);
        }
    }
}
