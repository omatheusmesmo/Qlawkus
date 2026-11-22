CREATE TABLE debug_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    kind VARCHAR(48) NOT NULL,
    run_id UUID,
    invocation_id UUID,
    trace_id VARCHAR(32),
    task_id VARCHAR(64),
    conversation_id VARCHAR(200),
    subject VARCHAR(200),
    call_id VARCHAR(200),
    outcome VARCHAR(64),
    detail JSONB NOT NULL,
    excerpt TEXT
);

CREATE INDEX debug_event_time ON debug_event (occurred_at DESC, id DESC);
CREATE INDEX debug_event_run ON debug_event (run_id, id) WHERE run_id IS NOT NULL;
CREATE INDEX debug_event_invocation ON debug_event (invocation_id, id) WHERE invocation_id IS NOT NULL;
CREATE INDEX debug_event_task ON debug_event (task_id, id) WHERE task_id IS NOT NULL;
CREATE INDEX debug_event_trace ON debug_event (trace_id, id) WHERE trace_id IS NOT NULL;
CREATE INDEX debug_event_excerpt_time ON debug_event (occurred_at) WHERE excerpt IS NOT NULL;
