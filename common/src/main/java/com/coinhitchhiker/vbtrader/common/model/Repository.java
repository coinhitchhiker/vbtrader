package com.coinhitchhiker.vbtrader.common.model;

import java.util.List;

public interface Repository {

    default void logCompleteTradingWindow(TradingWindow tradingWindow) {
        return;
    }

    default List<Candle> getLastNCandle(int n, long curTimestamp) {
        return null;
    }

    Candle getCurrentCandle(long curTimestamp);

    List<Candle> getCandles(String symbol, long startTime, long endTime);

}
