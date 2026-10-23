-- Sizes the vector column to the embedding model's dimension. V4 fixed it at 1024, which forced every
-- provider to 1024; when NVIDIA retired its 1024 model and the successor only produced 2048 (#357),
-- no vector could be stored at all.
--
-- Repeatable, keyed on the dimension: Flyway checksums a repeatable migration after placeholder
-- replacement, so changing the model's dimension re-applies this at the next boot (pinned by
-- EmbeddingDimensionMigrationTest). At an unchanged dimension it keeps every vector. At a new one, a
-- vector from the old model lives in a different space and cannot be compared with the new ones, so
-- it is cleared and the text kept: EmbeddingBackfill re-embeds those rows after startup. That is also
-- why the column is now nullable.
ALTER TABLE embeddings ALTER COLUMN embedding DROP NOT NULL;

ALTER TABLE embeddings ALTER COLUMN embedding TYPE vector(${embedding_dimension})
  USING CASE
    WHEN vector_dims(embedding) = ${embedding_dimension} THEN embedding::vector(${embedding_dimension})
  END;
