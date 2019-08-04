package com.coinhitchhiker.vbtrader.common;

import java.util.Map;
import java.util.TreeMap;

public class TickData {

    private String symbol;
    private long timestamp;
    private double currentPrice;
    private Map<Integer, Double> movingAverages = new TreeMap<>();

    public TickData(String symbol, long timestamp, double currentPrice) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.currentPrice = currentPrice;
    }

    public double getMAScore() {
        int i = 0;
        for(Map.Entry<Integer, Double> e : this.movingAverages.entrySet()) {
            if(e.getValue() < currentPrice) {
                i++;
            }
        }
        return i / this.movingAverages.size();
    }

    public void addMovingAverage(int nthMA, double price) {
        this.movingAverages.put(nthMA, price);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }
}
