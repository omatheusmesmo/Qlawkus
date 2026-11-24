package dev.omatheusmesmo.qlawkus.messaging.discord.deployment;

import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import com.fasterxml.jackson.databind.ser.std.ClassSerializer;
import dev.omatheusmesmo.qlawkus.messaging.discord.DiscordShutdownListener;
import discord4j.discordjson.json.gateway.HeartbeatConverter;
import discord4j.discordjson.json.gateway.OpcodeConverter;
import discord4j.discordjson.possible.PossibleFilter;
import discord4j.rest.json.response.ErrorResponse;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.ShutdownListenerBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;

class DiscordProcessor {

    private static final String FEATURE = "qlawkus-messaging-discord";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerHealthCheck(Capabilities capabilities, BuildProducer<AdditionalBeanBuildItem> beans) {
        if (capabilities.isPresent(Capability.SMALLRYE_HEALTH)) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass("dev.omatheusmesmo.qlawkus.messaging.discord.DiscordReadinessCheck")
                    .setDefaultScope(DotNames.APPLICATION_SCOPED)
                    .setUnremovable()
                    .build());
        }
    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    List<IndexDependencyBuildItem> indexDiscordPayloads() {
        return List.of(
                new IndexDependencyBuildItem("com.discord4j", "discord-json"),
                new IndexDependencyBuildItem("com.discord4j", "discord-json-api"),
                new IndexDependencyBuildItem("com.discord4j", "discord4j-gateway"),
                new IndexDependencyBuildItem("com.discord4j", "discord4j-voice"));
    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class)
    void registerDiscordPayloads(CombinedIndexBuildItem index, BuildProducer<ReflectiveClassBuildItem> reflection) {
        String[] payloads = index.getIndex().getKnownClasses().stream()
                .map(ClassInfo::name)
                .filter(DiscordProcessor::isGeneratedPayload)
                .map(DotName::toString)
                .sorted()
                .toArray(String[]::new);
        reflection.produce(ReflectiveClassBuildItem.builder(payloads)
                .reason("Discord4J Jackson payloads")
                .constructors().methods().fields().build());
        reflection.produce(ReflectiveClassBuildItem.builder(
                HeartbeatConverter.class, OpcodeConverter.class, PossibleFilter.class, ClassSerializer.class)
                .constructors().build());
        reflection.produce(ReflectiveClassBuildItem.builder(ErrorResponse.class)
                .constructors().methods().fields().build());
        reflection.produce(ReflectiveClassBuildItem.builder(
                "com.github.benmanes.caffeine.cache.SI", "com.github.benmanes.caffeine.cache.PW")
                .reason("Caffeine cache constructors used by Discord4J")
                .constructors().build());
    }

    private static boolean isGeneratedPayload(DotName type) {
        String name = type.withoutPackagePrefix();
        return type.toString().startsWith("discord4j.discordjson.json.")
                && name.startsWith("Immutable") && (!name.contains("$") || name.endsWith("$Json"));
    }

    /**
     * Waits for the gateway logout instead of firing it and letting the process exit underneath it.
     */
    @BuildStep
    ShutdownListenerBuildItem drainGatewayOnShutdown() {
        return new ShutdownListenerBuildItem(new DiscordShutdownListener());
    }

    @BuildStep
    RuntimeInitializedClassBuildItem initReactorResourcesAtRuntime() {
        return new RuntimeInitializedClassBuildItem("discord4j.common.ReactorResources");
    }

    @BuildStep
    RuntimeInitializedClassBuildItem initReactorNettyAtRuntime() {
        return new RuntimeInitializedClassBuildItem("reactor.netty.ReactorNetty");
    }

    @BuildStep
    RuntimeInitializedClassBuildItem initReactorNettyHttpClientAtRuntime() {
        return new RuntimeInitializedClassBuildItem("reactor.netty.http.client.HttpClient");
    }

    @BuildStep
    RuntimeInitializedClassBuildItem initReactorNettyHttpServerAtRuntime() {
        return new RuntimeInitializedClassBuildItem("reactor.netty.http.server.HttpServer");
    }

    @BuildStep
    RuntimeInitializedClassBuildItem initReactorNettyHttpClientSecureAtRuntime() {
        return new RuntimeInitializedClassBuildItem("reactor.netty.http.client.HttpClientSecure");
    }

    @BuildStep
    RuntimeInitializedClassBuildItem initReactorNettyTcpClientSecureAtRuntime() {
        return new RuntimeInitializedClassBuildItem("reactor.netty.tcp.TcpClientSecure");
    }

    @BuildStep
    RuntimeInitializedClassBuildItem initJdkSslClientContextAtRuntime() {
        return new RuntimeInitializedClassBuildItem("io.netty.handler.ssl.JdkSslClientContext");
    }

    @BuildStep
    RuntimeInitializedClassBuildItem initJdkSslContextAtRuntime() {
        return new RuntimeInitializedClassBuildItem("io.netty.handler.ssl.JdkSslContext");
    }
}
