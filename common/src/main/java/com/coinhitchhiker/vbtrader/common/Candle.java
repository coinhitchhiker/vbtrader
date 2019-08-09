package com.coinhitchhiker.vbtrader.common;

import java.io.Serializable;
import java.util.List;

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

    public static Candle fromBinanceCandle(String symbol, String interval, List<Object> data) {
        long openTime = ((Double) data.get(0)).longValue();
        double open = Double.valueOf((String) data.get(1));
        double high = Double.valueOf((String) data.get(2));
        double low = Double.valueOf((String) data.get(3));
        double close = Double.valueOf((String) data.get(4));
        double volume = Double.valueOf((String) data.get(5));
        long closeTime = ((Double) data.get(6)).longValue();
        return new Candle(symbol, interval, openTime, closeTime, open, high, low, close, volume);
    }

    //-----------------------------------------------------

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
