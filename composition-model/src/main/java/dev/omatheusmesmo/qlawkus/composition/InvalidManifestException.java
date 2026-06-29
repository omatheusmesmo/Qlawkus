package dev.omatheusmesmo.qlawkus.composition;

/**
 * Thrown when {@code agent.yml} cannot be parsed or violates the schema. Carries a message that is
 * safe to surface to the operator (it never echoes secret values - the manifest holds none).
 */
public class InvalidManifestException extends RuntimeException {

    public InvalidManifestException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidManifestException(String message) {
        super(message);
    }
}
