CREATE TABLE strategy_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    strategy_key VARCHAR(60) NOT NULL, 
    version VARCHAR(40) NOT NULL, 
    parameter_schema JSONB,
    code_fingerprint VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_strategy_versions_key_version
        UNIQUE (strategy_key, version)
);
