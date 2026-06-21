CREATE TABLE IF NOT EXISTS embeddings (
  embedding_id UUID PRIMARY KEY,
  embedding vector(1024) NOT NULL,
  text TEXT NOT NULL,
  metadata JSONB NULL,
  content_hash TEXT GENERATED ALWAYS AS (md5(text)) STORED UNIQUE
);
