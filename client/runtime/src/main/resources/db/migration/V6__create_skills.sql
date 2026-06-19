CREATE TABLE skill (
    name VARCHAR(255) PRIMARY KEY,
    description TEXT,
    body TEXT,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
