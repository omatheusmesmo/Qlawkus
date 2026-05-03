CREATE TABLE google_credential (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider VARCHAR(64) NOT NULL DEFAULT 'google',
    encrypted_refresh_token TEXT NOT NULL,
    access_token TEXT,
    token_type VARCHAR(32),
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_google_credential_provider UNIQUE (provider)
);
