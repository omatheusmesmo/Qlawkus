package dev.omatheusmesmo.qlawkus.deployment;

import dev.omatheusmesmo.qlawkus.agent.QlawkusAgent;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * langchain4j-agentic finds the {@code @Agent} method and the {@code @ToolProviderSupplier} and
 * {@code @ToolsSupplier} statics of a declarative agent by reflecting on its interface. A native image
 * answers that reflection only for registered types, so without this registration every turn through
 * {@link QlawkusAgent} (scheduled tasks and the messaging channels) failed with "No agent method found"
 * while the JVM image ran them.
 */
class QlawkusAgentReflectionTest {

    @Test
    void theDeclarativeAgentIsRegisteredWithItsMethods() {
        ReflectiveClassBuildItem registered = new ClientProcessor().registerQlawkusAgentForReflection();

        assertEquals(List.of(QlawkusAgent.class.getName()), registered.getClassNames());
        assertTrue(registered.isMethods(), "the agent method and the supplier statics are found by reflection");
    }
}
