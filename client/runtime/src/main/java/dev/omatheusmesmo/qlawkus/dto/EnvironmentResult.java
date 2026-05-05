package dev.omatheusmesmo.qlawkus.dto;

import java.util.List;

public record EnvironmentResult(
        String os,
        String shell,
        String workspace,
        List<String> availableCommands
) {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OS: ").append(os).append("\n");
        sb.append("Shell: ").append(shell).append("\n");
        sb.append("Workspace: ").append(workspace).append("\n");
        sb.append("Available commands: ").append(availableCommands).append("\n");
        return sb.toString();
    }
}
