package dev.omatheusmesmo.qlawkus.messaging.telegram.deployment;

import dev.omatheusmesmo.qlawkus.messaging.telegram.TelegramShutdownListener;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownListenerBuildItem;

class TelegramProcessor {

    private static final String FEATURE = "qlawkus-messaging-telegram";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Drains the long-poll thread through the platform's shutdown phases. The poll loop is a plain
     * daemon thread, so nothing else would wait for it: the JVM exits and the thread dies wherever
     * it happens to be, including partway through dispatching an update.
     */
    @BuildStep
    ShutdownListenerBuildItem drainPollLoopOnShutdown() {
        return new ShutdownListenerBuildItem(new TelegramShutdownListener());
    }
}
