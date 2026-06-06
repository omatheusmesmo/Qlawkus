package dev.omatheusmesmo.qlawkus.messaging.discord.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;

class DiscordProcessor {

    private static final String FEATURE = "qlawkus-messaging-discord";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    RuntimeInitializedClassBuildItem initReactorResourcesAtRuntime() {
        return new RuntimeInitializedClassBuildItem("discord4j.common.ReactorResources");
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
    RuntimeInitializedClassBuildItem initReactorNettyTcpSslProviderAtRuntime() {
        return new RuntimeInitializedClassBuildItem("reactor.netty.tcp.SslProvider");
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
