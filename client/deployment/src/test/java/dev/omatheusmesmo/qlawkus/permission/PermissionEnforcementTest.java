package dev.omatheusmesmo.qlawkus.permission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.approval.ApprovalPolicy;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessEntry;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessRegistry;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PermissionEnforcementTest {

    private static final String TOOL = "readFile";
    private static final PermissionService.Actor ADMIN = PermissionService.Actor.admin("test");
    private static final AgentDeliveryContext.Snapshot ORIGIN = new AgentDeliveryContext.Snapshot(null, null, null);

    @Inject
    QlawToolProvider provider;

    @Inject
    PermissionService permissions;

    @Inject
    ApprovalPolicy approvalPolicy;

    @Inject
    ToolAccessRegistry registry;

    @AfterEach
    void reset() {
        permissions.reset(ADMIN, ORIGIN);
    }

    private Set<String> catalog() {
        return provider.provideTools(null).tools().keySet().stream().map(spec -> spec.name())
                .collect(Collectors.toSet());
    }

    @Test
    void aDeniedToolIsLeftOutOfTheCatalogTheModelSees() {
        assertTrue(catalog().contains(TOOL));

        permissions.setTool(TOOL, "deny", ADMIN, ORIGIN);

        assertFalse(catalog().contains(TOOL), "a denied tool must not be offered to the model at all");
        assertTrue(catalog().size() > 1, "other tools stay");
    }

    @Test
    void aDeniedCellHidesEveryToolOfItsClassInThatGroup() {
        String group = permissions.explain(TOOL).group();

        permissions.set(group, "read", "deny", ADMIN, ORIGIN);

        Set<String> names = catalog();
        registry.entries().stream()
                .filter(entry -> entry.accessClass().name().equals("READ"))
                .filter(entry -> permissions.explain(entry.tool()).group().equals(group))
                .forEach(entry -> assertFalse(names.contains(entry.tool()), entry.tool()));
    }

    @Test
    void anAskedToolGoesThroughTheApprovalGate() {
        assertFalse(approvalPolicy.requires(TOOL));

        permissions.setTool(TOOL, "ask", ADMIN, ORIGIN);

        assertTrue(approvalPolicy.requires(TOOL));
    }

    @Test
    void anAllowedToolIsNotGatedAndStaysInTheCatalog() {
        permissions.setTool(TOOL, "allow", ADMIN, ORIGIN);

        assertFalse(approvalPolicy.requires(TOOL));
        assertTrue(catalog().contains(TOOL));
    }

    @Test
    void theModelHasNoToolThatReachesThePermissionPolicy() {
        assertTrue(catalog().stream().noneMatch(name -> name.toLowerCase().contains("permission")), catalog().toString());
        assertTrue(registry.entries().stream().map(ToolAccessEntry::origin)
                .noneMatch(origin -> origin.contains(".permission.")));

        RegisterAiService declaration = AgentService.class.getAnnotation(RegisterAiService.class);
        for (Class<?> declared : declaration.tools()) {
            assertFalse(declared.getPackageName().endsWith(".permission"), declared.getName());
            Arrays.stream(declared.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(Tool.class))
                    .map(Method::getReturnType)
                    .forEach(type -> assertFalse(type.getName().contains(".permission."), type.getName()));
        }
    }
}
