package com.coinhitchhiker.vbtrader.common.model;

public interface TradingEngine {

    void init(long curTimestamp);

    TradeResult trade(double curPrice, long curTimestamp);

    double buySignalStrength(double curPrice, long curTimestamp);

    boolean sellSignal(double curPrice, long curTimestamp);

    void onTradeEvent(TradeEvent e);

    double getTrailingStopPrice();

    double getStopLossPrice();

}
