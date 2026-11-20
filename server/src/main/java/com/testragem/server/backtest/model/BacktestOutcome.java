package com.testragem.server.backtest.model;

import java.math.BigDecimal;
import java.util.List;

/** Completed accounting and recorded chart data. Return and drawdown values are fractions. */
public record BacktestOutcome(BigDecimal finalEquity, BigDecimal totalReturn, BigDecimal maxDrawdown,
        BigDecimal totalFees, BigDecimal totalSlippage, String engineVersion,
        List<TradeFill> fills, List<EquityPoint> equityPoints, List<IndicatorPoint> indicatorPoints) {
    public BacktestOutcome {
        fills = List.copyOf(fills);
        equityPoints = List.copyOf(equityPoints);
        indicatorPoints = List.copyOf(indicatorPoints);
    }

    /** Compatibility for runs/fixtures that do not record indicators. */
    public BacktestOutcome(BigDecimal finalEquity, BigDecimal totalReturn, BigDecimal maxDrawdown,
            BigDecimal totalFees, BigDecimal totalSlippage, String engineVersion,
            List<TradeFill> fills, List<EquityPoint> equityPoints) {
        this(finalEquity, totalReturn, maxDrawdown, totalFees, totalSlippage, engineVersion,
                fills, equityPoints, List.of());
    }
}
