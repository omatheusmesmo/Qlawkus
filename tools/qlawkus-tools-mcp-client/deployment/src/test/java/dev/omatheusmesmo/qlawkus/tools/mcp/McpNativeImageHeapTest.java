package dev.omatheusmesmo.qlawkus.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuthService;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * A static random generator is initialised at image build time in native mode and fails the build
 * ("Random/SplittableRandom class in the image heap"), so the PKCE and state source must not be one.
 */
class McpNativeImageHeapTest {
    @Test
    void theOAuthServiceHoldsNoStaticRandomGenerator() {
        var offenders = Arrays.stream(McpOAuthService.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> Random.class.isAssignableFrom(field.getType()))
                .map(field -> field.getName()).toList();
        assertTrue(offenders.isEmpty(), "static random fields: " + offenders);
    }
}
