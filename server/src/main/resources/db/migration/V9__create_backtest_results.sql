-- Synchronous MVP: one accepted result per immutable configuration.
-- The former, invalid job-based draft is preserved in docs/migration-drafts.
CREATE TABLE backtest_results (
    config_id UUID PRIMARY KEY REFERENCES backtest_configurations(id),
    final_equity NUMERIC(24,8) NOT NULL,
    total_return NUMERIC(24,12) NOT NULL,
    max_drawdown NUMERIC(24,12) NOT NULL,
    total_fees NUMERIC(24,8) NOT NULL,
    total_slippage NUMERIC(24,8) NOT NULL,
    engine_version VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status = 'SUCCEEDED'),
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    configuration_snapshot JSONB NOT NULL,
    dataset_fingerprint VARCHAR(64) NOT NULL
);
CREATE INDEX ix_backtest_configurations_owner_history
    ON backtest_configurations(owner_id, created_at DESC, id DESC);
