CREATE TABLE dataset_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol VARCHAR(30), 
    timeframe VARCHAR(20),
    source TEXT, 
    fingerprint VARCHAR(64) NOT NULL UNIQUE,
    coverage_start TIMESTAMPTZ,
    coverage_end TIMESTAMPTZ,
    artifact_uri TEXT,
    quality_summary JSONB,
    created_at TIMESTAMPTZ
);
