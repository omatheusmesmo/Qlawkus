-- Facts are split into embedding-sized segments (one fact -> N vector rows) so no single embedding
-- exceeds the model's token limit. Every segment of a fact shares a parent factHash carried in
-- metadata; content_hash is generated from it, so existsByContentHash deduplicates at fact
-- granularity. It is NOT unique because N segments of one fact legitimately share the same hash.
CREATE TABLE IF NOT EXISTS embeddings (
  embedding_id UUID PRIMARY KEY,
  embedding vector(1024) NOT NULL,
  text TEXT NOT NULL,
  metadata JSONB NULL,
  content_hash TEXT GENERATED ALWAYS AS (metadata->>'factHash') STORED
);

CREATE INDEX IF NOT EXISTS idx_embeddings_content_hash ON embeddings (content_hash);
