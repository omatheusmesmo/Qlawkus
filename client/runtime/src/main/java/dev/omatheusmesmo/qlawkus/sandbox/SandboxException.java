package dev.omatheusmesmo.qlawkus.sandbox;

/**
 * Thrown when an ephemeral sandbox resource (directory, file, etc.) cannot be
 * provisioned. Wrapping is intentional so callers can fail fast at the entry point
 * of a sandboxed operation without dealing with low-level I/O exceptions.
 */
public class SandboxException extends RuntimeException {

    public SandboxException(String message, Throwable cause) {
        super(message, cause);
    }
}
