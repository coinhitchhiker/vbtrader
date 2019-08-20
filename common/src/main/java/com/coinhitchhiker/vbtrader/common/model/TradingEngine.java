package com.coinhitchhiker.vbtrader.common.model;

import com.coinhitchhiker.vbtrader.common.strategy.VolatilityBreakoutRules;

public interface TradingEngine {

    TradeResult run(double curPrice, long curTimeStamp);

    void setVBRules(VolatilityBreakoutRules vbRules);
}
