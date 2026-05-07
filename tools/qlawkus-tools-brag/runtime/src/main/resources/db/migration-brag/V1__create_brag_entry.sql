CREATE TABLE brag_entry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date DATE NOT NULL,
    achievement TEXT NOT NULL,
    impact TEXT,
    repo VARCHAR(128),
    deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_brag_entry_date ON brag_entry (date);
CREATE INDEX idx_brag_entry_deleted ON brag_entry (deleted);
