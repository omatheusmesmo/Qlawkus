CREATE TABLE IF NOT EXISTS googleoauth_state (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_googleoauth_state_token UNIQUE (token)
);
