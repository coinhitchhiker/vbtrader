package com.coinhitchhiker.vbtrader.common.model;

public interface TradingEngine {

    TradeResult trade(double curPrice, long curTimestamp);

    double buySignalStrength(double curPrice, long curTimestamp);

    boolean sellSignal(double curPrice, long curTimestamp);

    default void onTradeEvent(TradeEvent e) {
        return;
    };

    double getTrailingStopPrice();

    double getStopLossPrice();

}
