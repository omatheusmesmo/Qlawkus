package dev.omatheusmesmo.qlawkus.heartbeat;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

@RegisterForReflection
public record HeartbeatSettings(boolean enabled,
        @Min(1) @Max(HeartbeatSettings.MAX_INTERVAL_MINUTES) int intervalMinutes,
        @Size(max = HeartbeatSettings.MAX_PROMPT_LENGTH) String prompt,
        @Pattern(regexp = "[a-z][a-z0-9-]{0,31}") String provider) {

    static final int MAX_PROMPT_LENGTH = 1000;
    static final int MAX_INTERVAL_MINUTES = 1440;

    public HeartbeatSettings {
        prompt = prompt == null ? "" : prompt.strip();
        provider = provider == null || provider.isBlank() ? null : provider.strip().toLowerCase(Locale.ROOT);
    }
}
