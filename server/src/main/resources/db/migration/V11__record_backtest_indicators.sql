-- Store the strategy's actual close-time observations, including gaps during warm-up.
-- Older runs remain readable and explicitly have no recorded indicators.
ALTER TABLE backtest_results
    ADD COLUMN indicator_points JSONB NOT NULL DEFAULT '[]'::jsonb
    CHECK (jsonb_typeof(indicator_points) = 'array');
