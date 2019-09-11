package com.coinhitchhiker.vbtrader.simulator;

import com.coinhitchhiker.vbtrader.common.model.OrderBookCache;

public class SimulatorOrderBookCache implements OrderBookCache {

    private double bestBid = 0;
    private double bestAsk = 0;

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

}
