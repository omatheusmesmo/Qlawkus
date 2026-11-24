-- Sizes the vector column to the embedding model's dimension. V4 fixed it at 1024, which forced every
-- provider to 1024; when NVIDIA retired its 1024 model and the successor only produced 2048 (#357),
-- no vector could be stored at all.
--
-- Repeatable, keyed on the dimension and on the model name: Flyway checksums a repeatable migration
-- after placeholder replacement, so changing either re-applies this at the next boot (pinned by
-- EmbeddingDimensionMigrationTest). A vector from another model lives in a different space and cannot
-- be compared with the new ones, even at the same dimension (#527), so when the recorded model differs
-- from the current one, or none was ever recorded, the vectors are cleared and the text kept:
-- EmbeddingBackfill re-embeds those rows after startup. That is also why the column is nullable.
CREATE TABLE IF NOT EXISTS embedding_model_marker (
  id BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
  model TEXT NOT NULL
);

ALTER TABLE embeddings ALTER COLUMN embedding DROP NOT NULL;

UPDATE embeddings SET embedding = NULL
  WHERE (SELECT model FROM embedding_model_marker) IS DISTINCT FROM $qlawkus$${embedding_model}$qlawkus$;

ALTER TABLE embeddings ALTER COLUMN embedding TYPE vector(${embedding_dimension})
  USING CASE
    WHEN vector_dims(embedding) = ${embedding_dimension} THEN embedding::vector(${embedding_dimension})
  END;

INSERT INTO embedding_model_marker (id, model) VALUES (TRUE, $qlawkus$${embedding_model}$qlawkus$)
  ON CONFLICT (id) DO UPDATE SET model = EXCLUDED.model;
