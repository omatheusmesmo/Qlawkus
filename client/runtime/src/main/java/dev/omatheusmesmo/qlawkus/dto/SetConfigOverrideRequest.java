package dev.omatheusmesmo.qlawkus.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for staging a single {@code BUILD_TIME}/{@code BUILD_AND_RUN_TIME_FIXED} property
 * override. The {@code property} is the dotted configuration name; the change is staged only, and
 * takes effect on the next rebuild.
 */
public record SetConfigOverrideRequest(
    @NotBlank String property,
    @NotBlank String value
) {}
