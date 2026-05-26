ALTER TABLE agentic_scope ALTER COLUMN scope_data TYPE TEXT USING encode(scope_data, 'escape');
