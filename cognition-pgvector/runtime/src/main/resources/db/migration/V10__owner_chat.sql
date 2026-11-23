CREATE TABLE owner_chat (
  provider_id VARCHAR(32) PRIMARY KEY CHECK (provider_id ~ '^[a-z][a-z0-9-]{0,31}$'),
  chat_id VARCHAR(200) NOT NULL CHECK (length(btrim(chat_id)) > 0),
  last_seen TIMESTAMPTZ NOT NULL
);
