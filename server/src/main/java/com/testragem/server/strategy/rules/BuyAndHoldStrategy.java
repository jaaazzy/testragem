package com.testragem.server.strategy.rules;
import com.testragem.server.dataset.model.Candle;

public final class BuyAndHoldStrategy implements TradingStrategy {
    @Override
    public Signal onStart(){
        return Signal.BUY;
    }

    @Override
    public Signal onCandleClose(Candle candle){
        return Signal.HOLD;
    }
}
