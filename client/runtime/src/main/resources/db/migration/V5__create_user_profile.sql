CREATE TABLE user_profile (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    profile TEXT,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

INSERT INTO user_profile (id, name, profile, created_at, updated_at)
VALUES (1, NULL, NULL, now(), now());
