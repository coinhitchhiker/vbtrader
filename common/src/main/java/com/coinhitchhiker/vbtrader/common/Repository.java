package com.coinhitchhiker.vbtrader.common;

import java.util.List;

public interface Repository {

    List<TradingWindow> getLastNTradingWindow(int n, int tradingWindowSizeInMinutes, long curTimestamp);

    TradingWindow getCurrentTradingWindow(long curTimestamp);

}
