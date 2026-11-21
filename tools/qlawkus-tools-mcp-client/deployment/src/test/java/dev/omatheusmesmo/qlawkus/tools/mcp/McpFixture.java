package dev.omatheusmesmo.qlawkus.tools.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.config.SecretsConfig;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.vertx.core.Vertx;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class McpFixture implements AutoCloseable {
    final Vertx vertx;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    final McpRegistry registry;
    final KeystoreSecretWriter secrets;
    final McpConfig config;

    McpFixture(Path root, String... overrides) throws Exception {
        this(root, null, overrides);
    }

    McpFixture(Path root, McpEndpointPolicy policy, String... overrides) throws Exception {
        this.vertx = Vertx.vertx();
        var builder = new SmallRyeConfigBuilder().withMapping(McpConfig.class).withMapping(AgentConfig.class)
                .withMapping(SecretsConfig.class).withConverter(Duration.class, 100, new DurationConverter())
                .withDefaultValue("user.home", root.toString())
                .withDefaultValue("qlawkus.agent.state.root", root.resolve("state").toString())
                .withDefaultValue("qlawkus.secrets.keystore-path", root.resolve("fixture-secrets.p12").toString())
                .withDefaultValue("qlawkus.secrets.keystore-password", "fixture-store-password")
                .withDefaultValue("qlawkus.mcp.allow-private-endpoints", "true")
                .withDefaultValue("qlawkus.mcp.discovery-timeout", "2s");
        for (int i = 0; i < overrides.length; i += 2) builder.withDefaultValue(overrides[i], overrides[i + 1]);
        var smallRye = builder.build();
        secrets = new KeystoreSecretWriter(smallRye.getConfigMapping(SecretsConfig.class));
        config = smallRye.getConfigMapping(McpConfig.class);
        var agent = smallRye.getConfigMapping(AgentConfig.class);
        registry = new McpRegistry(config, agent, secrets, new ObjectMapper(), vertx, null, executor,
                policy == null ? new McpEndpointPolicy(config.allowPrivateEndpoints()) : policy);
        var initialize = McpRegistry.class.getDeclaredMethod("initialize");
        initialize.setAccessible(true);
        initialize.invoke(registry);
    }

    ToolProviderResult tools() {
        return new McpToolContributor(registry).provideTools(null);
    }

    @Override
    public void close() throws Exception {
        registry.close();
        executor.shutdownNow();
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
