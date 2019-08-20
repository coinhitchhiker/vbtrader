package com.coinhitchhiker.vbtrader.common.model;

public interface TradingEngine {

    TradeResult run(double curPrice, long curTimeStamp);
}
