CREATE TABLE IF NOT EXISTS agentic_scope (
    agent_id VARCHAR(255) NOT NULL,
    memory_id VARCHAR(255) NOT NULL,
    scope_data TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_agentic_scope PRIMARY KEY (agent_id, memory_id)
);
