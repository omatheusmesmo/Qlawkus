CREATE TABLE IF NOT EXISTS embeddings (
  embedding_id    UUID          PRIMARY KEY,
  embedding       vector(768)   NOT NULL,
  text            TEXT          NULL,
  metadata        JSONB         NULL
);
