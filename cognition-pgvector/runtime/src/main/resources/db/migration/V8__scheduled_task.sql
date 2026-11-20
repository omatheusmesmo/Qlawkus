CREATE TABLE scheduled_task (
  id           VARCHAR(64)   PRIMARY KEY,
  memory_id    VARCHAR(255)  NOT NULL,
  provider_id  VARCHAR(255)  NOT NULL,
  chat_id      VARCHAR(255)  NOT NULL,
  task         TEXT          NOT NULL,
  cron         VARCHAR(255)  NOT NULL,
  one_shot     BOOLEAN       NOT NULL,
  status       VARCHAR(16)   NOT NULL,
  created_at   TIMESTAMP     NOT NULL
);
