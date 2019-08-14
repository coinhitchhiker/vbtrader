package com.coinhitchhiker.vbtrader.common;

import java.util.Map;

public interface OrderBookCache {

    double getBestAsk();

    double getBestBid();

    double getMidPrice();

    void onTradeEvent(Map<String, Object> trade);

}
