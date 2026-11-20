package dev.omatheusmesmo.qlawkus.console;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * What the console's navigation shows, read by the shared layout. It holds no state of its own: a feature is
 * on exactly when an extension has registered its {@link ConsoleFeature} bean.
 */
@Named("consoleNavigation")
@ApplicationScoped
public class ConsoleNavigation {

    private static final String VOICE = "voice";

    private final Instance<ConsoleFeature> features;

    @Inject
    public ConsoleNavigation(Instance<ConsoleFeature> features) {
        this.features = features;
    }

    public boolean isVoiceEnabled() {
        return features.stream().anyMatch(feature -> VOICE.equals(feature.name()));
    }
}
