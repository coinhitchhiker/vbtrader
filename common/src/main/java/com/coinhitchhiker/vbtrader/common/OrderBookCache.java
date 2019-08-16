package com.coinhitchhiker.vbtrader.common;

import java.util.Map;

public interface OrderBookCache {

    default double getBestAsk() {
        return 0.0D;
    }

    default double getBestBid() {
        return 0.0D;
    }

    default double getMidPrice() {
        return 0.0D;
    }

}
