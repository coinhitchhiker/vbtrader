package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.model.OrderBookCache;
import com.coinhitchhiker.vbtrader.common.model.event.TradeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

public class SimulatorOrderBookCache implements OrderBookCache {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    private final String EXCHANGE;
    private final String SYMBOL;

    private double bestBid = 0;
    private double bestAsk = 0;

    public SimulatorOrderBookCache(String EXCHANGE, String SYMBOL) {
        this.EXCHANGE = EXCHANGE;
        this.SYMBOL = SYMBOL;
    }

    public void setCurPrice(double curPrice) {
        this.bestAsk = curPrice;
        this.bestBid = curPrice;
    }

    public double getBestAsk() {
        return bestAsk;
    }

    public double getBestBid() {
        return bestBid;
    }

    public double getMidPrice() {
        return (bestAsk + bestBid)/2;
    }

    public void onTradeEvent(double price, long tradeTime, double amount) {
        TradeEvent e = new TradeEvent(EXCHANGE, SYMBOL, price, tradeTime , amount, null, null, null);
        this.applicationEventPublisher.publishEvent(e);
    }

}
