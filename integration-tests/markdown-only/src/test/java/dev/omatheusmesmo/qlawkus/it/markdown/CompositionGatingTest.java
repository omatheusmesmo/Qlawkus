package dev.omatheusmesmo.qlawkus.it.markdown;

import dev.omatheusmesmo.qlawkus.composition.CompositionManifest;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closes the composition proof chain at its far end. The maven-invoker IT in composition-maven-plugin
 * proves the first half - a deselected capability loses its dependency in the reconciled pom - and this
 * proves the consequence: with the dependency gone, the capability contributes no CDI bean to the
 * running container.
 *
 * <p>This module is the natural host because its baked manifest is {@code default: disabled} with no
 * {@code except} list, so every declared capability in the reactor is deselected at once and only the
 * always-present skeleton ({@code qlawkus-client}) remains.
 */
@QuarkusTest
class CompositionGatingTest {

    /**
     * One bean per declared capability, chosen so a hit means the module really was composed in.
     * Mirrors the {@code metadata.qlawkus.capability} keys in each module's quarkus-extension.yaml.
     */
    private static final Map<String, String> CAPABILITY_BEANS = Map.of(
            "cognition.pgvector", "dev.omatheusmesmo.qlawkus.store.pg.PgFactStore",
            "console", "dev.omatheusmesmo.qlawkus.console.ConsoleResource",
            "brag", "dev.omatheusmesmo.qlawkus.tools.brag.BragTool",
            "skill-hub", "dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubTool",
            "google-workspace", "dev.omatheusmesmo.qlawkus.tools.google.calendar.CalendarTool",
            "messaging.discord", "dev.omatheusmesmo.qlawkus.messaging.discord.DiscordProviderAdapter",
            "messaging.telegram", "dev.omatheusmesmo.qlawkus.messaging.telegram.TelegramProviderAdapter",
            "messaging.slack", "dev.omatheusmesmo.qlawkus.messaging.slack.SlackProviderAdapter",
            "messaging.whatsapp", "dev.omatheusmesmo.qlawkus.messaging.whatsapp.WhatsAppProviderAdapter");

    /** Tools the skeleton always carries, so an all-absent assertion can never pass vacuously. */
    private static final List<String> SKELETON_TOOLS = List.of(
            "dev.omatheusmesmo.qlawkus.tool.shell.FileTool",
            "dev.omatheusmesmo.qlawkus.tool.shell.ShellTool",
            "dev.omatheusmesmo.qlawkus.tool.review.CodeReviewTool");

    private static CompositionManifest bakedManifest;

    @Inject
    @QlawTool
    Instance<Object> registeredTools;

    @BeforeAll
    static void readBakedManifest() {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(CompositionPaths.DEFAULT_MANIFEST)) {
            assertNotNull(in, "the baked manifest must ship at " + CompositionPaths.DEFAULT_MANIFEST);
            bakedManifest = CompositionManifestParser.parse(in);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read the baked manifest", e);
        }
    }

    @Test
    void bakedManifest_deselectsEveryDeclaredCapability() {
        for (String capability : CAPABILITY_BEANS.keySet()) {
            assertFalse(bakedManifest.buildTime().isEnabled(capability),
                    "manifest should deselect " + capability + " under 'default: disabled'");
        }
    }

    @Test
    void deselectedCapability_contributesNoBean() {
        for (Map.Entry<String, String> entry : CAPABILITY_BEANS.entrySet()) {
            String capability = entry.getKey();
            String beanClass = entry.getValue();

            assertFalse(classPresent(beanClass),
                    "deselected capability " + capability + " must not put " + beanClass + " on the classpath");
        }
    }

    @Test
    void skeletonTools_remainRegistered() {
        Set<String> registered = registeredToolClassNames();
        for (String tool : SKELETON_TOOLS) {
            assertTrue(registered.contains(tool),
                    "skeleton tool " + tool + " must stay registered; got " + registered);
        }
    }

    @Test
    void noRegisteredTool_comesFromADeselectedCapability() {
        Set<String> registered = registeredToolClassNames();
        Set<String> leaked = registered.stream()
                .filter(name -> CAPABILITY_BEANS.values().stream().anyMatch(name::equals))
                .collect(Collectors.toSet());
        assertEquals(Set.of(), leaked, "no tool from a deselected capability may be registered");
    }

    private Set<String> registeredToolClassNames() {
        return registeredTools.stream()
                .map(ClientProxy::unwrap)
                .map(tool -> {
                    Class<?> c = tool.getClass();
                    while (c.getName().contains("_Subclass")) {
                        c = c.getSuperclass();
                    }
                    return c.getName();
                })
                .collect(Collectors.toSet());
    }

    private static boolean classPresent(String fqn) {
        try {
            Class.forName(fqn, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
