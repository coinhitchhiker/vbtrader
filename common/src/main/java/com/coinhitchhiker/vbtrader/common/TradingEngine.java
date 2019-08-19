package com.coinhitchhiker.vbtrader.common;

public interface TradingEngine {

    TradeResult run(double curPrice, long curTimeStamp);
}
