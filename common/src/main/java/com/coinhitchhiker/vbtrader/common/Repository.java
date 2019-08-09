package com.coinhitchhiker.vbtrader.common;

import java.util.List;

public interface Repository {

    List<TradingWindow> getLastNTradingWindow(int n, long curTimestamp);

    TradingWindow getCurrentTradingWindow(long curTimestamp);

    void recordOrder(TradingWindow tradingWindow, OrderInfo orderInfo);

}
