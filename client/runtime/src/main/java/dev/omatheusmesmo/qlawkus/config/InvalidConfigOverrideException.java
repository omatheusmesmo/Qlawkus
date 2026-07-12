package dev.omatheusmesmo.qlawkus.config;

/**
 * Thrown when a staged config-overrides document cannot be parsed, or names a property that is
 * undocumented, {@code RUN_TIME}-phase (belongs in the runtime-toggle tier instead), or a known secret
 * (belongs in the keystore instead). The message is safe to surface to the operator - it never echoes
 * a secret value, since secret properties are rejected before any value is read back.
 */
public class InvalidConfigOverrideException extends RuntimeException {

    public InvalidConfigOverrideException(String message) {
        super(message);
    }
}
