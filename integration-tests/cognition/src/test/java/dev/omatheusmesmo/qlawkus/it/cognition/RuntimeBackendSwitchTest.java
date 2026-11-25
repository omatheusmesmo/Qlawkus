package dev.omatheusmesmo.qlawkus.it.cognition;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.omatheusmesmo.qlawkus.approval.ApprovalStore;
import dev.omatheusmesmo.qlawkus.approval.FileApprovalStore;
import dev.omatheusmesmo.qlawkus.skill.MarkdownSkillStore;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.OwnerChatStore;
import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownEpisodicStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownFactStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownOwnerChatStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownSoulStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownUserProfileStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownWorkingMemoryStore;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves the Postgres extension can sit on the classpath and stay out of the way: with the markdown
 * backend chosen at boot and no database anywhere, the application starts and every store SPI resolves
 * to its file-backed implementation. This is what lets one published image serve the quickstart with
 * no database and a Postgres deployment from the same bytes.
 */
@QuarkusTest
@TestProfile(RuntimeBackendSwitchTest.MarkdownAtBoot.class)
class RuntimeBackendSwitchTest {

    @Inject
    FactStore facts;

    @Inject
    EpisodicStore episodes;

    @Inject
    SoulStore soul;

    @Inject
    UserProfileStore profile;

    @Inject
    WorkingMemoryStore workingMemory;

    @Inject
    OwnerChatStore ownerChat;

    @Inject
    ScheduledTaskStore tasks;

    @Inject
    SkillStore skills;

    @Inject
    ApprovalStore approvals;

    @Test
    void everyStoreResolvesToItsMarkdownImplementationWithNoDatabase() {
        assertInstanceOf(MarkdownFactStore.class, ClientProxy.unwrap(facts));
        assertInstanceOf(MarkdownEpisodicStore.class, ClientProxy.unwrap(episodes));
        assertInstanceOf(MarkdownSoulStore.class, ClientProxy.unwrap(soul));
        assertInstanceOf(MarkdownUserProfileStore.class, ClientProxy.unwrap(profile));
        assertInstanceOf(MarkdownWorkingMemoryStore.class, ClientProxy.unwrap(workingMemory));
        assertInstanceOf(MarkdownOwnerChatStore.class, ClientProxy.unwrap(ownerChat));
        assertInstanceOf(MarkdownScheduledTaskStore.class, ClientProxy.unwrap(tasks));
        assertInstanceOf(MarkdownSkillStore.class, ClientProxy.unwrap(skills));
        assertInstanceOf(FileApprovalStore.class, ClientProxy.unwrap(approvals));
    }

    @Test
    void theDebugHistoryEndpointAnswersConflictInsteadOfFailingWithoutADatabase() {
        given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                .when().get("/api/admin/debug-history")
                .then().statusCode(409);
    }

    public static class MarkdownAtBoot implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "qlawkus.cognition.backend", "markdown",
                    "quarkus.datasource.devservices.enabled", "false",
                    "quarkus.arc.remove-unused-beans", "false",
                    "qlawkus.agent.facts.root", "target/runtime-switch-it/facts",
                    "qlawkus.agent.episodic.root", "target/runtime-switch-it/journals",
                    "qlawkus.agent.working-memory.root", "target/runtime-switch-it/working-memory",
                    "qlawkus.agent.state.root", "target/runtime-switch-it/state");
        }
    }
}
