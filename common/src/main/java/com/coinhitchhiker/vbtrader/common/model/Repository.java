package com.coinhitchhiker.vbtrader.common.model;

import java.util.List;

public interface Repository {

    default void refreshTradingWindows() {
        return;
    }

    default void logCompleteTradingWindow(TradingWindow tradingWindow) {
        return;
    }

    List<TradingWindow> getLastNTradingWindow(int n, long curTimestamp);

    default List<Candle> getLastNCandle(int n, long curTimestamp) {
        return null;
    }

    TradingWindow getCurrentTradingWindow(long curTimestamp);

    Candle getCurrentCandle(long curTimestamp);

}
