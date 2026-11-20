-- Personal research metadata is separate from immutable simulation results.
CREATE TABLE research_presets (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_research_presets_owner ON research_presets(owner_id, created_at DESC);
CREATE TABLE research_preset_versions (
    preset_id UUID NOT NULL REFERENCES research_presets(id),
    version INTEGER NOT NULL CHECK (version > 0),
    name VARCHAR(100) NOT NULL,
    configuration JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (preset_id, version)
);
CREATE TABLE research_preferences (
    owner_id UUID PRIMARY KEY REFERENCES users(id),
    settings JSONB NOT NULL
);
CREATE TABLE research_annotations (
    config_id UUID PRIMARY KEY REFERENCES backtest_results(config_id),
    title VARCHAR(100) NOT NULL DEFAULT '',
    notes VARCHAR(4000) NOT NULL DEFAULT '',
    tags JSONB NOT NULL DEFAULT '[]'::jsonb
);
