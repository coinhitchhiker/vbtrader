package com.coinhitchhiker.vbtrader.common;

import java.util.List;

public interface Repository {

    default void refreshTradingWindows() {
        return;
    }

    default void logCompleteTradingWindow(TradingWindow tradingWindow) {
        return;
    }

    List<TradingWindow> getLastNTradingWindow(int n, long curTimestamp);

    TradingWindow getCurrentTradingWindow(long curTimestamp);

    default double getPVT(long currentTimestamp) {
        return 0;
    }

    default double getOBV(long curTimestamp) {
        return 0;
    }

}
