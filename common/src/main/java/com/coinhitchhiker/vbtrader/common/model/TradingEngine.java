package com.coinhitchhiker.vbtrader.common.model;

public interface TradingEngine {

    TradeResult trade(double curPrice, long curTimestamp, double curVol);

    default double buySignalStrength(double curPrice, long curTimestamp) {
        return 0.0;
    }

    default double sellSignalStrength(double curPrice, long curTimestamp) {
        return 0.0;
    }

    default void onTradeEvent(TradeEvent e) {
        return;
    };

    double getTrailingStopPrice();

    double getStopLossPrice();

}
