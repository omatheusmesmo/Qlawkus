package dev.omatheusmesmo.qlawkus.messaging.discord.deployment;

import dev.omatheusmesmo.qlawkus.messaging.discord.DiscordShutdownListener;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownListenerBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;

class DiscordProcessor {

    private static final String FEATURE = "qlawkus-messaging-discord";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
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
