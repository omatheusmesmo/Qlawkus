CREATE TABLE approval_request (
  id             VARCHAR(32)   PRIMARY KEY CHECK (id ~ '^[0-9a-f]{32}$'),
  tool_name      VARCHAR(255)  NOT NULL,
  arguments      TEXT          NOT NULL,
  binding_hash   VARCHAR(64)   NOT NULL,
  preview        TEXT          NOT NULL,
  memory_id      VARCHAR(255),
  provider_id    VARCHAR(255),
  chat_id        VARCHAR(255),
  status         VARCHAR(16)   NOT NULL,
  created_at     TIMESTAMPTZ   NOT NULL,
  expires_at     TIMESTAMPTZ   NOT NULL,
  decided_at     TIMESTAMPTZ,
  decided_by     VARCHAR(255),
  result_summary TEXT
);

CREATE INDEX approval_request_status_idx ON approval_request (status);
