package dev.omatheusmesmo.qlawkus.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
  @NotBlank String message
) {}
