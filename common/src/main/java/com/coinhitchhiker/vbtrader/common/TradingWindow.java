package com.coinhitchhiker.vbtrader.common;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.List;

public class TradingWindow {

    private final String symbol;
    private final long startTimeStamp;    // unix epoch in millis
    private final long endTimeStamp;

    private double openPrice;
    private double highPrice;
    private double closePrice;
    private double lowPrice;
    private double volume;

    private long curTimeStamp;
    private OrderInfo buyOrder;

    public TradingWindow(String symbol, long startTimeStamp, long endTimeStamp, double openPrice, double highPrice, double closePrice, double lowPrice, double volume) {
        this.symbol = symbol;
        this.startTimeStamp = startTimeStamp;
        this.endTimeStamp = endTimeStamp;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.closePrice = closePrice;
        this.lowPrice = lowPrice;
        this.volume = volume;
    }

    public TradingWindow(String symbol, long startTimeStamp, long endTimeStamp, double openPrice) {
        this.symbol = symbol;
        this.startTimeStamp = startTimeStamp;
        this.endTimeStamp = endTimeStamp;
        this.openPrice = openPrice;
        this.lowPrice = openPrice;
        this.highPrice = openPrice;
        this.closePrice = openPrice;
    }

    public double getNoiseRatio() {
        return  1 - Math.abs(openPrice - closePrice) / (highPrice - lowPrice);
    }

    public boolean isBetween(long timestamp) {
        return startTimeStamp <= timestamp && timestamp <= endTimeStamp;
    }

    public double getRange() {
        return highPrice - lowPrice;
    }

    public boolean isBuySignal(double curPrice, double k, TradingWindow prevTradingWindow) {
        if(highPrice == 0 || lowPrice == 0) return false;

        return  curPrice > openPrice + k * prevTradingWindow.getRange();
    }

    public void updateWindowData(TradeEvent e) {

        if(this.highPrice < e.getPrice()) this.highPrice = e.getPrice();
        if(this.lowPrice > e.getPrice()) this.lowPrice = e.getPrice();
        this.closePrice = e.getPrice();

        this.volume += e.getAmount();
        this.curTimeStamp = e.getTradeTime();

    }

    public void setBuyOrder(OrderInfo orderInfo) {
        this.buyOrder = orderInfo;
    }

    public OrderInfo getBuyOrder() {
        return this.buyOrder;
    }

    public static TradingWindow of(List<Candle> candles) {
        int tempListSize = candles.size();

        String symbol = candles.get(0).getSymbol();
        long openTime = candles.get(0).getOpenTime();
        long closeTime = candles.get(tempListSize-1).getCloseTime();
        double openPrice = candles.get(0).getOpenPrice();
        double highPrice = candles.stream().mapToDouble(Candle::getHighPrice).max().getAsDouble();
        double lowPrice = candles.stream().mapToDouble(Candle::getLowPrice).min().getAsDouble();
        double closePrice = candles.get(tempListSize-1).getClosePrice();
        double volume = candles.stream().mapToDouble(Candle::getVolume).sum();

        return new TradingWindow(symbol, openTime, closeTime, openPrice, highPrice, closePrice, lowPrice, volume);
    }

    @Override
    public String toString() {
        return "TradingWindow{" +
                "symbol='" + symbol + '\'' +
                ", curTimestamp=" + new DateTime(curTimeStamp).withZone(DateTimeZone.UTC).toString() +
                ", start=" + new DateTime(startTimeStamp).withZone(DateTimeZone.UTC).toString() +
                ", end=" + new DateTime(endTimeStamp).withZone(DateTimeZone.UTC).toString() +
                ", openPrice=" + openPrice +
                ", highPrice=" + highPrice +
                ", closePrice=" + closePrice +
                ", lowPrice=" + lowPrice +
                ", volume=" + volume +
                '}';
    }

    //-----------------------------------------------------------------------------------

    public String getSymbol() {
        return symbol;
    }

    public long getStartTimeStamp() {
        return startTimeStamp;
    }

    public long getEndTimeStamp() {
        return endTimeStamp;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(double openPrice) {
        this.openPrice = openPrice;
    }

    public double getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(double highPrice) {
        this.highPrice = highPrice;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(double closePrice) {
        this.closePrice = closePrice;
    }

    public double getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(double lowPrice) {
        this.lowPrice = lowPrice;
    }

    public double getVolume() {
        return volume;
    }
}
