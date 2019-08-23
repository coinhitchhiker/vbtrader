package com.coinhitchhiker.vbtrader.common.model;

import com.coinhitchhiker.vbtrader.common.strategy.PVTOBV;
import com.coinhitchhiker.vbtrader.common.strategy.Strategy;
import com.coinhitchhiker.vbtrader.common.strategy.VolatilityBreakout;

public interface TradingEngine {

    TradeResult run(double curPrice, long curTimeStamp);

    default void setVBRules(VolatilityBreakout vbRules) {
        return;
    }

    default void setPVTOBV(PVTOBV pvtobv) {
        return;
    }

    default void setStrategy(Strategy strategy) {
        return;
    }
}
