package dev.omatheusmesmo.qlawkus.dto;

import dev.omatheusmesmo.qlawkus.agent.ToolProgress;
import jakarta.validation.constraints.NotBlank;

/**
 * A chat message. {@code toolProgress} is optional: it picks how much tool activity the streamed
 * reply carries ({@code off}, {@code status}, {@code preview} or {@code verbose}), and defaults to
 * {@code preview}.
 */
public record ChatRequest(
  @NotBlank String message,
  ToolProgress toolProgress
) {

  public ChatRequest(String message) {
    this(message, null);
  }

  public ToolProgress effectiveToolProgress() {
    return toolProgress == null ? ToolProgress.DEFAULT : toolProgress;
  }
}
