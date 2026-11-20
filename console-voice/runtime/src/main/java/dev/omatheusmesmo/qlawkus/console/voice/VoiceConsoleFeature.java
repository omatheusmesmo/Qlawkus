package dev.omatheusmesmo.qlawkus.console.voice;

import dev.omatheusmesmo.qlawkus.console.ConsoleFeature;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Announces the live voice page to the console, which then links it in the navigation.
 */
@ApplicationScoped
public class VoiceConsoleFeature implements ConsoleFeature {

    @Override
    public String name() {
        return "voice";
    }
}
