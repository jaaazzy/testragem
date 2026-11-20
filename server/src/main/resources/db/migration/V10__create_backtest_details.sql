CREATE TABLE trade_fills (
    config_id UUID NOT NULL REFERENCES backtest_results(config_id),
    sequence_number INTEGER NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity NUMERIC(30,12) NOT NULL CHECK (quantity > 0),
    reference_price NUMERIC(30,12) NOT NULL CHECK (reference_price > 0),
    execution_price NUMERIC(30,12) NOT NULL CHECK (execution_price > 0),
    fee NUMERIC(24,8) NOT NULL CHECK (fee >= 0),
    slippage_cost NUMERIC(24,8) NOT NULL CHECK (slippage_cost >= 0),
    cash_after NUMERIC(24,8) NOT NULL CHECK (cash_after >= 0),
    PRIMARY KEY (config_id, sequence_number)
);
CREATE TABLE equity_points (
    config_id UUID NOT NULL REFERENCES backtest_results(config_id),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    cash NUMERIC(24,8) NOT NULL,
    position_quantity NUMERIC(30,12) NOT NULL,
    equity NUMERIC(24,8) NOT NULL,
    drawdown NUMERIC(24,12) NOT NULL,
    PRIMARY KEY (config_id, timestamp)
);
