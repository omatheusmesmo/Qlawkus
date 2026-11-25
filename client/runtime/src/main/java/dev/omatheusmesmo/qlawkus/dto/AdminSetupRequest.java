package dev.omatheusmesmo.qlawkus.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body of the first-run admin setup: the one-time {@code token} printed in the agent log and the
 * {@code password} to set. The password is never echoed back, logged or part of {@link #toString()}.
 */
public record AdminSetupRequest(
    @NotBlank String token,
    @NotBlank String password
) {

  @Override
  public String toString() {
    return "AdminSetupRequest[token=<redacted>, password=<redacted>]";
  }
}
