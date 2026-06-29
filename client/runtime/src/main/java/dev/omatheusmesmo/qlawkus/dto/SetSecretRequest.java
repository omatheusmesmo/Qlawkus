package dev.omatheusmesmo.qlawkus.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for writing a secret into the keystore. The {@code alias} is the configuration
 * property the secret supplies (for example {@code quarkus.langchain4j.openai."primary".api-key});
 * the {@code value} is the secret itself and is never echoed back or logged.
 */
public record SetSecretRequest(
    @NotBlank String alias,
    @NotBlank String value
) {}
