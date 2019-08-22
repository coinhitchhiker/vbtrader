package com.coinhitchhiker.vbtrader.common.model;

import com.coinhitchhiker.vbtrader.common.strategy.PVTOBV;
import com.coinhitchhiker.vbtrader.common.strategy.VolatilityBreakout;

public interface TradingEngine {

    TradeResult run(double curPrice, long curTimeStamp);

    void setVBRules(VolatilityBreakout vbRules);

    void setPVTOBV(PVTOBV pvtobv);
}
