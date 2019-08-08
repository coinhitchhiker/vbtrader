package com.coinhitchhiker.vbtrader.simulator;

import java.io.Serializable;

public class Candle implements Serializable {
    //symbol interval openTime closeTime openPrice highPrice lowPrice closePrice volume
    private final String symbol;
    private final String interval;
    private final long openTime;    // milli
    private final long closeTime;
    private final double openPrice;
    private final double highPrice;
    private final double lowPrice;
    private final double closePrice;
    private final double volume;

    public Candle(String symbol, String interval, long openTime, long closeTime, double openPrice, double highPrice, double lowPrice, double closePrice, double volume) {
        this.symbol = symbol;
        this.interval = interval;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getInterval() {
        return interval;
    }

    public long getOpenTime() {
        return openTime;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public double getHighPrice() {
        return highPrice;
    }

    public double getLowPrice() {
        return lowPrice;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public double getVolume() {
        return volume;
    }
}
