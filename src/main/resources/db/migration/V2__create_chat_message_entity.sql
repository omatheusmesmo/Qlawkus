CREATE TABLE chat_message_entity (
  id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  memory_id       VARCHAR(255)  NOT NULL,
  type            VARCHAR(24)   NOT NULL,
  content         TEXT          NOT NULL,
  created_at      TIMESTAMP     DEFAULT now()
);
