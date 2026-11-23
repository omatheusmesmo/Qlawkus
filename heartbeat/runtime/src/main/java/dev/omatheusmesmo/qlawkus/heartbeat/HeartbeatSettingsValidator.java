package dev.omatheusmesmo.qlawkus.heartbeat;

import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Validator;

import java.util.regex.Pattern;

@ApplicationScoped
class HeartbeatSettingsValidator {

    private static final Pattern PRIVATE_KEY = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----");
    private final Validator validator;
    private final SecretRedactor redactor;

    HeartbeatSettingsValidator(Validator validator, SecretRedactor redactor) {
        this.validator = validator;
        this.redactor = redactor;
    }

    void validate(HeartbeatSettings settings) {
        if (!validator.validate(settings).isEmpty()) {
            throw new IllegalArgumentException(
                    "Choose an interval from 1 to 1440 minutes, instructions up to 1000 characters and a known provider.");
        }
        String prompt = settings.prompt();
        if (settings.enabled() && prompt.isBlank()) {
            throw new IllegalArgumentException("Enabled heartbeat requires owner instructions.");
        }
        if (PRIVATE_KEY.matcher(prompt).find() || !prompt.equals(redactor.redact(prompt))) {
            throw new IllegalArgumentException("Remove credentials from heartbeat instructions.");
        }
    }
}
