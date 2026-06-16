package dev.omatheusmesmo.qlawkus.model;

import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Qualifies the primary embedding model built by {@link PrimaryEmbeddingModelProducer}.
 *
 * <p>The primary embedding is produced through the langchain4j upstream {@code OpenAiEmbeddingModel}
 * (not the quarkus-langchain4j openai extension) because it needs {@code dimensions} and
 * {@code customParameters} (NVIDIA {@code input_type}) that the extension config cannot express.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
public @interface PrimaryEmbedding {
}
