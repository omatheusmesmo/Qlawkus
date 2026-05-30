CREATE TABLE googleoauth_state (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    memory_id VARCHAR(255),
    provider_id VARCHAR(255),
    chat_id VARCHAR(255),
    CONSTRAINT uk_googleoauth_state_token UNIQUE (token)
);
