package dev.omatheusmesmo.qlawkus.console.skillhub;

import dev.omatheusmesmo.qlawkus.console.ConsoleFeature;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Announces the install panel to the console, which then links it from the skills page.
 */
@ApplicationScoped
public class SkillHubConsoleFeature implements ConsoleFeature {

    @Override
    public String name() {
        return "skill-hub";
    }
}
