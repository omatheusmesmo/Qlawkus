package dev.omatheusmesmo.qlawkus.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for writing a single {@code RUN_TIME} property into the runtime-toggle override file.
 * The {@code property} is the dotted configuration name (for example
 * {@code qlawkus.skill-hub.approval-mode}); the change takes effect on the next restart, since config
 * sources are read at startup.
 */
public record SetRuntimeToggleRequest(
    @NotBlank String property,
    @NotBlank String value
) {}
