package com.coinhitchhiker.vbtrader.common.model;

import com.coinhitchhiker.vbtrader.common.strategy.pvtobv.PVTOBV;

public interface TradingEngine {

    TradeResult run(double curPrice, long curTimeStamp);

    boolean buySignal(double curPrice, long curTimestamp);

    boolean sellSignal(double curPrice, long curTimestamp);

}
