package dev.omatheusmesmo.qlawkus.devui;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import java.nio.file.Path;

/**
 * Carries the build-time-resolved location of the application's {@code agent.yml} into the running
 * dev-mode container. The path is only knowable during augmentation, where the workspace module is
 * visible, so it is recorded rather than rediscovered at runtime.
 */
@Recorder
public class DevUiRecorder {

    public RuntimeValue<DevUiCompositionSource> compositionSource(String manifestPath) {
        return new RuntimeValue<>(new DevUiCompositionSource(
                manifestPath == null ? null : Path.of(manifestPath)));
    }
}
