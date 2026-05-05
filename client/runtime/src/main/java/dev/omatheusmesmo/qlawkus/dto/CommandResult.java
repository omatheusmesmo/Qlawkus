package dev.omatheusmesmo.qlawkus.dto;

public record CommandResult(
        String stdout,
        String stderr,
        int exitCode,
        long durationMs,
        boolean truncated
) {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Exit code: ").append(exitCode).append("\n");
        sb.append("Duration: ").append(durationMs).append("ms\n");
        if (stdout != null && !stdout.isBlank()) {
            sb.append("stdout:\n").append(stdout).append("\n");
        }
        if (stderr != null && !stderr.isBlank()) {
            sb.append("stderr:\n").append(stderr).append("\n");
        }
        if (truncated) {
            sb.append("[Output truncated — exceeded 1MB limit]\n");
        }
        return sb.toString();
    }
}
