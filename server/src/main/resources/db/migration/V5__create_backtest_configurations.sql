CREATE TABLE backtest_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID REFERENCES users(id),
    dataset_version_id UUID REFERENCES dataset_versions(id),
    strategy_version_id UUID REFERENCES strategy_versions(id),
    name VARCHAR(150),
    range_start TIMESTAMPTZ, 
    range_end TIMESTAMPTZ, 
    initial_capital NUMERIC(24, 8),
    fee_bps NUMERIC(12, 6),
    slippage_bps NUMERIC(12, 6),
    parameters JSONB,
    execution_policy JSONB,
    random_seed BIGINT, 
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
