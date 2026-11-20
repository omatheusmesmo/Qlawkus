package dev.omatheusmesmo.qlawkus.console.voice.deployment;

import dev.omatheusmesmo.qlawkus.console.voice.TtsRouterSpeech;
import dev.omatheusmesmo.qlawkus.console.voice.VoiceConsoleFeature;
import dev.omatheusmesmo.qlawkus.console.voice.VoiceConsoleResource;
import dev.omatheusmesmo.qlawkus.console.voice.VoiceOriginCheck;
import dev.omatheusmesmo.qlawkus.console.voice.VoiceSessionLimiter;
import dev.omatheusmesmo.qlawkus.console.voice.VoiceSessions;
import dev.omatheusmesmo.qlawkus.console.voice.VoiceSocket;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Build steps for the optional {@code qlawkus-console-voice} extension. Its beans live in a runtime jar
 * that is not a CDI bean archive on its own, so they are registered here, the same way the console
 * registers its own.
 */
class ConsoleVoiceProcessor {

    private static final String FEATURE = "qlawkus-console-voice";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem voiceBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(VoiceSocket.class, VoiceSessions.class, VoiceSessionLimiter.class,
                        VoiceOriginCheck.class, VoiceConsoleResource.class, VoiceConsoleFeature.class,
                        TtsRouterSpeech.class)
                .setUnremovable()
                .build();
    }
}
