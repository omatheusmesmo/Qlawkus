CREATE TABLE journal (
  id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  date            DATE          NOT NULL UNIQUE,
  summary         TEXT          NOT NULL,
  message_count   INTEGER       NOT NULL,
  created_at      TIMESTAMP     DEFAULT now(),
  updated_at      TIMESTAMP     DEFAULT now()
);
